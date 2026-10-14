package com.chess.engine.engine;

import java.util.*;

public class Board {

    private final char[] board = new char[64];

    private boolean whiteToMove = true;

    private boolean whiteKingSideCastle = true;
    private boolean whiteQueenSideCastle = true;

    private boolean blackKingSideCastle = true;
    private boolean blackQueenSideCastle = true;

    private int enPassantSquare = -1;

    // Horde chess variant (White has no king and a horde of pawns); see HordeChess
    private boolean horde = false;

    // Atomic chess variant: captures explode (see AtomicChess)
    private boolean atomic = false;

    // Three-Check chess variant: 3 checks win the game
    private boolean threeCheck = false;

    // Anti-chess variant: forced captures, no castling, lose all pieces to win
    private boolean antiChess = false;
    private boolean kingOfTheHill = false;
    private boolean racingKings = false;

    // Chess960: position number (-1 = none) and the start file of each castling rook (WK, WQ, BK, BQ)
    private boolean chess960 = false;
    private int chess960Id = -1;
    private final int[] castleRookFile = {7, 0, 7, 0};

    // Three-Check: number of checks each side has delivered so far
    private int whiteChecks = 0;
    private int blackChecks = 0;

    // --- Optimization Structures: Zobrist Keys & Move Undo History ---
    private long zobristKey = 0L;
    private static final long[][][] ZOBRIST_PIECES = new long[64][12][1];
    private static final long[] ZOBRIST_CASTLING = new long[16];
    private static final long[] ZOBRIST_EN_PASSANT = new long[65]; // 0..63 + 1 for -1
    private static final long ZOBRIST_SIDE = 0xF0F0F0F00F0F0F0FL;

    static {
        Random rand = new Random(42);
        for (int sq = 0; sq < 64; sq++) {
            for (int p = 0; p < 12; p++) {
                ZOBRIST_PIECES[sq][p][0] = rand.nextLong();
            }
        }
        for (int i = 0; i < 16; i++) {
            ZOBRIST_CASTLING[i] = rand.nextLong();
        }
        for (int i = 0; i < 65; i++) {
            ZOBRIST_EN_PASSANT[i] = rand.nextLong();
        }
    }

    private static class UndoState {
        boolean whiteKingSideCastle;
        boolean whiteQueenSideCastle;
        boolean blackKingSideCastle;
        boolean blackQueenSideCastle;
        int enPassantSquare;
        char capturedPiece;
        long zobristKey;
        boolean isNullMove;
        java.util.List<int[]> exploded;   // atomic chess: pieces removed by the explosion
        int whiteChecks;
        int blackChecks;
    }

    private final Deque<UndoState> undoStack = new ArrayDeque<>();

    public Board() {
        setupStartingPosition();
    }

    public Board(Board other) {
        System.arraycopy(other.board, 0, this.board, 0, 64);

        this.whiteToMove = other.whiteToMove;
        this.whiteKingSideCastle = other.whiteKingSideCastle;
        this.whiteQueenSideCastle = other.whiteQueenSideCastle;
        this.blackKingSideCastle = other.blackKingSideCastle;
        this.blackQueenSideCastle = other.blackQueenSideCastle;
        this.enPassantSquare = other.enPassantSquare;
        this.zobristKey = other.zobristKey;
        this.horde = other.horde;
        this.atomic = other.atomic;
        this.threeCheck = other.threeCheck;
        this.antiChess = other.antiChess;
        this.kingOfTheHill = other.kingOfTheHill;
        this.racingKings = other.racingKings;
        this.chess960 = other.chess960;
        this.chess960Id = other.chess960Id;
        System.arraycopy(other.castleRookFile, 0, this.castleRookFile, 0, 4);
        this.whiteChecks = other.whiteChecks;
        this.blackChecks = other.blackChecks;
    }

