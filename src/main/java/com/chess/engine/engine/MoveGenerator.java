package com.chess.engine.engine;

import java.util.List;
import java.util.ArrayList;

public class MoveGenerator {

    private static final int[] KN_DF = {1, 2, 2, 1, -1, -2, -2, -1};
    private static final int[] KN_DR = {2, 1, -1, -2, -2, -1, 1, 2};
    private static final int[] ORTH_DF = {1, -1, 0, 0};
    private static final int[] ORTH_DR = {0, 0, 1, -1};
    private static final int[] DIAG_DF = {1, 1, -1, -1};
    private static final int[] DIAG_DR = {1, -1, 1, -1};

    public static List<Move> generateLegalMoves(Board board) {

        if (board.isKingOfTheHill() && KingOfTheHill.isGameWon(board)) {
            return new ArrayList<>(); // a king is on the hill: the game is over
        }

        if (board.isRacingKings() && RacingKings.isDecided(board)) {
            return new ArrayList<>(); // a king has reached the 8th rank: the game is over
        }

        if (board.isAtomic()) {
            return AtomicChess.generateLegalMoves(board);
        }

        if (board.isAntiChess()) {
            return AntiChess.generateLegalMoves(board);
        }

        List<Move> pseudo = generatePseudoLegalMoves(board);
        boolean whiteMoved = board.isWhiteToMove();
        boolean racing = board.isRacingKings();

        char kingChar = whiteMoved ? 'K' : 'k';
        int kingSq = -1;
        for (int i = 0; i < 64; i++) {
            if (board.getPiece(i) == kingChar) {
                kingSq = i;
                break;
            }
        }

        // Horde chess: White has no king, so it can never be in check and every pseudo-legal move is legal
        if (kingSq < 0 && board.isHorde() && whiteMoved && !racing) {
            return pseudo;
        }

        // When the king is not in check, a move of a piece that does not stand on a line through
        // the king (and that is not the king / en passant) can never expose it: no copy needed.
        boolean quick = !racing && kingSq >= 0 && !isSquareAttacked(board, kingSq, !whiteMoved);

        List<Move> legal = new ArrayList<>(pseudo.size());

        for (Move move : pseudo) {

            if (quick
                    && !move.enPassant
                    && Character.toLowerCase(board.getPiece(move.from)) != 'k'
                    && !aligned(kingSq, move.from)) {
                legal.add(move);
                continue;
            }

            Board copy = board.copy();

            makeMove(copy, move);

            // Racing Kings: neither king may be in check after any move
            if (!isKingInCheck(copy, whiteMoved)
                    && !(racing && isKingInCheck(copy, !whiteMoved))) {
                legal.add(move);
            }
        }

        return legal;
    }

    /** True when the two squares share a rank, a file or a diagonal. */
    private static boolean aligned(int a, int b) {
        int df = (a & 7) - (b & 7);
        int dr = (a >> 3) - (b >> 3);
        return df == 0 || dr == 0 || Math.abs(df) == Math.abs(dr);
    }

    public static List<Move> generatePseudoLegalMoves(Board board) {

        List<Move> moves = new ArrayList<>(48);

        for (int square = 0; square < 64; square++) {

            char piece = board.getPiece(square);

            if (piece == '.') {
                continue;
            }

            boolean whitePiece = Character.isUpperCase(piece);

            if (whitePiece != board.isWhiteToMove()) {
                continue;
            }

            switch (Character.toLowerCase(piece)) {

                case 'p' -> generatePawnMoves(board, square, moves);
                case 'n' -> generateKnightMoves(board, square, moves);
                case 'b' -> generateSlidingMoves(
                        board,
                        square,
                        moves,
                        new int[]{9, 7, -9, -7}
                );
                case 'r' -> generateSlidingMoves(
                        board,
                        square,
                        moves,
                        new int[]{8, -8, 1, -1}
                );

                case 'q' -> generateSlidingMoves(
                        board,
                        square,
                        moves,
                        new int[]{
                                8, -8, 1, -1, 9, 7, -9, -7
                        }
                );
                case 'k' -> generateKingMoves(board, square, moves);
            }
        }

        return moves;
    }

