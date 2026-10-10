package com.chess.engine.engine;

import java.util.*;

@SuppressWarnings("unused")
public class ChessEngine {

    private static final int CHECKMATE_SCORE = 100_000;
    private static final int INFINITY = 1_000_000;
    private static final int MAX_PLY = 64;

    // Transposition Table Entry Flags
    private static final byte EXACT = 0;
    private static final byte LOWERBOUND = 1; // Beta cut-off
    private static final byte UPPERBOUND = 2; // Alpha fail-low

    private static class TTEntry {

		long key;
        int depth;
        int score;
        byte flag;
        Move bestMove;

        TTEntry(long key, int depth, int score, byte flag, Move bestMove) {
            this.key = key;
            this.depth = depth;
            this.score = score;
            this.flag = flag;
            this.bestMove = bestMove;
        }
    }

    private final Map<Long, TTEntry> transpositionTable = new HashMap<>(200_000);
    private final Move[][] killerMoves = new Move[MAX_PLY][2];

    private Move bestMoveThisRootSearch = null;
    private long startTime;
    private long maxTimeMillis;
    private boolean timeUp = false;

    public ChessEngine() {
        // Default constructor
    }

    /**
     * Entry point using Iterative Deepening with an explicit time limit.
     */
    public Move findBestMove(Board board, int maxDepth, long timeLimitMillis) {
        this.startTime = System.currentTimeMillis();
        this.maxTimeMillis = timeLimitMillis;
        this.timeUp = false;

        Move bestMoveOverall = null;
        clearKillerMoves();
        transpositionTable.clear(); // the hash does not encode the variant, so never reuse entries across games

        // Iterative Deepening
        for (int currentDepth = 1; currentDepth <= maxDepth; currentDepth++) {
            bestMoveThisRootSearch = null;

            int score = alphaBeta(board, currentDepth, 0, -INFINITY, INFINITY, true);

            if (checkTimeout()) {
                // Return whatever best move we found up to the last fully completed depth
                break;
            }

            if (bestMoveThisRootSearch != null) {
                bestMoveOverall = bestMoveThisRootSearch;
            } else {
                TTEntry entry = transpositionTable.get(board.getZobristKey());
                if (entry != null && entry.bestMove != null) {
                    bestMoveOverall = entry.bestMove;
                }
            }

            // Early exit if a forced checkmate is guaranteed
            if (Math.abs(score) > CHECKMATE_SCORE - 200) {
                System.out.println("Forced mate found at depth " + currentDepth + " plies! Score: " + score);
                break;
            }
        }

        return bestMoveOverall;
    }

    /**
     * Overloaded entry point defaulting to a 5-second time limit.
     */
    public Move findBestMove(Board board, int maxDepth) {
        return findBestMove(board, maxDepth, 5000);
    }