    public void setupStartingPosition() {
        for (int i = 0; i < 64; i++) {
            board[i] = '.';
        }

        String firstRank = "RNBQKBNR";
        String secondRank = "PPPPPPPP";

        for (int i = 0; i < 8; i++) {
            board[i] = firstRank.charAt(i);
            board[8 + i] = secondRank.charAt(i);
            board[48 + i] = Character.toLowerCase(secondRank.charAt(i));
            board[56 + i] = Character.toLowerCase(firstRank.charAt(i));
        }

        whiteToMove = true;
        whiteKingSideCastle = true;
        whiteQueenSideCastle = true;
        blackKingSideCastle = true;
        blackQueenSideCastle = true;
        horde = false;
        atomic = false;
        threeCheck = false;
        antiChess = false;
        kingOfTheHill = false;
        racingKings = false;
        chess960 = false;
        chess960Id = -1;
        castleRookFile[0] = 7; castleRookFile[1] = 0; castleRookFile[2] = 7; castleRookFile[3] = 0;
        whiteChecks = 0;
        blackChecks = 0;
        enPassantSquare = -1;
        undoStack.clear();

        recalculateZobristKey();
    }

    // --- Zobrist Hash Logic ---
    public long getZobristKey() {
        return zobristKey;
    }

    public void recalculateZobristKey() {
        zobristKey = 0L;
        for (int sq = 0; sq < 64; sq++) {
            char p = board[sq];
            if (p != '.') {
                int pIdx = pieceToZobristIndex(p);
                zobristKey ^= ZOBRIST_PIECES[sq][pIdx][0];
            }
        }
        if (!whiteToMove) {
            zobristKey ^= ZOBRIST_SIDE;
        }
        int castlingBits = (whiteKingSideCastle ? 1 : 0) | (whiteQueenSideCastle ? 2 : 0)
                | (blackKingSideCastle ? 4 : 0) | (blackQueenSideCastle ? 8 : 0);
        zobristKey ^= ZOBRIST_CASTLING[castlingBits];
        zobristKey ^= ZOBRIST_EN_PASSANT[enPassantSquare == -1 ? 64 : enPassantSquare];
    }

    private int pieceToZobristIndex(char p) {
        switch (p) {
            case 'P': return 0;  case 'N': return 1;  case 'B': return 2;
            case 'R': return 3;  case 'Q': return 4;  case 'K': return 5;
            case 'p': return 6;  case 'n': return 7;  case 'b': return 8;
            case 'r': return 9;  case 'q': return 10; case 'k': return 11;
            default: return 0;
        }
    }

    // --- Search & Evaluation Support Methods ---
    public char getPieceTypeAt(int square) {
        if (square < 0 || square >= 64) return '.';
        return board[square];
    }

    public int getPieceValueAt(int square) {
        char p = board[square];
        switch (Character.toUpperCase(p)) {
            case 'P': return 100;
            case 'N': return 320;
            case 'B': return 330;
            case 'R': return 500;
            case 'Q': return 900;
            case 'K': return 20000;
            default: return 0;
        }
    }

    public int getMaterialScore(boolean isWhite) {
        int score = 0;
        for (int sq = 0; sq < 64; sq++) {
            char p = board[sq];
            if (p == '.') continue;
            boolean pieceIsWhite = Character.isUpperCase(p);
            if (pieceIsWhite == isWhite) {
                score += getPieceValueAt(sq);
            }
        }
        return score;
    }

    public int getNonPawnMaterialCount(boolean isWhite) {
        int count = 0;
        for (int sq = 0; sq < 64; sq++) {
            char p = board[sq];
            if (p == '.') continue;
            boolean pieceIsWhite = Character.isUpperCase(p);
            char type = Character.toUpperCase(p);
            if (pieceIsWhite == isWhite && type != 'P' && type != 'K') {
                count++;
            }
        }
        return count;
    }

    public int evaluatePosition() {
        int score = 0;
        for (int sq = 0; sq < 64; sq++) {
            char p = board[sq];
            if (p == '.') continue;

            int val = getPieceValueAt(sq);
            int pstBonus = getPieceSquareBonus(p, sq);

            if (Character.isUpperCase(p)) {
                score += (val + pstBonus);
            } else {
                score -= (val + pstBonus);
            }
        }
        return whiteToMove ? score : -score;
    }

    private int getPieceSquareBonus(char p, int sq) {
        int rank = sq / 8;
        int file = sq % 8;
        int centerBonus = (3 - Math.abs(3 - rank)) + (3 - Math.abs(3 - file));

        if (Character.toUpperCase(p) == 'P') {
            int advance = Character.isUpperCase(p) ? rank : (7 - rank);
            return advance * 10;
        }
        return centerBonus * 5;
    }