    private static void generatePawnMoves(
            Board board,
            int square,
            List<Move> moves) {

        char pawn = board.getPiece(square);

        boolean white = Character.isUpperCase(pawn);

        int direction = white ? 8 : -8;

        int rank = square / 8;
        int file = square % 8;

        int oneForward = square + direction;

        if (oneForward >= 0 &&
                oneForward < 64 &&
                board.getPiece(oneForward) == '.') {

            addPawnMove(board, square, oneForward, moves);

            int startingRank = white ? 1 : 6;

            int twoForward = square + 2 * direction;

            if ((rank == startingRank
                    || HordeChess.canDoubleStepFromFirstRank(board, white, rank)) &&
                    board.getPiece(twoForward) == '.') {

                moves.add(new Move(square, twoForward));
            }
        }

        int[] captures = {file - 1, file + 1};

        for (int targetFile : captures) {

            if (targetFile < 0 || targetFile > 7) {
                continue;
            }

            int target = square + direction
                    + (targetFile - file);

            if (target < 0 || target >= 64) {
                continue;
            }

            char targetPiece = board.getPiece(target);

            if (targetPiece != '.' &&
                    Character.isUpperCase(targetPiece) != white) {

                addPawnMove(board, square, target, moves);
            }

            if (target == board.getEnPassantSquare()) {

                moves.add(
                        new Move(square, target, '\0', false, false, true));
            }
        }
    }

    private static void addPawnMove(Board board, int from, int to, List<Move> moves) {

        boolean white =
                Character.isUpperCase(board.getPiece(from));

        int rank = to / 8;

        if (rank == 7 || rank == 0) {

            char[] promotions =
                    white
                            ? new char[]{'Q', 'R', 'B', 'N'}
                            : new char[]{'q', 'r', 'b', 'n'};
            for (char promotion : promotions) {
                moves.add(new Move(from, to, promotion, false, false, false));
            }

        } else {
            moves.add(new Move(from, to));
        }
    }

    private static void generateKnightMoves(Board board, int square, List<Move> moves) {

        int file = square % 8;

        int[] offsets = {17, 15, 10, 6, -6, -10, -15, -17};

        for (int offset : offsets) {

            int target = square + offset;

            if (target < 0 || target >= 64) {
                continue;
            }

            int targetFile = target % 8;

            if (Math.abs(targetFile - file) > 2) {
                continue;
            }

            char targetPiece = board.getPiece(target);

            if (targetPiece == '.' ||
                    Character.isUpperCase(targetPiece) != Character.isUpperCase(board.getPiece(square))) {

                moves.add(new Move(square, target));
            }
        }
    }

    private static void generateSlidingMoves(Board board, int square, List<Move> moves, int[] directions) {

        boolean white =
                Character.isUpperCase(board.getPiece(square));

        for (int direction : directions) {
            int current = square;

            while (true) {

                int next = current + direction;

                if (next < 0 || next >= 64) {
                    break;
                }

                int currentFile = current % 8;
                int nextFile = next % 8;

                if (Math.abs(nextFile - currentFile) > 1) {
                    break;
                }

                char target = board.getPiece(next);

                if (target == '.') {

                    moves.add(new Move(square, next));
                    current = next;

                } else {

                    if (Character.isUpperCase(target) != white) {
                        moves.add(new Move(square, next));
                    }

                    break;
                }
            }
        }
    }