    /**
     * Negamax Alpha-Beta with MVV-LVA, NMP, Transposition Table lookup, and Mate Distance Pruning.
     */
    private int alphaBeta(Board board, int depth, int ply, int alpha, int beta, boolean allowNull) {
        if (checkTimeout()) return 0;

        int originalAlpha = alpha;
        long zobristKey = board.getZobristKey();

        // --- 1. MATE DISTANCE PRUNING ---
        // Prune branches that cannot offer a faster checkmate than one already discovered
        int mateAlpha = -CHECKMATE_SCORE + ply;
        int mateBeta = CHECKMATE_SCORE - ply;

        if (alpha < mateAlpha) alpha = mateAlpha;
        if (beta > mateBeta) beta = mateBeta;
        if (alpha >= beta) return alpha;

        // Atomic chess: a king that has been blown off ends the game immediately
        if (board.isAtomic()) {
            if (AtomicChess.hasLostKing(board, board.isWhiteToMove())) return -CHECKMATE_SCORE + ply;
            if (AtomicChess.hasLostKing(board, !board.isWhiteToMove())) return CHECKMATE_SCORE - ply;
        }

        // Three-Check: the side that just moved delivered a third check, so the side to move has lost
        if (board.isThreeCheck() && (board.getWhiteChecks() >= 3 || board.getBlackChecks() >= 3)) {
            return -CHECKMATE_SCORE + ply;
        }

        // --- 2. TRANSPOSITION TABLE LOOKUP ---
        TTEntry ttEntry = transpositionTable.get(zobristKey);
        if (ttEntry != null && ttEntry.depth >= depth) {
            int ttScore = retrieveTTScore(ttEntry.score, ply);

            if (ttEntry.flag == EXACT) {
                if (ply == 0) bestMoveThisRootSearch = ttEntry.bestMove;
                return ttScore;
            } else if (ttEntry.flag == LOWERBOUND) {
                alpha = Math.max(alpha, ttScore);
            } else if (ttEntry.flag == UPPERBOUND) {
                beta = Math.min(beta, ttScore);
            }

            if (alpha >= beta) {
                if (ply == 0) bestMoveThisRootSearch = ttEntry.bestMove;
                return ttScore;
            }
        }

        // --- 3. BASE CASE / QUIESCENCE SEARCH ---
        if (depth <= 0) {
            return quiescenceSearch(board, alpha, beta, ply);
        }

        boolean inCheck = board.isInCheck(board.isWhiteToMove());

        // Single Check Extension: bounded by MAX_PLY to prevent stack overflow recursion
        int searchDepth = depth;
        if (inCheck && ply < MAX_PLY) {
            searchDepth++;
        }

        // --- 4. NULL MOVE PRUNING (NMP) ---
        if (allowNull && !inCheck && searchDepth >= 3 && hasNonPawnMaterial(board) && !board.isAntiChess()) {
            board.makeNullMove();
            int R = 2; // Reduction factor
            int nullScore = -alphaBeta(board, searchDepth - 1 - R, ply + 1, -beta, -beta + 1, false);
            board.undoNullMove();

            if (nullScore >= beta) {
                return beta; // Prune sub-tree!
            }
        }

        List<Move> legalMoves = board.generateLegalMoves();

        // Terminal Node Check: Checkmate or Stalemate
        if (legalMoves.isEmpty()) {
            if (board.isAntiChess()) {
                return CHECKMATE_SCORE - ply; // Anti-chess: no pieces / no moves means the side to move WINS
            }
            if (inCheck) {
                return -CHECKMATE_SCORE + ply; // Prefer faster checkmate
            }
            if (board.isHorde() && HordeChess.whiteHasNoPieces(board)) {
                return -CHECKMATE_SCORE + ply; // Horde chess: White has lost every piece
            }
            return 0; // Stalemate
        }

        // --- 5. MOVE ORDERING (MVV-LVA + TT + Killer Moves) ---
        Move ttMove = (ttEntry != null) ? ttEntry.bestMove : null;
        orderMoves(legalMoves, board, ttMove, ply);

        Move bestMoveLocal = null;
        int maxScore = -INFINITY;

        // --- 6. SEARCH LOOP ---
        for (Move move : legalMoves) {
            board.makeMove(move);

            int score = -alphaBeta(board, searchDepth - 1, ply + 1, -beta, -alpha, true);

            board.undoMove(move);

            if (timeUp) return 0;

            if (score > maxScore) {
                maxScore = score;
                bestMoveLocal = move;
                if (ply == 0) {
                    bestMoveThisRootSearch = move;
                }
            }

            alpha = Math.max(alpha, score);
            if (alpha >= beta) {
                // Store quiet move that caused beta cut-off as a Killer Move
                if (!move.isCapture() && ply < MAX_PLY) {
                    storeKillerMove(move, ply);
                }
                break; // Beta Cut-off
            }
        }

        // --- 7. TRANSPOSITION TABLE STORE ---
        byte flag;
        if (maxScore <= originalAlpha) {
            flag = UPPERBOUND;
        } else if (maxScore >= beta) {
            flag = LOWERBOUND;
        } else {
            flag = EXACT;
        }

        int scoreToStore = storeTTScore(maxScore, ply);

        // Store entry if slot is empty, deeper, or replaces an exact result
        if (ttEntry == null || searchDepth >= ttEntry.depth || flag == EXACT) {
            transpositionTable.put(zobristKey, new TTEntry(zobristKey, searchDepth, scoreToStore, flag, bestMoveLocal));
        }

        return maxScore;
    }