    // --- Null Move Pruning (NMP) Support ---
    public void makeNullMove() {
        UndoState state = new UndoState();
        state.whiteKingSideCastle = whiteKingSideCastle;
        state.whiteQueenSideCastle = whiteQueenSideCastle;
        state.blackKingSideCastle = blackKingSideCastle;
        state.blackQueenSideCastle = blackQueenSideCastle;
        state.enPassantSquare = enPassantSquare;
        state.capturedPiece = '.';
        state.zobristKey = zobristKey;
        state.isNullMove = true;
        undoStack.push(state);

        // Clear En Passant square if active
        if (enPassantSquare != -1) {
            zobristKey ^= ZOBRIST_EN_PASSANT[enPassantSquare];
            zobristKey ^= ZOBRIST_EN_PASSANT[64];
            enPassantSquare = -1;
        }

        // Toggle side to move
        whiteToMove = !whiteToMove;
        zobristKey ^= ZOBRIST_SIDE;
    }

    public void undoNullMove() {
        if (undoStack.isEmpty()) return;
        UndoState state = undoStack.pop();

        this.whiteToMove = !this.whiteToMove;
        this.whiteKingSideCastle = state.whiteKingSideCastle;
        this.whiteQueenSideCastle = state.whiteQueenSideCastle;
        this.blackKingSideCastle = state.blackKingSideCastle;
        this.blackQueenSideCastle = state.blackQueenSideCastle;
        this.enPassantSquare = state.enPassantSquare;
        this.zobristKey = state.zobristKey;
    }

    // --- Move Execution & Undo Stack ---
    public void makeMove(Move move) {
        UndoState state = new UndoState();
        state.whiteKingSideCastle = whiteKingSideCastle;
        state.whiteQueenSideCastle = whiteQueenSideCastle;
        state.blackKingSideCastle = blackKingSideCastle;
        state.blackQueenSideCastle = blackQueenSideCastle;
        state.enPassantSquare = enPassantSquare;
        state.capturedPiece = board[move.toSquare];
        state.zobristKey = zobristKey;
        state.isNullMove = false;
        state.whiteChecks = whiteChecks;
        state.blackChecks = blackChecks;
        undoStack.push(state);

        char movingPiece = board[move.fromSquare];

        // Perform board updates
        boolean castle960 = chess960 && move.isCastle;
        if (castle960) {
            // Chess960: the king moves onto its rook; both end on their normal castling squares
            char rook = board[move.toSquare];
            int base = (move.fromSquare / 8) * 8;
            state.capturedPiece = '.';
            board[move.fromSquare] = '.';
            board[move.toSquare] = '.';
            board[base + (move.castleKingSide ? 6 : 2)] = movingPiece;
            board[base + (move.castleKingSide ? 5 : 3)] = rook;
        } else {
            board[move.toSquare] = (move.promotionPiece != '.' && move.promotionPiece != '\0') ? move.promotionPiece : movingPiece;
            board[move.fromSquare] = '.';
        }

        // En Passant handling
        if (move.isEnPassant) {
            int capSq = whiteToMove ? move.toSquare - 8 : move.toSquare + 8;
            board[capSq] = '.';
        }

        // Update En Passant Target
        if (Character.toUpperCase(movingPiece) == 'P' && Math.abs(move.toSquare - move.fromSquare) == 16
                && HordeChess.doubleStepSetsEnPassant(this, move.fromSquare)) {
            enPassantSquare = (move.fromSquare + move.toSquare) / 2;
        } else {
            enPassantSquare = -1;
        }

        // Castling move updates
        if (move.isCastle && !chess960) {
            if (move.toSquare == 6) { board[7] = '.'; board[5] = 'R'; }
            else if (move.toSquare == 2) { board[0] = '.'; board[3] = 'R'; }
            else if (move.toSquare == 62) { board[63] = '.'; board[61] = 'r'; }
            else if (move.toSquare == 58) { board[56] = '.'; board[59] = 'r'; }
        }

        // Update Castling Rights
        if (movingPiece == 'K') { whiteKingSideCastle = false; whiteQueenSideCastle = false; }
        if (movingPiece == 'k') { blackKingSideCastle = false; blackQueenSideCastle = false; }
        int wkr = castleRookFile[0], wqr = castleRookFile[1];
        int bkr = 56 + castleRookFile[2], bqr = 56 + castleRookFile[3];
        if (move.fromSquare == wqr || move.toSquare == wqr) whiteQueenSideCastle = false;
        if (move.fromSquare == wkr || move.toSquare == wkr) whiteKingSideCastle = false;
        if (move.fromSquare == bqr || move.toSquare == bqr) blackQueenSideCastle = false;
        if (move.fromSquare == bkr || move.toSquare == bkr) blackKingSideCastle = false;

        // Atomic chess: a capture blows up the capturer and everything around it (except pawns)
        if (atomic && (state.capturedPiece != '.' || move.isEnPassant)) {
            state.exploded = AtomicChess.explode(this, move.toSquare);
        }

        whiteToMove = !whiteToMove;

        // Three-Check: the side that just moved gets a point if it gave check
        if (threeCheck && isInCheck(whiteToMove)) {
            if (whiteToMove) {
                blackChecks++;
            } else {
                whiteChecks++;
            }
        }

        recalculateZobristKey();
    }