    private static void generateKingMoves(Board board, int square, List<Move> moves) {

        int[] directions = {8, -8, 1, -1, 9, 7, -9, -7};
        boolean white = Character.isUpperCase(board.getPiece(square));

        // Enemy color attacker flag:
        // If king is white, we check if target is attacked by BLACK (byWhite = false)
        // If king is black, we check if target is attacked by WHITE (byWhite = true)
        boolean attackedByEnemy = !white;

        for (int direction : directions) {
            int target = square + direction;

            if (target < 0 || target >= 64) {
                continue;
            }

            if (Math.abs((target % 8) - (square % 8)) > 1) {
                continue;
            }

            char piece = board.getPiece(target);

            // 1. Target square must be empty or contain an enemy piece
            if (piece == '.' || Character.isUpperCase(piece) != white) {

                // 2. Ensure king does not move onto a square attacked by opponent
                // atomic chess: legality (including touching kings) is decided by AtomicChess
                // anti-chess: no king safety at all
                if (board.isAtomic() || board.isAntiChess() || !isSquareAttacked(board, target, attackedByEnemy)) {
                    moves.add(new Move(square, target));
                }
            }
        }

        // --- CASTLING --- (not allowed in anti-chess)
        if (board.isAntiChess()) {
            return;
        }

        if (board.isChess960()) {
            Chess960.addCastlingMoves(board, square, moves);
            return;
        }

        if (white && square == 4) {

            if (board.canWhiteCastleKingSide() &&
                    board.getPiece(5) == '.' &&
                    board.getPiece(6) == '.' &&
                    !isSquareAttacked(board, 4, false) &&
                    !isSquareAttacked(board, 5, false) &&
                    !isSquareAttacked(board, 6, false)) {

                moves.add(new Move(4, 6, '\0', true, false, false));
            }

            if (board.canWhiteCastleQueenSide() &&
                    board.getPiece(3) == '.' &&
                    board.getPiece(2) == '.' &&
                    board.getPiece(1) == '.' &&
                    !isSquareAttacked(board, 4, false) &&
                    !isSquareAttacked(board, 3, false) &&
                    !isSquareAttacked(board, 2, false)) {

                moves.add(new Move(4, 2, '\0', false, true, false));
            }

        } else if (!white && square == 60) {

            if (board.canBlackCastleKingSide() &&
                    board.getPiece(61) == '.' &&
                    board.getPiece(62) == '.' &&
                    !isSquareAttacked(board, 60, true) &&
                    !isSquareAttacked(board, 61, true) &&
                    !isSquareAttacked(board, 62, true)) {

                moves.add(new Move(60, 62, '\0', true, false, false));
            }

            if (board.canBlackCastleQueenSide() &&
                    board.getPiece(59) == '.' &&
                    board.getPiece(58) == '.' &&
                    board.getPiece(57) == '.' &&
                    !isSquareAttacked(board, 60, true) &&
                    !isSquareAttacked(board, 59, true) &&
                    !isSquareAttacked(board, 58, true)) {

                moves.add(new Move(60, 58, '\0', false, true, false));
            }
        }
    }

    public static void makeMove(Board board, Move move) {

        // Atomic chess: a capture explodes (remember it before the board changes)
        boolean atomicCapture = board.isAtomic()
                && (board.getPiece(move.to) != '.' || move.enPassant);

        char piece = board.getPiece(move.from);

        board.setPiece(move.from, '.');

        // Chess960 castling: the king moves onto its rook, both end on their normal castling squares
        if (board.isChess960() && (move.castleKingSide || move.castleQueenSide)) {
            boolean whiteKing = Character.isUpperCase(piece);
            int base = (move.from / 8) * 8;
            char rook = board.getPiece(move.to);

            board.setPiece(move.to, '.');
            board.setPiece(base + (move.castleKingSide ? 6 : 2), piece);
            board.setPiece(base + (move.castleKingSide ? 5 : 3), rook);

            if (whiteKing) {
                board.setWhiteKingSideCastle(false);
                board.setWhiteQueenSideCastle(false);
            } else {
                board.setBlackKingSideCastle(false);
                board.setBlackQueenSideCastle(false);
            }
            board.setEnPassantSquare(-1);
            board.setWhiteToMove(!board.isWhiteToMove());
            return;
        }

        //en-passant captures

        if (move.enPassant) {

            int capturedPawn =
                    move.to + (Character.isUpperCase(piece) ? -8 : 8);

            board.setPiece(capturedPawn, '.');
        }

        //castling

        if (move.castleKingSide) {

            if (Character.isUpperCase(piece)) {

                board.setPiece(7, '.');
                board.setPiece(5, 'R');

            } else {

                board.setPiece(63, '.');
                board.setPiece(61, 'r');
            }
        }

        if (move.castleQueenSide) {

            if (Character.isUpperCase(piece)) {

                board.setPiece(0, '.');
                board.setPiece(3, 'R');

            } else {

                board.setPiece(56, '.');
                board.setPiece(59, 'r');
            }
        }

        if (move.promotion != '\0') {
            piece = move.promotion;
        }

        board.setPiece(move.to, piece);

        //castling rights

        if (piece == 'K') {
            board.setWhiteKingSideCastle(false);
            board.setWhiteQueenSideCastle(false);
        }

        if (piece == 'k') {
            board.setBlackKingSideCastle(false);
            board.setBlackQueenSideCastle(false);
        }

        int wqr = board.getCastleRookFile(true, false), wkr = board.getCastleRookFile(true, true);
        int bqr = 56 + board.getCastleRookFile(false, false), bkr = 56 + board.getCastleRookFile(false, true);

        if (move.from == wqr || move.to == wqr) {
            board.setWhiteQueenSideCastle(false);
        }

        if (move.from == wkr || move.to == wkr) {
            board.setWhiteKingSideCastle(false);
        }

        if (move.from == bqr || move.to == bqr) {
            board.setBlackQueenSideCastle(false);
        }

        if (move.from == bkr || move.to == bkr) {
            board.setBlackKingSideCastle(false);
        }

        //en-passant target

        board.setEnPassantSquare(-1);

        if (Character.toLowerCase(piece) == 'p' &&
                Math.abs(move.to - move.from) == 16 &&
                HordeChess.doubleStepSetsEnPassant(board, move.from)) {

            board.setEnPassantSquare(
                    (move.from + move.to) / 2
            );
        }

        if (atomicCapture) {
            AtomicChess.explode(board, move.to);
        }

        board.setWhiteToMove(!board.isWhiteToMove());

        // Three-Check: the side that just moved scores a check if it gave one
        if (board.isThreeCheck() && isKingInCheck(board, board.isWhiteToMove())) {
            if (board.isWhiteToMove()) {
                board.setBlackChecks(board.getBlackChecks() + 1);
            } else {
                board.setWhiteChecks(board.getWhiteChecks() + 1);
            }
        }
    }