    /**
     * Quiescence Search resolves lingering tactical captures and check evasions.
     */
    private int quiescenceSearch(Board board, int alpha, int beta, int ply) {
        if (checkTimeout()) return 0;

        if (board.isAtomic()) {
            if (AtomicChess.hasLostKing(board, board.isWhiteToMove())) return -CHECKMATE_SCORE + ply;
            if (AtomicChess.hasLostKing(board, !board.isWhiteToMove())) return CHECKMATE_SCORE - ply;
        }

        if (board.isThreeCheck() && (board.getWhiteChecks() >= 3 || board.getBlackChecks() >= 3)) {
            return -CHECKMATE_SCORE + ply;
        }

        // Anti-chess: captures are forced, so just evaluate the leaf
        if (board.isAntiChess()) {
            if (AntiChess.hasNoPieces(board, board.isWhiteToMove())) return CHECKMATE_SCORE - ply;
            if (AntiChess.hasNoPieces(board, !board.isWhiteToMove())) return -CHECKMATE_SCORE + ply;
            return evaluate(board);
        }

        boolean inCheck = board.isInCheck(board.isWhiteToMove());

        if (!inCheck) {
            int standPat = evaluate(board);
            if (standPat >= beta) return beta;
            if (standPat > alpha) alpha = standPat;
        }

        List<Move> moves = inCheck ? board.generateLegalMoves() : board.generateCaptures();

        if (moves.isEmpty()) {
            if (inCheck) {
                return -CHECKMATE_SCORE + ply; // checkmate
            }
            // Quiet position with no captures: this is NOT stalemate, just a
            // leaf. Return the stand-pat evaluation (alpha was raised to it above).
            return alpha;
        }

        orderMoves(moves, board, null, ply);

        for (Move move : moves) {
            board.makeMove(move);

            int score = -quiescenceSearch(board, -beta, -alpha, ply + 1);

            board.undoMove(move);

            if (timeUp) return 0;

            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }

        return alpha;
    }

    /**
     * Order moves by PV/TT Move -> MVV-LVA Captures -> Promotions -> Killer Moves.
     */
    private void orderMoves(List<Move> moves, Board board, Move ttMove, int ply) {
        moves.sort((m1, m2) -> {
            int score1 = getMovePriority(m1, board, ttMove, ply);
            int score2 = getMovePriority(m2, board, ttMove, ply);
            return Integer.compare(score2, score1);
        });
    }

    /**
     * MVV-LVA Priority Calculation:
     * Priority = Victim Value * 10 - Attacker Value
     */
    private int getMovePriority(Move move, Board board, Move ttMove, int ply) {
        // 1. TT / PV Best Move
        if (ttMove != null && move.equals(ttMove)) {
            return 100_000;
        }

        // 2. MVV-LVA Captures
        if (move.isCapture()) {
            int victimValue = getPieceValue(board.getPieceTypeAt(move.to));
            int attackerValue = getPieceValue(board.getPieceTypeAt(move.from));
            return 10_000 + (victimValue * 10 - attackerValue);
        }

        // 3. Promotions
        if (move.getPromotionPiece() != '\0' && move.getPromotionPiece() != '.') {
            return 9_000;
        }

        // 4. Killer Moves
        if (ply < MAX_PLY) {
            if (move.equals(killerMoves[ply][0])) return 8_000;
            if (move.equals(killerMoves[ply][1])) return 7_000;
        }

        return 0;
    }

    private void storeKillerMove(Move move, int ply) {
        if (!move.equals(killerMoves[ply][0])) {
            killerMoves[ply][1] = killerMoves[ply][0];
            killerMoves[ply][0] = move;
        }
    }

    private void clearKillerMoves() {
        for (int i = 0; i < MAX_PLY; i++) {
            killerMoves[i][0] = null;
            killerMoves[i][1] = null;
        }
    }

    private boolean hasNonPawnMaterial(Board board) {
        return board.getNonPawnMaterialCount(board.isWhiteToMove()) > 0;
    }

    private int getPieceValue(char pieceType) {
        switch (Character.toUpperCase(pieceType)) {
            case 'P': return 100;
            case 'N': return 320;
            case 'B': return 330;
            case 'R': return 500;
            case 'Q': return 900;
            case 'K': return 20000;
            default: return 0;
        }
    }

    private int storeTTScore(int score, int ply) {
        if (score > CHECKMATE_SCORE - 200) {
            return score + ply;
        } else if (score < -CHECKMATE_SCORE + 200) {
            return score - ply;
        }
        return score;
    }

    private int retrieveTTScore(int score, int ply) {
        if (score > CHECKMATE_SCORE - 200) {
            return score - ply;
        } else if (score < -CHECKMATE_SCORE + 200) {
            return score + ply;
        }
        return score;
    }

    private final PositionEval positionEval = new PositionEval();