    public void undoMove(Move move) {
        if (undoStack.isEmpty()) return;
        UndoState state = undoStack.pop();

        if (state.isNullMove) {
            this.whiteToMove = !this.whiteToMove;
            this.whiteKingSideCastle = state.whiteKingSideCastle;
            this.whiteQueenSideCastle = state.whiteQueenSideCastle;
            this.blackKingSideCastle = state.blackKingSideCastle;
            this.blackQueenSideCastle = state.blackQueenSideCastle;
            this.enPassantSquare = state.enPassantSquare;
            this.zobristKey = state.zobristKey;
            return;
        }

        if (state.exploded != null) {
            AtomicChess.restore(this, state.exploded);
        }

        whiteToMove = !whiteToMove;
        char movedPiece = board[move.toSquare];

        if (move.promotionPiece != '.' && move.promotionPiece != '\0') {
            movedPiece = whiteToMove ? 'P' : 'p';
        }

        boolean castle960 = chess960 && move.isCastle;
        if (castle960) {
            int base = (move.fromSquare / 8) * 8;
            int kingDest = base + (move.castleKingSide ? 6 : 2);
            int rookDest = base + (move.castleKingSide ? 5 : 3);
            char king = board[kingDest];
            char rook = board[rookDest];
            board[kingDest] = '.';
            board[rookDest] = '.';
            board[move.fromSquare] = king;
            board[move.toSquare] = rook;
        } else {
            board[move.fromSquare] = movedPiece;
            board[move.toSquare] = state.capturedPiece;
        }

        if (move.isEnPassant) {
            int capSq = whiteToMove ? move.toSquare - 8 : move.toSquare + 8;
            board[capSq] = whiteToMove ? 'p' : 'P';
            board[move.toSquare] = '.';
        }

        if (move.isCastle && !chess960) {
            if (move.toSquare == 6) { board[5] = '.'; board[7] = 'R'; }
            else if (move.toSquare == 2) { board[3] = '.'; board[0] = 'R'; }
            else if (move.toSquare == 62) { board[61] = '.'; board[63] = 'r'; }
            else if (move.toSquare == 58) { board[59] = '.'; board[56] = 'r'; }
        }

        this.whiteKingSideCastle = state.whiteKingSideCastle;
        this.whiteQueenSideCastle = state.whiteQueenSideCastle;
        this.blackKingSideCastle = state.blackKingSideCastle;
        this.blackQueenSideCastle = state.blackQueenSideCastle;
        this.enPassantSquare = state.enPassantSquare;
        this.zobristKey = state.zobristKey;
        this.whiteChecks = state.whiteChecks;
        this.blackChecks = state.blackChecks;
    }

    // --- Move Generation APIs ---
    // After makeMove: is the king of the side that just moved safe? (Atomic and Anti-Chess have their own rules.)
    private boolean moverIsSafe() {
        if (antiChess) {
            return true; // Anti-Chess ignores king safety and allows king captures
        }
        if (atomic) {
            return AtomicChess.isMoverSafe(this, !whiteToMove);
        }
        if (racingKings) {
            return !isInCheck(true) && !isInCheck(false); // neither king may ever be in check
        }
        return !isInCheck(!whiteToMove);
    }