    public static boolean isKingInCheck(
            Board board,
            boolean whiteKing) {

        if (board.isAtomic()) {
            return AtomicChess.isKingInCheck(board, whiteKing);
        }

        // Anti-chess has no check
        if (board.isAntiChess()) {
            return false;
        }

        char king = whiteKing ? 'K' : 'k';

        for (int i = 0; i < 64; i++) {

            if (board.getPiece(i) == king) {
                return isSquareAttacked(board, i, !whiteKing);
            }
        }

        // Horde chess: White has no king, so it can never be in check
        return !(board.isHorde() && whiteKing);
    }

    /**
     * Ray-based attack test: looks outwards from the target square instead of generating the
     * attacks of every enemy piece (several times faster).
     */
    public static boolean isSquareAttacked(Board board, int square, boolean byWhite) {

        if (board.isAtomic()) {
            return AtomicChess.isSquareAttacked(board, square, byWhite);
        }

        int file = square & 7;
        int rank = square >> 3;

        // pawns: a white pawn attacks upwards, so it stands one rank below the target
        char pawn = byWhite ? 'P' : 'p';
        int pawnRank = byWhite ? rank - 1 : rank + 1;
        if (pawnRank >= 0 && pawnRank < 8) {
            if (file > 0 && board.getPiece(pawnRank * 8 + file - 1) == pawn) {
                return true;
            }
            if (file < 7 && board.getPiece(pawnRank * 8 + file + 1) == pawn) {
                return true;
            }
        }

        // knights
        char knight = byWhite ? 'N' : 'n';
        for (int k = 0; k < 8; k++) {
            int f = file + KN_DF[k];
            int r = rank + KN_DR[k];
            if (f >= 0 && f < 8 && r >= 0 && r < 8 && board.getPiece(r * 8 + f) == knight) {
                return true;
            }
        }

        // king
        char king = byWhite ? 'K' : 'k';
        for (int dr = -1; dr <= 1; dr++) {
            for (int df = -1; df <= 1; df++) {
                if (df == 0 && dr == 0) {
                    continue;
                }
                int f = file + df;
                int r = rank + dr;
                if (f >= 0 && f < 8 && r >= 0 && r < 8 && board.getPiece(r * 8 + f) == king) {
                    return true;
                }
            }
        }

        char rook = byWhite ? 'R' : 'r';
        char bishop = byWhite ? 'B' : 'b';
        char queen = byWhite ? 'Q' : 'q';

        // rooks / queens along ranks and files
        for (int d = 0; d < 4; d++) {
            int f = file + ORTH_DF[d];
            int r = rank + ORTH_DR[d];
            while (f >= 0 && f < 8 && r >= 0 && r < 8) {
                char p = board.getPiece(r * 8 + f);
                if (p != '.') {
                    if (p == rook || p == queen) {
                        return true;
                    }
                    break;
                }
                f += ORTH_DF[d];
                r += ORTH_DR[d];
            }
        }

        // bishops / queens along diagonals
        for (int d = 0; d < 4; d++) {
            int f = file + DIAG_DF[d];
            int r = rank + DIAG_DR[d];
            while (f >= 0 && f < 8 && r >= 0 && r < 8) {
                char p = board.getPiece(r * 8 + f);
                if (p != '.') {
                    if (p == bishop || p == queen) {
                        return true;
                    }
                    break;
                }
                f += DIAG_DF[d];
                r += DIAG_DR[d];
            }
        }

        return false;
    }
}