    /**
     * Static evaluation from the side-to-move's point of view
     * (material + piece placement, see PositionEval).
     */
    private int evaluate(Board board) {
        if (board.isAntiChess()) {
            return AntiChess.evaluate(board); // already from the side-to-move's point of view
        }

        int perspective = board.isWhiteToMove() ? 1 : -1;
        int score = positionEval.evaluateWhite(board);

        // Three-Check: every check already delivered is worth a lot
        if (board.isThreeCheck()) {
            score += (board.getWhiteChecks() - board.getBlackChecks()) * 80;
        }

        return score * perspective;
    }

    private boolean checkTimeout() {
        if (!timeUp && (System.currentTimeMillis() - startTime >= maxTimeMillis)) {
            timeUp = true;
        }
        return timeUp;
    }

    // =====================================================================
    //  Positional evaluation (centre, development, castling, king safety,
    //  pawn structure, outposts, open files, long diagonals, queen support,
    //  endgame mating technique)
    // =====================================================================
    private static final class PositionEval {

    private static final int P = 1, N = 2, B = 3, R = 4, Q = 5, K = 6;

    private static final int[] VALUE = {0, 100, 320, 330, 500, 900, 0};
    private static final int[] PHASE_W = {0, 0, 1, 1, 2, 4, 0};
    private static final int[] PASSED = {0, 5, 10, 20, 35, 60, 100, 0};

    private static final int[][] KNIGHT = new int[64][];
    private static final int[][] KING = new int[64][];
    private static final int[][][] RAYS = new int[64][8][];
    private static final int[][][] PAWN_ATT = new int[2][64][];
    private static final boolean[] CENTER = new boolean[64];

    // piece-square tables, written rank 8 first (white's point of view)
    private static final int[][] PST = new int[7][];

    private static final int[] KING_MG = {
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -20, -30, -30, -40, -40, -30, -30, -20,
            -10, -20, -20, -20, -20, -20, -20, -10,
            20, 20, 0, 0, 0, 0, 20, 20,
            20, 30, 10, 0, 0, 10, 30, 20};
    private static final int[] KING_EG = {
            -50, -40, -30, -20, -20, -30, -40, -50,
            -30, -20, -10, 0, 0, -10, -20, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -30, 0, 0, 0, 0, -30, -30,
            -50, -30, -30, -30, -30, -30, -30, -50};

    static {
        int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}, {1, 1}, {-1, -1}, {-1, 1}, {1, -1}};
        int[][] kn = {{1, 2}, {2, 1}, {-1, 2}, {-2, 1}, {1, -2}, {2, -1}, {-1, -2}, {-2, -1}};

        for (int s = 0; s < 64; s++) {
            int f = s & 7, r = s >> 3;

            for (int d = 0; d < 8; d++) {
                int[] tmp = new int[7];
                int n = 0;
                int ff = f + dirs[d][0], rr = r + dirs[d][1];
                while (ff >= 0 && ff < 8 && rr >= 0 && rr < 8) {
                    tmp[n++] = rr * 8 + ff;
                    ff += dirs[d][0];
                    rr += dirs[d][1];
                }
                RAYS[s][d] = Arrays.copyOf(tmp, n);
            }

            KING[s] = collect(f, r, dirs);
            KNIGHT[s] = collect(f, r, kn);
            PAWN_ATT[0][s] = collect(f, r, new int[][]{{-1, 1}, {1, 1}});
            PAWN_ATT[1][s] = collect(f, r, new int[][]{{-1, -1}, {1, -1}});
        }

        CENTER[27] = CENTER[28] = CENTER[35] = CENTER[36] = true;