    public List<Move> generateLegalMoves() {
        if (racingKings && RacingKings.isDecided(this)) {
            return new ArrayList<>(); // a king has reached the 8th rank: the game is over
        }
        if (kingOfTheHill && KingOfTheHill.isGameWon(this)) {
            return new ArrayList<>(); // a king is on the hill: the game is over
        }
        List<Move> pseudoMoves = generatePseudoMoves(false, false);
        List<Move> legalMoves = new ArrayList<>();

        for (Move m : pseudoMoves) {
            makeMove(m);
            if (moverIsSafe()) {
                legalMoves.add(m);
            }
            undoMove(m);
        }

        // Filter mandatory captures for Anti-Chess
        if (antiChess) {
            return AntiChess.filterMandatoryCaptures(this, legalMoves);
        }

        return legalMoves;
    }

    public List<Move> generateCaptures() {
        if (racingKings && RacingKings.isDecided(this)) {
            return new ArrayList<>(); // a king has reached the 8th rank: the game is over
        }
        if (kingOfTheHill && KingOfTheHill.isGameWon(this)) {
            return new ArrayList<>(); // a king is on the hill: the game is over
        }
        List<Move> pseudoCaptures = generatePseudoMoves(true, false);
        List<Move> legalCaptures = new ArrayList<>();

        for (Move m : pseudoCaptures) {
            makeMove(m);
            if (moverIsSafe()) {
                legalCaptures.add(m);
            }
            undoMove(m);
        }
        return legalCaptures;
    }

    public List<Move> generateQuietChecks() {
        if (racingKings && RacingKings.isDecided(this)) {
            return new ArrayList<>(); // a king has reached the 8th rank: the game is over
        }
        if (kingOfTheHill && KingOfTheHill.isGameWon(this)) {
            return new ArrayList<>(); // a king is on the hill: the game is over
        }
        List<Move> pseudoQuiets = generatePseudoMoves(false, true);
        List<Move> quietChecks = new ArrayList<>();

        for (Move m : pseudoQuiets) {
            makeMove(m);
            if (moverIsSafe() && isInCheck(whiteToMove)) {
                quietChecks.add(m);
            }
            undoMove(m);
        }
        return quietChecks;
    }

    private List<Move> generatePseudoMoves(boolean capturesOnly, boolean quietsOnly) {
        List<Move> moves = new ArrayList<>();
        for (int sq = 0; sq < 64; sq++) {
            char p = board[sq];
            if (p == '.' || (whiteToMove && !Character.isUpperCase(p)) || (!whiteToMove && Character.isUpperCase(p))) {
                continue;
            }
            generatePieceMoves(sq, p, moves, capturesOnly, quietsOnly);
        }
        return moves;
    }

    private void generatePieceMoves(int sq, char p, List<Move> moves, boolean capturesOnly, boolean quietsOnly) {
        char type = Character.toUpperCase(p);
        int rank = sq / 8;
        int file = sq % 8;

        if (type == 'P') {
            int dir = whiteToMove ? 8 : -8;
            int target = sq + dir;

            // Step Forward
            if (!capturesOnly && target >= 0 && target < 64 && board[target] == '.') {
                addPawnMove(sq, target, false, moves);
                int doubleStep = sq + 2 * dir;
                int startRank = whiteToMove ? 1 : 6;
                if ((rank == startRank || HordeChess.canDoubleStepFromFirstRank(this, whiteToMove, rank))
                        && board[doubleStep] == '.') {
                    addPawnMove(sq, doubleStep, false, moves);
                }
            }

            // Captures
            int[] capOffsets = whiteToMove ? new int[]{7, 9} : new int[]{-7, -9};
            for (int off : capOffsets) {
                int cSq = sq + off;
                if (cSq >= 0 && cSq < 64 && Math.abs((cSq % 8) - file) == 1) {
                    if (!quietsOnly && board[cSq] != '.' && (whiteToMove != Character.isUpperCase(board[cSq]))) {
                        addPawnMove(sq, cSq, true, moves);
                    } else if (cSq == enPassantSquare) {
                        // full constructor so that the 'enPassant' flag is set as well
                        moves.add(new Move(sq, cSq, '\0', false, false, true, true));
                    }
                }
            }
        } else {
            int[][] vectors = getPieceVectors(type);
            boolean sliding = (type == 'B' || type == 'R' || type == 'Q');

            for (int[] vec : vectors) {
                int curR = rank + vec[0];
                int curF = file + vec[1];

                while (curR >= 0 && curR < 8 && curF >= 0 && curF < 8) {
                    int targetSq = curR * 8 + curF;
                    char targetPiece = board[targetSq];

                    if (targetPiece == '.') {
                        if (!capturesOnly) {
                            moves.add(new Move(sq, targetSq, false));
                        }
                    } else {
                        if (whiteToMove != Character.isUpperCase(targetPiece)) {
                            if (!quietsOnly) {
                                moves.add(new Move(sq, targetSq, true));
                            }
                        }
                        break; // Blocked
                    }

                    if (!sliding) break;
                    curR += vec[0];
                    curF += vec[1];
                }
            }

            if (type == 'K' && !capturesOnly && !antiChess) {
                addCastlingMoves(sq, moves);
            }
        }
    }

    /**
     * Castling: right still available, rook on its square, squares between empty,
     * and the king neither in check nor passing through / landing on an attacked square.
     */
    private void addCastlingMoves(int sq, List<Move> moves) {
        if (antiChess) return; // Castling disabled in Anti-Chess

        if (chess960) {
            Chess960.addCastlingMoves(this, sq, moves);
            return;
        }

        if (whiteToMove && sq == 4) {
            if (whiteKingSideCastle && board[7] == 'R' && board[5] == '.' && board[6] == '.'
                    && !isSquareAttackedBy(4, false) && !isSquareAttackedBy(5, false)
                    && !isSquareAttackedBy(6, false)) {
                moves.add(new Move(4, 6, '\0', true, false, false, false));
            }
            if (whiteQueenSideCastle && board[0] == 'R' && board[1] == '.' && board[2] == '.' && board[3] == '.'
                    && !isSquareAttackedBy(4, false) && !isSquareAttackedBy(3, false)
                    && !isSquareAttackedBy(2, false)) {
                moves.add(new Move(4, 2, '\0', false, true, false, false));
            }
        } else if (!whiteToMove && sq == 60) {
            if (blackKingSideCastle && board[63] == 'r' && board[61] == '.' && board[62] == '.'
                    && !isSquareAttackedBy(60, true) && !isSquareAttackedBy(61, true)
                    && !isSquareAttackedBy(62, true)) {
                moves.add(new Move(60, 62, '\0', true, false, false, false));
            }
            if (blackQueenSideCastle && board[56] == 'r' && board[57] == '.' && board[58] == '.' && board[59] == '.'
                    && !isSquareAttackedBy(60, true) && !isSquareAttackedBy(59, true)
                    && !isSquareAttackedBy(58, true)) {
                moves.add(new Move(60, 58, '\0', false, true, false, false));
            }
        }
    }

    private void addPawnMove(int from, int to, boolean isCapture, List<Move> moves) {
        int targetRank = to / 8;
        if (targetRank == 0 || targetRank == 7) {
            char[] promos = whiteToMove ? new char[]{'Q', 'R', 'B', 'N'} : new char[]{'q', 'r', 'b', 'n'};
            for (char promo : promos) {
                // full constructor so that 'promotion' (not only the alias) is set
                moves.add(new Move(from, to, promo, false, false, false, isCapture));
            }
        } else {
            moves.add(new Move(from, to, isCapture));
        }
    }

    private int[][] getPieceVectors(char type) {
        switch (type) {
            case 'N': return new int[][]{{2,1},{1,2},{-1,2},{-2,1},{-2,-1},{-1,-2},{1,-2},{2,-1}};
            case 'B': return new int[][]{{1,1},{1,-1},{-1,1},{-1,-1}};
            case 'R': return new int[][]{{1,0},{-1,0},{0,1},{0,-1}};
            case 'Q': case 'K': return new int[][]{{1,1},{1,-1},{-1,1},{-1,-1},{1,0},{-1,0},{0,1},{0,-1}};
            default: return new int[0][0];
        }
    }

    /**
     * Public static helper to check if a specific square is under attack by a given color.
     */
    public static boolean isSquareAttacked(Board board, int targetSq, boolean byWhite) {
        return board.isSquareAttackedBy(targetSq, byWhite);
    }