        PST[P] = new int[]{
                0, 0, 0, 0, 0, 0, 0, 0,
                50, 50, 50, 50, 50, 50, 50, 50,
                10, 10, 20, 30, 30, 20, 10, 10,
                5, 5, 10, 25, 25, 10, 5, 5,
                0, 0, 0, 20, 20, 0, 0, 0,
                5, -5, -10, 0, 0, -10, -5, 5,
                5, 10, 10, -20, -20, 10, 10, 5,
                0, 0, 0, 0, 0, 0, 0, 0};
        PST[N] = new int[]{
                -50, -40, -30, -30, -30, -30, -40, -50,
                -40, -20, 0, 0, 0, 0, -20, -40,
                -30, 0, 10, 15, 15, 10, 0, -30,
                -30, 5, 15, 20, 20, 15, 5, -30,
                -30, 0, 15, 20, 20, 15, 0, -30,
                -30, 5, 10, 15, 15, 10, 5, -30,
                -40, -20, 0, 5, 5, 0, -20, -40,
                -50, -40, -30, -30, -30, -30, -40, -50};
        PST[B] = new int[]{
                -20, -10, -10, -10, -10, -10, -10, -20,
                -10, 0, 0, 0, 0, 0, 0, -10,
                -10, 0, 5, 10, 10, 5, 0, -10,
                -10, 5, 5, 10, 10, 5, 5, -10,
                -10, 0, 10, 10, 10, 10, 0, -10,
                -10, 10, 10, 10, 10, 10, 10, -10,
                -10, 5, 0, 0, 0, 0, 5, -10,
                -20, -10, -10, -10, -10, -10, -10, -20};
        PST[R] = new int[]{
                0, 0, 0, 0, 0, 0, 0, 0,
                5, 10, 10, 10, 10, 10, 10, 5,
                -5, 0, 0, 0, 0, 0, 0, -5,
                -5, 0, 0, 0, 0, 0, 0, -5,
                -5, 0, 0, 0, 0, 0, 0, -5,
                -5, 0, 0, 0, 0, 0, 0, -5,
                -5, 0, 0, 0, 0, 0, 0, -5,
                0, 0, 0, 5, 5, 0, 0, 0};
        PST[Q] = new int[]{
                -20, -10, -10, -5, -5, -10, -10, -20,
                -10, 0, 0, 0, 0, 0, 0, -10,
                -10, 0, 5, 5, 5, 5, 0, -10,
                -5, 0, 5, 5, 5, 5, 0, -5,
                0, 0, 5, 5, 5, 5, 0, -5,
                -10, 5, 5, 5, 5, 5, 0, -10,
                -10, 0, 5, 0, 0, 0, 0, -10,
                -20, -10, -10, -5, -5, -10, -10, -20};
        PST[K] = KING_MG;
    }

    private static int[] collect(int f, int r, int[][] offsets) {
        int[] tmp = new int[offsets.length];
        int n = 0;
        for (int[] o : offsets) {
            int ff = f + o[0], rr = r + o[1];
            if (ff >= 0 && ff < 8 && rr >= 0 && rr < 8) {
                tmp[n++] = rr * 8 + ff;
            }
        }
        return Arrays.copyOf(tmp, n);
    }

    private final int[] sq = new int[64];      // + white piece type, - black piece type
    private final int[] kingSq = new int[2];
    private int side;                          // 0 = white to move, 1 = black
    private int castle;                        // 1 = WK, 2 = WQ, 4 = BK, 8 = BQ

    private final int[][] pawnCnt = new int[2][8];
    private final int[][] pawnMax = new int[2][8];
    private final int[][] pawnMin = new int[2][8];
    private final boolean[][] passedFile = new boolean[2][8];
    private final boolean[][] zoneOf = new boolean[2][64];
    private final int[] npMat = new int[2];
    private final int[] bishopCnt = new int[2];
    private final int[] attCount = new int[2];
    private final int[] attVal = new int[2];
    private final int[] queenSq = new int[2];
    private final int[][] rookSq = new int[2][2];
    private final int[] rookN = new int[2];

    private static int pieceCode(char c) {
        int t;
        switch (Character.toLowerCase(c)) {
            case 'p': t = P; break;
            case 'n': t = N; break;
            case 'b': t = B; break;
            case 'r': t = R; break;
            case 'q': t = Q; break;
            case 'k': t = K; break;
            default: return 0;
        }
        return Character.isUpperCase(c) ? t : -t;
    }

    /** Static evaluation in centipawns, positive = good for White. */
    int evaluateWhite(Board board) {
        int k0 = 0, k1 = 0;
        kingSq[0] = kingSq[1] = -1;
        int material = 0;

        for (int s = 0; s < 64; s++) {
            int p = pieceCode(board.getPiece(s));
            sq[s] = p;
            if (p == K) { kingSq[0] = s; k0++; }
            else if (p == -K) { kingSq[1] = s; k1++; }
            int v = VALUE[Math.abs(p)];
            material += p > 0 ? v : -v;
        }

        // positions without exactly one king each (board editor) get plain material
        if (k0 != 1 || k1 != 1) {
            return material;
        }

        side = board.isWhiteToMove() ? 0 : 1;
        castle = (board.canWhiteCastleKingSide() ? 1 : 0)
                | (board.canWhiteCastleQueenSide() ? 2 : 0)
                | (board.canBlackCastleKingSide() ? 4 : 0)
                | (board.canBlackCastleQueenSide() ? 8 : 0);

        return evaluate();
    }

    private static int chebyshev(int a, int b) {
        return Math.max(Math.abs((a & 7) - (b & 7)), Math.abs((a >> 3) - (b >> 3)));
    }

    /** Static evaluation, positive = good for White. */
    private int evaluate() {
        int phase = 0;
        int score = 0;

        for (int c = 0; c < 2; c++) {
            Arrays.fill(pawnCnt[c], 0);
            Arrays.fill(pawnMax[c], -1);
            Arrays.fill(pawnMin[c], 8);
            Arrays.fill(passedFile[c], false);
            Arrays.fill(zoneOf[c], false);
            npMat[c] = 0;
            bishopCnt[c] = 0;
            attCount[c] = 0;
            attVal[c] = 0;
            queenSq[c] = -1;
            rookN[c] = 0;
        }

        // pass A: material, piece-square values, pawn files
        for (int s = 0; s < 64; s++) {
            int p = sq[s];
            if (p == 0) continue;
            int t = Math.abs(p);
            int c = p > 0 ? 0 : 1;

            if (t == P) {
                int f = s & 7, r = s >> 3;
                pawnCnt[c][f]++;
                if (r > pawnMax[c][f]) pawnMax[c][f] = r;
                if (r < pawnMin[c][f]) pawnMin[c][f] = r;
            } else if (t != K) {
                npMat[c] += VALUE[t];
                phase += PHASE_W[t];
                if (t == B) bishopCnt[c]++;
                if (t == Q) queenSq[c] = s;
            }

            if (t != K) {
                int v = VALUE[t] + PST[t][c == 0 ? s ^ 56 : s];
                score += c == 0 ? v : -v;
            }
        }
        if (phase > 24) phase = 24;

        for (int c = 0; c < 2; c++) {
            int k = kingSq[c];
            zoneOf[c][k] = true;
            for (int t : KING[k]) zoneOf[c][t] = true;
        }

        // pass B: pawn structure
        for (int s = 0; s < 64; s++) {
            int p = sq[s];
            if (Math.abs(p) != P) continue;
            int c = p > 0 ? 0 : 1;
            int f = s & 7, r = s >> 3;
            int rr = c == 0 ? r : 7 - r;
            int pv = 0;

            if (pawnCnt[c][f] > 1) pv -= 6;

            boolean left = f > 0 && pawnCnt[c][f - 1] > 0;
            boolean right = f < 7 && pawnCnt[c][f + 1] > 0;
            if (!left && !right) pv -= 12;

            boolean passed = true;
            for (int ff = Math.max(0, f - 1); ff <= Math.min(7, f + 1); ff++) {
                if (c == 0 ? pawnMax[1][ff] > r : pawnMin[0][ff] < r) {
                    passed = false;
                    break;
                }
            }
            if (passed) {
                passedFile[c][f] = true;
                int b = PASSED[rr];
                pv += b + b * (24 - phase) / 24;
            }

            for (int t : PAWN_ATT[1 - c][s]) {
                if (sq[t] == p) pv += 5; // protected by a pawn
            }
            for (int t : PAWN_ATT[c][s]) {
                if (CENTER[t]) pv += 5;  // pawn attacks a central square
            }

            score += c == 0 ? pv : -pv;
        }

        // pass C: pieces
        for (int s = 0; s < 64; s++) {
            int p = sq[s];
            int t = Math.abs(p);
            if (t == 0 || t == P || t == K) continue;

            int c = p > 0 ? 0 : 1;
            int f = s & 7, r = s >> 3;
            int rr = c == 0 ? r : 7 - r;
            int pv = 0;
            int mob = 0, centerHits = 0;
            boolean zoneHit = false;
            boolean[] enemyZone = zoneOf[1 - c];
            int sign = c == 0 ? 1 : -1;

            switch (t) {
                case N: {
                    for (int tg : KNIGHT[s]) {
                        int tp = sq[tg] * sign;
                        if (tp <= 0) mob++;
                        if (CENTER[tg]) centerHits++;
                        if (enemyZone[tg]) zoneHit = true;
                    }
                    pv += (mob - 4) * 4 + centerHits * 3;

                    if (rr >= 3 && rr <= 5) {
                        boolean supported = false;
                        for (int tg : PAWN_ATT[1 - c][s]) {
                            if (sq[tg] == P * sign) supported = true;
                        }
                        boolean attackable = false;
                        for (int ff = f - 1; ff <= f + 1; ff += 2) {
                            if (ff < 0 || ff > 7) continue;
                            if (c == 0 ? pawnMax[1][ff] > r : pawnMin[0][ff] < r) attackable = true;
                        }
                        if (supported && !attackable) pv += 18;
                    }

                    boolean home = c == 0 ? (s == 1 || s == 6) : (s == 57 || s == 62);
                    if (home) pv -= 10 * phase / 24;

                    if (zoneHit) { attCount[c]++; attVal[c] += 2; }
                    break;
                }
                case B: {
                    for (int d = 4; d < 8; d++) {
                        for (int tg : RAYS[s][d]) {
                            int tp = sq[tg] * sign;
                            if (tp <= 0) mob++;
                            if (CENTER[tg]) centerHits++;
                            if (enemyZone[tg]) zoneHit = true;
                            if (tp != 0) break;
                        }
                    }
                    pv += (mob - 6) * 3 + centerHits * 3;

                    boolean longDiag = s % 9 == 0 || (s % 7 == 0 && s != 0 && s != 63);
                    if (longDiag) pv += 10;                       // long diagonal
                    if (longDiag && zoneHit) pv += 12;            // ... eyeing the enemy king

                    // fianchetto bishop (g2/b2, g7/b7) guarding its own castled king
                    int ownK = kingSq[c];
                    if (c == 0) {
                        if (s == 14 && (ownK == 5 || ownK == 6 || ownK == 7)) pv += 18;
                        else if (s == 9 && (ownK == 0 || ownK == 1 || ownK == 2)) pv += 18;
                    } else {
                        if (s == 54 && (ownK == 61 || ownK == 62 || ownK == 63)) pv += 18;
                        else if (s == 49 && (ownK == 56 || ownK == 57 || ownK == 58)) pv += 18;
                    }

                    boolean home = c == 0 ? (s == 2 || s == 5) : (s == 58 || s == 61);
                    if (home) pv -= 10 * phase / 24;

                    if (zoneHit) { attCount[c]++; attVal[c] += 2; }
                    break;
                }
                case R: {
                    for (int d = 0; d < 4; d++) {
                        for (int tg : RAYS[s][d]) {
                            int tp = sq[tg] * sign;
                            if (tp <= 0) mob++;
                            if (enemyZone[tg]) zoneHit = true;
                            if (tp != 0) break;
                        }
                    }
                    pv += (mob - 7) * 2;

                    if (pawnCnt[c][f] == 0) {
                        pv += pawnCnt[1 - c][f] == 0 ? 20 : 10; // open / semi-open file
                    } else {
                        boolean pawnAhead = c == 0 ? pawnMax[0][f] > r : pawnMin[1][f] < r;
                        if (pawnAhead) {
                            pv += 6;                                 // rook behind its own pawn
                            if (passedFile[c][f]) pv += 15;          // ... a passed pawn
                        }
                    }
                    if (rr == 6) pv += 15;

                    if (rookN[c] < 2) rookSq[c][rookN[c]++] = s;

                    // rooks on the central files back up central pawn breaks
                    if (f >= 2 && f <= 5) pv += 4;

                    // rook on the back rank beside a castled king guards it
                    int rk = kingSq[c];
                    boolean kingCastled = (rk >> 3) == (c == 0 ? 0 : 7) && ((rk & 7) == 6 || (rk & 7) == 2);
                    if (kingCastled && rr == 0 && Math.abs((rk & 7) - f) <= 2) pv += 10;

                    if (zoneHit) { attCount[c]++; attVal[c] += 3; }
                    break;
                }
                case Q: {
                    for (int d = 0; d < 8; d++) {
                        for (int tg : RAYS[s][d]) {
                            int tp = sq[tg] * sign;
                            if (tp <= 0) mob++;
                            if (enemyZone[tg]) zoneHit = true;
                            if (tp != 0) break;
                        }
                    }
                    pv += (Math.min(mob, 16) - 8) / 2;

                    // queen backing up our own pieces along its lines
                    for (int d = 0; d < 8; d++) {
                        for (int tg : RAYS[s][d]) {
                            int tp = sq[tg] * sign;
                            if (tp == 0) continue;
                            if (tp >= N && tp <= R) pv += 3;
                            break;
                        }
                    }

                    int homeSq = c == 0 ? 3 : 59;
                    if (s != homeSq && phase >= 18) {
                        int undeveloped = 0;
                        int[] minorHome = c == 0 ? new int[]{1, 2, 5, 6} : new int[]{57, 58, 61, 62};
                        for (int h : minorHome) {
                            int hp = Math.abs(sq[h]);
                            if ((hp == N || hp == B) && (sq[h] > 0) == (c == 0)) undeveloped++;
                        }
                        if (undeveloped >= 2) pv -= 15;
                    }

                    if (zoneHit) { attCount[c]++; attVal[c] += 5; }
                    break;
                }
                default:
                    break;
            }

            score += c == 0 ? pv : -pv;
        }

        for (int c = 0; c < 2; c++) {
            int pv = 0;

            if (bishopCnt[c] >= 2) pv += 30;

            // connected rooks (only happens once the minor pieces and queen are developed)
            if (rookN[c] == 2) {
                int a = rookSq[c][0], b = rookSq[c][1];
                boolean connected = false;
                if ((a >> 3) == (b >> 3)) {
                    connected = true;
                    for (int x = Math.min(a, b) + 1; x < Math.max(a, b); x++) {
                        if (sq[x] != 0) { connected = false; break; }
                    }
                } else if ((a & 7) == (b & 7)) {
                    connected = true;
                    for (int x = Math.min(a, b) + 8; x < Math.max(a, b); x += 8) {
                        if (sq[x] != 0) { connected = false; break; }
                    }
                }
                if (connected) pv += 12;
            }

            // queen too far from a king that is being attacked: drop back
            if (attCount[1 - c] >= 2 && queenSq[c] >= 0 && chebyshev(queenSq[c], kingSq[c]) > 5) {
                pv -= 10;
            }

            // attack on the enemy king zone
            if (attCount[c] >= 2) {
                pv += attVal[c] * attCount[c] * 2 * phase / 24;
            }
            // our queen helps defend the king when it is under attack
            if (attCount[1 - c] >= 2 && queenSq[c] >= 0 && chebyshev(queenSq[c], kingSq[c]) <= 3) {
                pv += 15;
            }

            score += c == 0 ? pv : -pv;
        }

        // kings
        for (int c = 0; c < 2; c++) {
            int k = kingSq[c];
            int kf = k & 7, kr = k >> 3;
            int rr = c == 0 ? kr : 7 - kr;
            int idx = c == 0 ? k ^ 56 : k;

            int kv = (KING_MG[idx] * phase + KING_EG[idx] * (24 - phase)) / 24;

            if (rr <= 1) {
                int dir = c == 0 ? 1 : -1;
                int own = c == 0 ? P : -P;
                int r1 = kr + dir, r2 = kr + 2 * dir;
                int shield = 0;
                for (int ff = Math.max(0, kf - 1); ff <= Math.min(7, kf + 1); ff++) {
                    if (r1 >= 0 && r1 < 8 && sq[r1 * 8 + ff] == own) shield += 10;
                    else if (r2 >= 0 && r2 < 8 && sq[r2 * 8 + ff] == own) shield += 5;
                    else shield -= 8;
                }
                kv += shield * phase / 24;
            }

            int rights = c == 0 ? (castle & 3) : ((castle >> 2) & 3);
            if (rr == 0 && (kf == 6 || kf == 2)) {
                kv += 25 * phase / 24;                        // castled
            } else if (rights == 0 && rr <= 3 && kf >= 2 && kf <= 5) {
                kv -= 20 * phase / 24;                        // in the centre with castling lost
            }

            score += c == 0 ? kv : -kv;
        }

        // mating technique: drive the lone king to the edge and bring our king closer
        int pawns0 = 0, pawns1 = 0;
        for (int f = 0; f < 8; f++) {
            pawns0 += pawnCnt[0][f];
            pawns1 += pawnCnt[1][f];
        }
        int lead = (npMat[0] + 100 * pawns0) - (npMat[1] + 100 * pawns1);
        if (phase <= 14 && Math.abs(lead) >= 250) {
            int strong = lead > 0 ? 0 : 1;
            int weak = 1 - strong;
            int wk = kingSq[weak], sk = kingSq[strong];
            int wf = wk & 7, wr = wk >> 3;
            int centerDist = Math.max(3 - wf, wf - 4) + Math.max(3 - wr, wr - 4);
            int manhattan = Math.abs(wf - (sk & 7)) + Math.abs(wr - (sk >> 3));
            int mop = 10 * centerDist + 4 * (14 - manhattan);
            score += strong == 0 ? mop : -mop;
        }

        score += side == 0 ? 10 : -10;
        return score;
       }
   }
}