    public boolean isSquareAttackedBy(int targetSq, boolean byWhite) {
        int targetRank = targetSq / 8;
        int targetFile = targetSq % 8;

        // 1. Pawn Attacks
        int[] pawnOffsets = byWhite ? new int[]{-7, -9} : new int[]{7, 9};
        for (int off : pawnOffsets) {
            int attackerSq = targetSq + off;
            if (attackerSq >= 0 && attackerSq < 64) {
                int attackerFile = attackerSq % 8;
                if (Math.abs(attackerFile - targetFile) == 1) {
                    char p = board[attackerSq];
                    if (p == (byWhite ? 'P' : 'p')) return true;
                }
            }
        }

        // 2. Knight Attacks
        int[][] knightVectors = getPieceVectors('N');
        for (int[] vec : knightVectors) {
            int r = targetRank + vec[0];
            int f = targetFile + vec[1];
            if (r >= 0 && r < 8 && f >= 0 && f < 8) {
                char p = board[r * 8 + f];
                if (p == (byWhite ? 'N' : 'n')) return true;
            }
        }

        // 3. Sliding Attacks (Rook, Bishop, Queen) & King Attacks
        int[][] directions = getPieceVectors('Q');
        for (int[] vec : directions) {
            int r = targetRank + vec[0];
            int f = targetFile + vec[1];
            boolean isDiagonal = (vec[0] != 0 && vec[1] != 0);
            int step = 1;

            while (r >= 0 && r < 8 && f >= 0 && f < 8) {
                char p = board[r * 8 + f];
                if (p != '.') {
                    boolean isAttackerColor = byWhite ? Character.isUpperCase(p) : Character.isLowerCase(p);
                    if (isAttackerColor) {
                        char type = Character.toUpperCase(p);
                        if (type == 'Q') return true;
                        if (isDiagonal && type == 'B') return true;
                        if (!isDiagonal && type == 'R') return true;
                        if (step == 1 && type == 'K' && !atomic) return true;
                    }
                    break; // Blocked by another piece
                }
                r += vec[0];
                f += vec[1];
                step++;
            }
        }

        return false;
    }

    public boolean isInCheck(boolean sideWhite) {
        if (antiChess) return false; // Anti-Chess has no checks

        if (atomic) {
            return AtomicChess.isKingInCheck(this, sideWhite);
        }

        int kingSq = -1;
        char kingChar = sideWhite ? 'K' : 'k';
        for (int i = 0; i < 64; i++) {
            if (board[i] == kingChar) {
                kingSq = i;
                break;
            }
        }
        if (kingSq == -1) return false;

        return isSquareAttackedBy(kingSq, !sideWhite);
    }

    // --- Standard Getters / Setters & FEN Methods ---
    public char getPiece(int square) { return board[square]; }
    public void setPiece(int square, char piece) { board[square] = piece; recalculateZobristKey(); }

    public boolean isWhiteToMove() { return whiteToMove; }
    public void setWhiteToMove(boolean value) { whiteToMove = value; recalculateZobristKey(); }

    public int getEnPassantSquare() { return enPassantSquare; }
    public void setEnPassantSquare(int square) { enPassantSquare = square; recalculateZobristKey(); }

    public boolean canWhiteCastleKingSide() { return whiteKingSideCastle; }
    public boolean canWhiteCastleQueenSide() { return whiteQueenSideCastle; }
    public boolean canBlackCastleKingSide() { return blackKingSideCastle; }
    public boolean canBlackCastleQueenSide() { return blackQueenSideCastle; }

    public void setWhiteKingSideCastle(boolean value) { whiteKingSideCastle = value; recalculateZobristKey(); }
    public void setWhiteQueenSideCastle(boolean value) { whiteQueenSideCastle = value; recalculateZobristKey(); }
    public void setBlackKingSideCastle(boolean value) { blackKingSideCastle = value; recalculateZobristKey(); }
    public void setBlackQueenSideCastle(boolean value) { blackQueenSideCastle = value; recalculateZobristKey(); }

    public void setWhiteCastleKingSide(boolean value) { setWhiteKingSideCastle(value); }
    public void setWhiteCastleQueenSide(boolean value) { setWhiteQueenSideCastle(value); }
    public void setBlackCastleKingSide(boolean value) { setBlackKingSideCastle(value); }
    public void setBlackCastleQueenSide(boolean value) { setBlackQueenSideCastle(value); }

    public boolean isAtomic() { return atomic; }
    public void setAtomic(boolean value) { atomic = value; }

    public boolean isHorde() { return horde; }
    public void setHorde(boolean value) { horde = value; }

    public boolean isThreeCheck() { return threeCheck; }
    public void setThreeCheck(boolean value) { threeCheck = value; }

    public int getWhiteChecks() { return whiteChecks; }
    public int getBlackChecks() { return blackChecks; }
    public void setWhiteChecks(int value) { whiteChecks = value; }
    public void setBlackChecks(int value) { blackChecks = value; }

    public boolean isChess960() { return chess960; }
    public void setChess960(boolean value) { this.chess960 = value; }
    public int getChess960Id() { return chess960Id; }
    public void setChess960Id(int id) { this.chess960Id = id; }

    // Start file (0 = a ... 7 = h) of the rook that castles on the given side.
    public int getCastleRookFile(boolean white, boolean kingSide) {
        return castleRookFile[(white ? 0 : 2) + (kingSide ? 0 : 1)];
    }
    public void setCastleRookFile(boolean white, boolean kingSide, int file) {
        castleRookFile[(white ? 0 : 2) + (kingSide ? 0 : 1)] = file;
    }

    public boolean isRacingKings() { return racingKings; }
    public void setRacingKings(boolean value) {
        this.racingKings = value;
        if (value) {
            // Racing Kings has no castling
            this.whiteKingSideCastle = false;
            this.whiteQueenSideCastle = false;
            this.blackKingSideCastle = false;
            this.blackQueenSideCastle = false;
        }
    }

    public boolean isKingOfTheHill() { return kingOfTheHill; }
    public void setKingOfTheHill(boolean value) { this.kingOfTheHill = value; }

    public boolean isAntiChess() { return antiChess; }
    public void setAntiChess(boolean value) {
        this.antiChess = value;
        if (value) {
            // Anti-chess explicitly disables castling rights for both colors
            this.whiteKingSideCastle = false;
            this.whiteQueenSideCastle = false;
            this.blackKingSideCastle = false;
            this.blackQueenSideCastle = false;
            recalculateZobristKey();
        }
    }

    public Board copy() { return new Board(this); }

    public String toFEN() {
        StringBuilder fen = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                char piece = board[rank * 8 + file];
                if (piece == '.') {
                    empty++;
                } else {
                    if (empty > 0) { fen.append(empty); empty = 0; }
                    fen.append(piece);
                }
            }
            if (empty > 0) fen.append(empty);
            if (rank != 0) fen.append('/');
        }

        fen.append(whiteToMove ? " w " : " b ");
        StringBuilder castling = new StringBuilder();
        if (whiteKingSideCastle) castling.append("K");
        if (whiteQueenSideCastle) castling.append("Q");
        if (blackKingSideCastle) castling.append("k");
        if (blackQueenSideCastle) castling.append("q");
        if (castling.length() == 0) castling.append("-");

        fen.append(castling).append(" ");
        if (enPassantSquare == -1) {
            fen.append("-");
        } else {
            fen.append((char) ('a' + (enPassantSquare % 8)))
               .append((char) ('1' + (enPassantSquare / 8)));
        }
        fen.append(" 0 1");
        return fen.toString();
    }

    public static Board fromFEN(String fen) {
        Board result = new Board();
        String[] parts = fen.trim().split("\\s+");
        String[] ranks = parts[0].split("/");

        for (int rank = 0; rank < 8; rank++) {
            int file = 0;
            for (char c : ranks[rank].toCharArray()) {
                if (Character.isDigit(c)) {
                    int empty = c - '0';
                    for (int i = 0; i < empty; i++) {
                        result.board[(7 - rank) * 8 + file] = '.';
                        file++;
                    }
                } else {
                    result.board[(7 - rank) * 8 + file] = c;
                    file++;
                }
            }
        }

        result.whiteToMove = parts.length < 2 || parts[1].equals("w");

        if (parts.length >= 3) {
            String castling = parts[2];
            result.whiteKingSideCastle = castling.contains("K");
            result.whiteQueenSideCastle = castling.contains("Q");
            result.blackKingSideCastle = castling.contains("k");
            result.blackQueenSideCastle = castling.contains("q");
        } else {
            result.whiteKingSideCastle = false;
            result.whiteQueenSideCastle = false;
            result.blackKingSideCastle = false;
            result.blackQueenSideCastle = false;
        }

        if (parts.length >= 4 && !parts[3].equals("-")) {
            String square = parts[3];
            int file = square.charAt(0) - 'a';
            int rank = square.charAt(1) - '1';
            result.enPassantSquare = rank * 8 + file;
        } else {
            result.enPassantSquare = -1;
        }

        result.recalculateZobristKey();
        return result;
    }
}