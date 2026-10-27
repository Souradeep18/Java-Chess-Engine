package com.chess.engine.engine;

import java.util.*;

@SuppressWarnings("unused")
public class ChessEngine {

    private static final int CHECKMATE_SCORE = 100_000;
    private static final int INFINITY = 1_000_000;
    private static final int MAX_PLY = 128;
    private static final int PATH_SIZE = 512;

    // Fixed-size transposition table (4M entries)
    private static final int TT_BITS = 22;
    private static final int TT_SIZE = 1 << TT_BITS;
    private static final int TT_MASK = TT_SIZE - 1;

    // Pruning margins & LMR Table
    private static final int[] FUTILITY_MARGIN = {0, 150, 300, 450};
    private static final int NO_EVAL = Integer.MIN_VALUE;
    private final int[] evalStack = new int[MAX_PLY + 4];
    private static final int[][] LMR = new int[64][64];
    private static final int[] LMP_IMPROVING = {0, 8, 12, 18, 99};
    private static final int[] LMP_STEADY = {0, 5, 8, 12, 99};

    static {
        for (int d = 1; d < 64; d++) {
            for (int m = 1; m < 64; m++) {
                // Slightly conservative LMR formula to avoid pruning subtle tactical defenses
                LMR[d][m] = (int) (0.5 + Math.log(d) * Math.log(m) / 3.2);
            }
        }
    }
    private static final int RFP_MARGIN = 120;

    // TT Entry Flags
    private static final byte EXACT = 0;
    private static final byte LOWERBOUND = 1;
    private static final byte UPPERBOUND = 2;

    private static class TTEntry {
        final long key;
        final int depth;
        final int score;
        final byte flag;
        final Move bestMove;

        TTEntry(long key, int depth, int score, byte flag, Move bestMove) {
            this.key = key;
            this.depth = depth;
            this.score = score;
            this.flag = flag;
            this.bestMove = bestMove;
        }
    }

    private final TTEntry[] tt;
    private final Move[][] killerMoves = new Move[MAX_PLY][2];
    private final int[][][] history = new int[2][64][64];

    private final long[] pathKeys = new long[PATH_SIZE];
    private final Map<Long, Integer> gameHistory = new HashMap<>();
    private int contempt = 0;

    private Move bestMoveThisRootSearch = null;
    private int bestScoreThisRootSearch = -INFINITY;   
    private int rootAlphaThisSearch = -INFINITY;       
    private long rootBaseTime = 0;                     
    private int panicExtensions = 0;                   
    private int rootIterationDepth = 0;
    private long startTime;
    private long maxTimeMillis;
    private boolean timeUp = false;
    private boolean useBook = true;
    private long nodes = 0;

    public static final int MOVE_MAX_DEPTH = 24;
    public static final long MOVE_TIME_MILLIS = 5000;

    private final boolean helperEngine; 
    private int searchThreads = defaultThreads();
    private int analysisThreads = 1;
    private final List<ChessEngine> helpers = new ArrayList<>();

    public ChessEngine() {
        this.tt = new TTEntry[TT_SIZE];
        this.helperEngine = false;
    }

    private ChessEngine(TTEntry[] sharedTable) {
        this.tt = sharedTable;
        this.helperEngine = true;
    }

    private static int defaultThreads() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(8, cores - 1));
    }

    public void setThreads(int threads) {
        this.searchThreads = Math.max(1, Math.min(32, threads));
    }

    public void setAnalysisThreads(int threads) {
        this.analysisThreads = Math.max(1, Math.min(32, threads));
    }

    public static int recommendedThreads() {
        return defaultThreads();
    }

    private List<Thread> startHelpers(Board root, int maxDepth, int total, int engineSide, int contemptValue) {
        List<Thread> started = new ArrayList<>();
        int n = total - 1;
        if (helperEngine || n <= 0) {
            return started;
        }
        while (helpers.size() < n) {
            helpers.add(new ChessEngine(tt));
        }
        final Map<Long, Integer> historyCopy = new HashMap<>(gameHistory);
        for (int i = 0; i < n; i++) {
            final ChessEngine h = helpers.get(i);
            final Board hb = root.copy();
            final int index = i;
            h.cancelled = false;
            Thread t = new Thread(() -> h.helperLoop(hb, maxDepth, index, historyCopy, engineSide, contemptValue),
                    "chess-helper-" + i);
            t.setDaemon(true);
            t.start();
            started.add(t);
        }
        return started;
    }

    private void stopHelpers(List<Thread> started) {
        for (int i = 0; i < started.size(); i++) {
            helpers.get(i).cancelled = true;
        }
        for (Thread t : started) {
            try {
                t.join(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void helperLoop(Board board, int maxDepth, int index, Map<Long, Integer> historyCopy, int engineSide,
                            int contemptValue) {
        this.startTime = System.currentTimeMillis();
        this.maxTimeMillis = Long.MAX_VALUE / 4;
        this.timeUp = false;
        this.nodes = 0;
        clearKillerMoves();
        clearHistory();
        gameHistory.clear();
        gameHistory.putAll(historyCopy);
        positionEval.setEngineSide(engineSide);
        contempt = contemptValue;
        pathKeys[0] = board.getZobristKey();
        try {
            for (int d = 1 + (index % 3); d <= maxDepth && !checkTimeout(); d++) {
                bestMoveThisRootSearch = null;
                alphaBeta(board, d, 0, -INFINITY, INFINITY, true);
            }
        } catch (RuntimeException ignored) {
        }
    }

    public void setUseBook(boolean useBook) {
        this.useBook = useBook;
    }

    private static int ttIndex(long key) {
        return ((int) (key ^ (key >>> 32))) & TT_MASK;
    }

    private TTEntry ttGet(long key) {
        TTEntry e = tt[ttIndex(key)];
        return (e != null && e.key == key) ? e : null;
    }

    private void ttPut(TTEntry entry) {
        int idx = ttIndex(entry.key);
        TTEntry existing = tt[idx];
        // Prefer deeper entries or replace entries from matching keys
        if (existing == null || entry.depth >= existing.depth - 2 || existing.key == entry.key) {
            tt[idx] = entry;
        }
    }

    private void ttClear() {
        Arrays.fill(tt, null);
    }

    private String tableSignature = "";

    private void prepareTables(Board board) {
        String sig = (board.isAtomic() ? "A" : "") + (board.isAntiChess() ? "N" : "") + (board.isRacingKings() ? "R" : "")
                + (board.isThreeCheck() ? "T" : "") + (board.isKingOfTheHill() ? "K" : "") + (board.isHorde() ? "H" : "")
                + (board.isChess960() ? "9" : "") + (board.isWhiteToMove() ? "w" : "b");
        if (!sig.equals(tableSignature)) {
            ttClear();
            evalCacheClear();
            for (ChessEngine h : helpers) h.evalCacheClear();
            tableSignature = sig;
        }
    }

    private void prepareAnalysisTables(Board board) {
        String sig = "analysis" + (board.isAtomic() ? "A" : "") + (board.isAntiChess() ? "N" : "") + (board.isRacingKings() ? "R" : "")
                + (board.isThreeCheck() ? "T" : "") + (board.isKingOfTheHill() ? "K" : "") + (board.isHorde() ? "H" : "")
                + (board.isChess960() ? "9" : "");
        if (!sig.equals(tableSignature)) {
            ttClear();
            evalCacheClear();
            for (ChessEngine h : helpers) h.evalCacheClear();
            tableSignature = sig;
        }
    }

    private static boolean standardLike(Board b) {
        return !(b.isAtomic() || b.isAntiChess() || b.isRacingKings()
                || b.isThreeCheck() || b.isKingOfTheHill() || b.isHorde());
    }

    public Move findBestMove(Board board, int maxDepth, long timeLimitMillis, List<Board> previousPositions) {
        this.startTime = System.currentTimeMillis();
        this.maxTimeMillis = timeLimitMillis;
        this.timeUp = false;
        this.nodes = 0;
        this.rootBaseTime = timeLimitMillis;
        this.panicExtensions = 0;

        if (useBook && OpeningBook.isStandard(board)) {
            Move bookMove = OpeningBook.probe(board);
            if (bookMove != null) {
                return bookMove;
            }
        }

        List<Move> rootLegal = board.generateLegalMoves();
        if (rootLegal.isEmpty()) {
            return null;
        }
        if (rootLegal.size() == 1) {
            return rootLegal.get(0);
        }

        clearKillerMoves();
        clearHistory();
        prepareTables(board);

        gameHistory.clear();
        if (previousPositions != null) {
            for (Board b : previousPositions) {
                gameHistory.merge(b.getZobristKey(), 1, Integer::sum);
            }
        }

        positionEval.setEngineSide(board.isWhiteToMove() ? 0 : 1);
        contempt = contemptFor(evaluate(board));

        Move bestMoveOverall = null;
        int prevScore = 0;
        boolean havePrev = false;

        int extensions = 0;
        final long baseTime = maxTimeMillis;
        int bestMateScore = 0;          
        Move bestMateMove = null;

        List<Thread> helperThreads = maxTimeMillis >= 400
                ? startHelpers(board, maxDepth, searchThreads, board.isWhiteToMove() ? 0 : 1, contempt)
                : new ArrayList<>();
        try {
            for (int currentDepth = 1; currentDepth <= maxDepth; currentDepth++) {
                int score;
                Move previousBest = bestMoveOverall;
                rootIterationDepth = currentDepth;

                if (currentDepth >= 3 && havePrev && Math.abs(prevScore) < CHECKMATE_SCORE - 200) {
                    int delta = 25;
                    int a = prevScore - delta;
                    int b = prevScore + delta;
                    while (true) {
                        bestMoveThisRootSearch = null;
                        score = alphaBeta(board, currentDepth, 0, a, b, true);
                        if (timeUp) break;
                        if (score <= a) {
                            a = Math.max(-INFINITY, a - delta * 2);
                        } else if (score >= b) {
                            b = Math.min(INFINITY, b + delta * 2);
                        } else {
                            break;
                        }
                        delta *= 2;
                    }
                } else {
                    bestMoveThisRootSearch = null;
                    score = alphaBeta(board, currentDepth, 0, -INFINITY, INFINITY, true);
                }

                if (checkTimeout()) {
                    if (currentDepth > 1 && bestMoveThisRootSearch != null
                            && bestScoreThisRootSearch > rootAlphaThisSearch) {
                        bestMoveOverall = bestMoveThisRootSearch;
                    }
                    if (bestMateMove != null && !(bestMoveThisRootSearch != null && score > bestMateScore)) {
                        bestMoveOverall = bestMateMove;
                    }
                    break;
                }

                if (bestMoveThisRootSearch != null) {
                    bestMoveOverall = bestMoveThisRootSearch;
                } else {
                    TTEntry entry = ttGet(board.getZobristKey());
                    if (entry != null && entry.bestMove != null) {
                        bestMoveOverall = entry.bestMove;
                    }
                }

                if (score > CHECKMATE_SCORE - 200) {
                    if (score > bestMateScore) {
                        bestMateScore = score;
                        bestMateMove = bestMoveOverall;
                    }
                } else if (bestMateMove != null) {
                    bestMoveOverall = bestMateMove;
                    score = bestMateScore;
                }

                boolean unstable = havePrev && (score < prevScore - 35
                        || (previousBest != null && bestMoveOverall != null && !bestMoveOverall.equals(previousBest)));
                if (unstable && currentDepth >= 5 && extensions < 2) {
                    maxTimeMillis += baseTime / 4;
                    extensions++;
                }

                prevScore = score;
                havePrev = true;
                contempt = contemptFor(score);

                System.out.println("info depth " + currentDepth + " score " + score + " nodes " + nodes
                        + " time " + (System.currentTimeMillis() - startTime) + "ms");

                if (Math.abs(score) > CHECKMATE_SCORE - 200 && currentDepth >= CHECKMATE_SCORE - Math.abs(score)) {
                    break;
                }
            }
        } finally {
            stopHelpers(helperThreads);
        }

        if (bestMoveOverall == null) {
            bestMoveOverall = rootLegal.get(0);
        }
        return bestMoveOverall;
    }

    public boolean isTheoryMove(Board board, Move move) {
        if (move == null || !OpeningBook.isStandard(board)) {
            return false;
        }
        for (int i = 0; i < 40; i++) {
            Move candidate = OpeningBook.probe(board);
            if (candidate == null) {
                return false;
            }
            if (candidate.equals(move)) {
                return true;
            }
        }
        return false;
    }

    public Move findBestMove(Board board, int maxDepth, long timeLimitMillis) {
        return findBestMove(board, maxDepth, timeLimitMillis, null);
    }

    public Move findBestMove(Board board, int maxDepth) {
        return findBestMove(board, maxDepth, MOVE_TIME_MILLIS, null);
    }

    public Move findBestMove(Board board, List<Board> previousPositions) {
        return findBestMove(board, MOVE_MAX_DEPTH, MOVE_TIME_MILLIS, previousPositions);
    }

    private int contemptFor(int score) {
        if (Math.abs(score) > CHECKMATE_SCORE - 200) return contempt;
        if (score >= 80) return Math.min(350, 60 + score / 2);
        if (score <= -250) return 0;
        return 20;
    }

    private int drawScore(Board board, int ply) {
        int currentEval = positionEval.evaluateWhite(board);
        int enginePerspectiveEval = board.isWhiteToMove() ? currentEval : -currentEval;

        if (enginePerspectiveEval > 100) {
            return -Math.min(150, Math.max(30, contempt));
        } else if (enginePerspectiveEval < -100) {
            return 60;
        }

        return (ply & 1) == 0 ? -contempt : contempt;
    }

    private boolean isRepetition(long key, int ply) {
        for (int i = ply - 2; i >= 0; i -= 2) {
            if (pathKeys[i] == key) return true;
        }
        return gameHistory.containsKey(key);
    }

    private int alphaBeta(Board board, int depth, int ply, int alpha, int beta, boolean allowNull) {
        if (checkTimeout()) return 0;
        if (ply >= PATH_SIZE - 2) return evaluate(board);
        nodes++;

        int originalAlpha = alpha;
        long zobristKey = board.getZobristKey();
        pathKeys[ply] = zobristKey;
        int side = board.isWhiteToMove() ? 0 : 1;

        int mateAlpha = -CHECKMATE_SCORE + ply;
        int mateBeta = CHECKMATE_SCORE - ply;

        if (alpha < mateAlpha) alpha = mateAlpha;
        if (beta > mateBeta) beta = mateBeta;
        if (alpha >= beta) return alpha;

        if (board.isAtomic()) {
            if (AtomicChess.hasLostKing(board, board.isWhiteToMove())) return -CHECKMATE_SCORE + ply;
            if (AtomicChess.hasLostKing(board, !board.isWhiteToMove())) return CHECKMATE_SCORE - ply;
        }

        if (board.isThreeCheck() && (board.getWhiteChecks() >= 3 || board.getBlackChecks() >= 3)) {
            return -CHECKMATE_SCORE + ply;
        }

        if (board.isRacingKings()) {
            int outcome = RacingKings.outcome(board);
            if (outcome == 2) return 0;
            if (outcome != 0) {
                boolean sideToMoveWon = (outcome == 1) == board.isWhiteToMove();
                return sideToMoveWon ? CHECKMATE_SCORE - ply : -CHECKMATE_SCORE + ply;
            }
        }

        if (board.isKingOfTheHill()) {
            if (KingOfTheHill.hasKingOnHill(board, !board.isWhiteToMove())) return -CHECKMATE_SCORE + ply;
            if (KingOfTheHill.hasKingOnHill(board, board.isWhiteToMove())) return CHECKMATE_SCORE - ply;
        }

        if (ply > 0 && isRepetition(zobristKey, ply)) {
            return drawScore(board, ply);
        }

        TTEntry ttEntry = ttGet(zobristKey);
        if (ttEntry != null && ply > 0 && ttEntry.depth >= depth) {
            int ttScore = retrieveTTScore(ttEntry.score, ply);

            if (ttEntry.flag == EXACT) {
                return ttScore;
            } else if (ttEntry.flag == LOWERBOUND) {
                alpha = Math.max(alpha, ttScore);
            } else if (ttEntry.flag == UPPERBOUND) {
                beta = Math.min(beta, ttScore);
            }

            if (alpha >= beta) {
                return ttScore;
            }
        }

        if (depth <= 0) {
            return quiescenceSearch(board, alpha, beta, ply);
        }

        boolean inCheck = board.isInCheck(board.isWhiteToMove());
        int searchDepth = depth;
        // Search extension on check to ensure calculation stability
        if (inCheck && ply < MAX_PLY) {
            searchDepth++;
        }

        boolean pvNode = beta - alpha > 1;
        boolean evalKnown = false;
        int staticEval = 0;

        evalStack[ply] = NO_EVAL;
        if (ply > 0 && !inCheck && standardLike(board)) {
            staticEval = evaluate(board);
            evalKnown = true;
            evalStack[ply] = staticEval;
        }
        boolean improving = evalKnown && ply >= 2 && evalStack[ply - 2] != NO_EVAL && staticEval > evalStack[ply - 2];

        // Reverse Futility Pruning
        if (evalKnown && !pvNode && depth <= 4
                && hasNonPawnMaterial(board)
                && Math.abs(beta) < CHECKMATE_SCORE - 200
                && staticEval - RFP_MARGIN * depth >= beta) {
            return staticEval - RFP_MARGIN * depth;
        }

        // Futility Pruning
        boolean futilityOk = evalKnown && !pvNode
                && depth <= 3
                && Math.abs(alpha) < CHECKMATE_SCORE - 200
                && staticEval + FUTILITY_MARGIN[depth] <= alpha;

        if (!inCheck && searchDepth >= 5 && ply > 0 && (ttEntry == null || ttEntry.bestMove == null)) {
            searchDepth--;
        }

        // Null Move Pruning
        if (allowNull && !inCheck && searchDepth >= 3 && hasNonPawnMaterial(board)
                && !board.isAntiChess() && !board.isRacingKings()
                && (evalKnown ? staticEval : evaluate(board)) >= beta) {
            board.makeNullMove();
            int R = searchDepth >= 6 ? 3 : 2;
            R = Math.min(R, searchDepth - 1);
            int nullScore = -alphaBeta(board, searchDepth - 1 - R, ply + 1, -beta, -beta + 1, false);
            board.undoNullMove();

            if (timeUp) return 0;
            if (nullScore >= beta) {
                return beta;
            }
        }

        List<Move> legalMoves = board.generateLegalMoves();

        if (legalMoves.isEmpty()) {
            if (board.isAntiChess()) {
                return CHECKMATE_SCORE - ply;
            }
            if (board.isRacingKings() && !board.isWhiteToMove() && RacingKings.kingOnEighth(board, true)) {
                return -CHECKMATE_SCORE + ply;
            }
            if (inCheck) {
                return -CHECKMATE_SCORE + ply;
            }
            if (board.isHorde() && HordeChess.whiteHasNoPieces(board)) {
                return -CHECKMATE_SCORE + ply;
            }
            return 0;
        }

        Move ttMove = (ttEntry != null) ? ttEntry.bestMove : null;
        int[] scores = scoreMoves(legalMoves, board, ttMove, ply);

        if (ply == 0) {
            rootAlphaThisSearch = alpha;
        }

        Move bestMoveLocal = null;
        int maxScore = -INFINITY;
        boolean lmrAllowed = !board.isAntiChess();
        boolean lmpOk = evalKnown && !pvNode && depth <= 4 && Math.abs(alpha) < CHECKMATE_SCORE - 200;
        int lmpLimit = depth <= 4 ? (improving ? LMP_IMPROVING[depth] : LMP_STEADY[depth]) : 999;
        Move[] triedQuiets = new Move[64];
        int nTried = 0;

        for (int i = 0; i < legalMoves.size(); i++) {
            pickBest(legalMoves, scores, i);
            Move move = legalMoves.get(i);

            boolean quiet = !move.isCapture() && !isPromotion(move);
            boolean seventh = ply < MAX_PLY && pawnToSeventh(board, move);
            boolean killer = ply < MAX_PLY && (move.equals(killerMoves[ply][0]) || move.equals(killerMoves[ply][1]));
            boolean reducible = lmrAllowed && quiet && !seventh && !inCheck && i >= 2 && searchDepth >= 3;
            boolean lateQuiet = lmpOk && quiet && !seventh && !killer && i >= lmpLimit && lmrAllowed;
            boolean prunableMove = (futilityOk && quiet && !seventh && i > 0) || lateQuiet;

            board.makeMove(move);

            boolean givesCheck = board.isInCheck(board.isWhiteToMove());

            if (prunableMove && !givesCheck) {
                board.undoMove(move);
                continue;
            }

            int newDepth = searchDepth - 1 + (seventh ? 1 : 0);
            int score;

            // Principal Variation Search (PVS) with LMR tuning
            if (i == 0) {
                score = -alphaBeta(board, newDepth, ply + 1, -beta, -alpha, true);
            } else {
                int reduction = 0;
                if (reducible && !givesCheck) {
                    reduction = LMR[Math.min(searchDepth, 63)][Math.min(i, 63)];
                    if (pvNode) reduction--;
                    if (improving) reduction--;
                    if (killer || history[side][move.from][move.to] > 3000) reduction--;
                    if (reduction < 0) reduction = 0;
                    if (newDepth - reduction < 1) reduction = Math.max(0, newDepth - 1);
                }

                score = -alphaBeta(board, newDepth - reduction, ply + 1, -alpha - 1, -alpha, true);
                if (score > alpha && reduction > 0) {
                    score = -alphaBeta(board, newDepth, ply + 1, -alpha - 1, -alpha, true);
                }
                if (score > alpha && score < beta) {
                    score = -alphaBeta(board, newDepth, ply + 1, -beta, -alpha, true);
                }
            }

            board.undoMove(move);

            if (timeUp) return 0;

            if (ply == 0 && i == 0 && rootIterationDepth >= 6 && panicExtensions < 2 && alpha > -INFINITY
                    && score <= alpha && rootBaseTime > 0) {
                maxTimeMillis += rootBaseTime / 3;
                panicExtensions++;
            }

            if (score > maxScore) {
                maxScore = score;
                bestMoveLocal = move;
                if (ply == 0) {
                    bestMoveThisRootSearch = move;
                    bestScoreThisRootSearch = score;
                }
            }

            if (quiet && nTried < 64) triedQuiets[nTried++] = move;

            alpha = Math.max(alpha, score);
            if (alpha >= beta) {
                if (quiet && ply < MAX_PLY) {
                    storeKillerMove(move, ply);
                    updateHistory(side, move, searchDepth);
                    for (int q = 0; q < nTried - 1; q++) {
                        Move bad = triedQuiets[q];
                        int hv = history[side][bad.from][bad.to] - searchDepth * searchDepth;
                        history[side][bad.from][bad.to] = Math.max(hv, -6000);
                    }
                }
                break;
            }
        }

        byte flag;
        if (maxScore <= originalAlpha) {
            flag = UPPERBOUND;
        } else if (maxScore >= beta) {
            flag = LOWERBOUND;
        } else {
            flag = EXACT;
        }

        int scoreToStore = storeTTScore(maxScore, ply);
        ttPut(new TTEntry(zobristKey, searchDepth, scoreToStore, flag, bestMoveLocal));

        return maxScore;
    }

    private int quiescenceSearch(Board board, int alpha, int beta, int ply) {
        if (checkTimeout()) return 0;
        if (ply >= PATH_SIZE - 2) return evaluate(board);
        nodes++;

        if (board.isAtomic()) {
            if (AtomicChess.hasLostKing(board, board.isWhiteToMove())) return -CHECKMATE_SCORE + ply;
            if (AtomicChess.hasLostKing(board, !board.isWhiteToMove())) return CHECKMATE_SCORE - ply;
        }

        if (board.isThreeCheck() && (board.getWhiteChecks() >= 3 || board.getBlackChecks() >= 3)) {
            return -CHECKMATE_SCORE + ply;
        }

        if (board.isKingOfTheHill()) {
            if (KingOfTheHill.hasKingOnHill(board, !board.isWhiteToMove())) return -CHECKMATE_SCORE + ply;
            if (KingOfTheHill.hasKingOnHill(board, board.isWhiteToMove())) return CHECKMATE_SCORE - ply;
        }

        if (board.isRacingKings()) {
            int outcome = RacingKings.outcome(board);
            if (outcome == 2) return 0;
            if (outcome != 0) {
                boolean sideToMoveWon = (outcome == 1) == board.isWhiteToMove();
                return sideToMoveWon ? CHECKMATE_SCORE - ply : -CHECKMATE_SCORE + ply;
            }
            return evaluate(board);
        }

        if (board.isAntiChess()) {
            if (AntiChess.hasNoPieces(board, board.isWhiteToMove())) return CHECKMATE_SCORE - ply;
            if (AntiChess.hasNoPieces(board, !board.isWhiteToMove())) return -CHECKMATE_SCORE + ply;
            return evaluate(board);
        }

        boolean inCheck = board.isInCheck(board.isWhiteToMove());
        boolean prune = !inCheck && standardLike(board);
        int standPat = 0;

        if (prune && pieceCount(board) <= 8 && board.generateLegalMoves().isEmpty()) {
            return drawScore(board, ply);
        }

        if (!inCheck) {
            standPat = evaluate(board);
            if (standPat >= beta) return beta;
            if (standPat > alpha) alpha = standPat;
        }

        // Search full moves when in check or early plies to ensure non-capture checks don't lead to outcalculation
        List<Move> moves = (inCheck || ply <= 2) ? board.generateLegalMoves() : board.generateCaptures();

        if (moves.isEmpty()) {
            if (inCheck) {
                return -CHECKMATE_SCORE + ply;
            }
            return alpha;
        }

        int[] scores = scoreMoves(moves, board, null, ply);

        for (int i = 0; i < moves.size(); i++) {
            pickBest(moves, scores, i);
            Move move = moves.get(i);

            if (prune && move.isCapture() && !isPromotion(move)) {
                int victim = getPieceValue(board.getPieceTypeAt(move.to));
                if (victim == 0 && move.enPassant) victim = 100;

                if (standPat + victim + 200 < alpha) continue;

                int attacker = getPieceValue(board.getPieceTypeAt(move.from));
                if (attacker > victim + 50 && see(board, move) < 0) {
                    continue;
                }
            }

            board.makeMove(move);
            int score = -quiescenceSearch(board, -beta, -alpha, ply + 1);
            board.undoMove(move);

            if (timeUp) return 0;

            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }

        return alpha;
    }

    private static int seeValue(char p) {
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

    private static final int[][] SEE_KNIGHT = {{2,1},{1,2},{-1,2},{-2,1},{-2,-1},{-1,-2},{1,-2},{2,-1}};
    private static final int[][] SEE_DIAG = {{1,1},{1,-1},{-1,1},{-1,-1}};
    private static final int[][] SEE_ORTH = {{1,0},{-1,0},{0,1},{0,-1}};

    private static int leastAttacker(char[] b, int to, boolean white) {
        int tr = to >> 3, tf = to & 7;
        char pawn = white ? 'P' : 'p';
        int pr = white ? tr - 1 : tr + 1;
        if (pr >= 0 && pr < 8) {
            if (tf > 0 && b[pr * 8 + tf - 1] == pawn) return pr * 8 + tf - 1;
            if (tf < 7 && b[pr * 8 + tf + 1] == pawn) return pr * 8 + tf + 1;
        }
        char knight = white ? 'N' : 'n';
        for (int[] v : SEE_KNIGHT) {
            int r = tr + v[0], f = tf + v[1];
            if (r >= 0 && r < 8 && f >= 0 && f < 8 && b[r * 8 + f] == knight) return r * 8 + f;
        }
        char bishop = white ? 'B' : 'b', rook = white ? 'R' : 'r', queen = white ? 'Q' : 'q', king = white ? 'K' : 'k';
        int bestQueen = -1;
        int bishopSq = -1, rookSq = -1;
        for (int[] v : SEE_DIAG) {
            int r = tr + v[0], f = tf + v[1];
            while (r >= 0 && r < 8 && f >= 0 && f < 8) {
                char c = b[r * 8 + f];
                if (c != '.') {
                    if (c == bishop && bishopSq < 0) bishopSq = r * 8 + f;
                    else if (c == queen && bestQueen < 0) bestQueen = r * 8 + f;
                    break;
                }
                r += v[0]; f += v[1];
            }
        }
        if (bishopSq >= 0) return bishopSq;
        for (int[] v : SEE_ORTH) {
            int r = tr + v[0], f = tf + v[1];
            while (r >= 0 && r < 8 && f >= 0 && f < 8) {
                char c = b[r * 8 + f];
                if (c != '.') {
                    if (c == rook && rookSq < 0) rookSq = r * 8 + f;
                    else if (c == queen && bestQueen < 0) bestQueen = r * 8 + f;
                    break;
                }
                r += v[0]; f += v[1];
            }
        }
        if (rookSq >= 0) return rookSq;
        if (bestQueen >= 0) return bestQueen;
        for (int dr = -1; dr <= 1; dr++) {
            for (int df = -1; df <= 1; df++) {
                if (dr == 0 && df == 0) continue;
                int r = tr + dr, f = tf + df;
                if (r >= 0 && r < 8 && f >= 0 && f < 8 && b[r * 8 + f] == king) return r * 8 + f;
            }
        }
        return -1;
    }

    private static int see(Board board, Move move) {
        char[] b = new char[64];
        for (int i = 0; i < 64; i++) b[i] = board.getPiece(i);
        int to = move.to;
        char mover = b[move.from];
        boolean white = Character.isUpperCase(mover);
        int[] gain = new int[34];
        int d = 0;
        int victim = seeValue(b[to]);
        if (move.enPassant) {
            victim = 100;
            b[white ? to - 8 : to + 8] = '.';
        }
        gain[0] = victim;
        int onSquare = seeValue(mover);
        b[move.from] = '.';
        b[to] = mover;
        boolean side = !white;
        while (d < 32) {
            int a = leastAttacker(b, to, side);
            if (a < 0) break;
            d++;
            gain[d] = onSquare - gain[d - 1];
            onSquare = seeValue(b[a]);
            b[to] = b[a];
            b[a] = '.';
            side = !side;
            if (Math.max(-gain[d - 1], gain[d]) < 0) break;
        }
        while (d > 0) {
            gain[d - 1] = -Math.max(-gain[d - 1], gain[d]);
            d--;
        }
        return gain[0];
    }

    private static int pieceCount(Board board) {
        int n = 0;
        for (int s = 0; s < 64; s++) {
            if (board.getPiece(s) != '.') n++;
        }
        return n;
    }

    private static boolean pawnToSeventh(Board board, Move move) {
        char pc = board.getPiece(move.from);
        if (pc == 'P') {
            int r = move.to >> 3;
            return r == 6 || (r == 5 && pieceCount(board) <= 8);
        }
        if (pc == 'p') {
            int r = move.to >> 3;
            return r == 1 || (r == 2 && pieceCount(board) <= 8);
        }
        return false;
    }

    private int[] scoreMoves(List<Move> moves, Board board, Move ttMove, int ply) {
        int side = board.isWhiteToMove() ? 0 : 1;
        int[] scores = new int[moves.size()];
        for (int i = 0; i < scores.length; i++) {
            scores[i] = getMovePriority(moves.get(i), board, ttMove, ply, side);
        }
        return scores;
    }

    private static void pickBest(List<Move> moves, int[] scores, int start) {
        int best = start;
        for (int j = start + 1; j < scores.length; j++) {
            if (scores[j] > scores[best]) best = j;
        }
        if (best != start) {
            int ts = scores[start];
            scores[start] = scores[best];
            scores[best] = ts;
            Move tm = moves.get(start);
            moves.set(start, moves.get(best));
            moves.set(best, tm);
        }
    }

    private int getMovePriority(Move move, Board board, Move ttMove, int ply, int side) {
        if (ttMove != null && move.equals(ttMove)) {
            return 100_000;
        }

        if (move.isCapture()) {
            int victimValue = getPieceValue(board.getPieceTypeAt(move.to));
            if (victimValue == 0 && move.enPassant) victimValue = 100;
            int attackerValue = getPieceValue(board.getPieceTypeAt(move.from));
            if (victimValue >= attackerValue || (standardLike(board) && see(board, move) >= 0)) {
                return 20_000 + (victimValue * 10 - attackerValue);
            }
            return 7_500 + victimValue / 10;
        }

        if (isPromotion(move)) {
            return 9_000;
        }

        if (ply < MAX_PLY) {
            if (move.equals(killerMoves[ply][0])) return 8_000;
            if (move.equals(killerMoves[ply][1])) return 7_000;
        }

        return history[side][move.from][move.to];
    }

    private boolean isPromotion(Move move) {
        char p = move.getPromotionPiece();
        return p != '\0' && p != '.' && p != ' ';
    }

    private void storeKillerMove(Move move, int ply) {
        if (!move.equals(killerMoves[ply][0])) {
            killerMoves[ply][1] = killerMoves[ply][0];
            killerMoves[ply][0] = move;
        }
    }

    private void updateHistory(int side, Move move, int depth) {
        int v = history[side][move.from][move.to] + depth * depth;
        history[side][move.from][move.to] = Math.min(v, 6000);
    }

    private void clearKillerMoves() {
        for (int i = 0; i < MAX_PLY; i++) {
            killerMoves[i][0] = null;
            killerMoves[i][1] = null;
        }
    }

    private void clearHistory() {
        for (int s = 0; s < 2; s++) {
            for (int f = 0; f < 64; f++) {
                Arrays.fill(history[s][f], 0);
            }
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

    private static final int EVAL_MASK = (1 << 20) - 1;
    private final long[] evalKeys = new long[EVAL_MASK + 1];
    private final int[] evalVals = new int[EVAL_MASK + 1];

    private void evalCacheClear() {
        Arrays.fill(evalKeys, 0L);
    }

    private int evaluate(Board board) {
        long key = board.getZobristKey();
        if (board.isThreeCheck()) {
            key ^= board.getWhiteChecks() * 0x9E3779B97F4A7C15L ^ board.getBlackChecks() * 0xC2B2AE3D27D4EB4FL;
        }
        int idx = (int) (key ^ (key >>> 32)) & EVAL_MASK;
        if (evalKeys[idx] == key) {
            return evalVals[idx];
        }
        int v = evaluateUncached(board);
        evalKeys[idx] = key;
        evalVals[idx] = v;
        return v;
    }

    private int evaluateUncached(Board board) {
        if (board.isAntiChess()) {
            return AntiChess.evaluate(board);
        }

        if (board.isRacingKings()) {
            int racing = RacingKings.evaluate(board);
            return board.isWhiteToMove() ? racing : -racing;
        }

        int perspective = board.isWhiteToMove() ? 1 : -1;
        int score = positionEval.evaluateWhite(board);

        if (board.isThreeCheck()) {
            score += (board.getWhiteChecks() - board.getBlackChecks()) * 80;
        }

        if (board.isKingOfTheHill()) {
            score += KingOfTheHill.kingProximityBonus(board);
        }

        return score * perspective;
    }

    public static final class AnalysisLine {
        public final int scoreCp;
        public final int mateIn;
        public final List<Move> pv;

        AnalysisLine(int scoreCp, int mateIn, List<Move> pv) {
            this.scoreCp = scoreCp;
            this.mateIn = mateIn;
            this.pv = pv;
        }
    }

    public interface AnalysisListener {
        void onDepthCompleted(int depth, List<AnalysisLine> lines);
    }

    private static final class Scored {
        final Move move;
        final int score;

        Scored(Move move, int score) {
            this.move = move;
            this.score = score;
        }
    }

    private volatile boolean cancelled = false;

    public void cancel() {
        cancelled = true;
        for (ChessEngine h : helpers) h.cancelled = true;
    }

    public void analyse(Board position, int numLines, int maxDepth, long timeLimitMillis, AnalysisListener listener) {
        List<Thread> helperThreads = new ArrayList<>();
        if (analysisThreads > 1 && timeLimitMillis >= 400) {
            prepareAnalysisTables(position);
            helperThreads = startHelpers(position, maxDepth, analysisThreads, -1, 0);
        }
        try {
            analyseInternal(position, numLines, maxDepth, timeLimitMillis, listener);
        } finally {
            stopHelpers(helperThreads);
        }
    }

    private void analyseInternal(Board position, int numLines, int maxDepth, long timeLimitMillis, AnalysisListener listener) {
        this.startTime = System.currentTimeMillis();
        this.maxTimeMillis = timeLimitMillis;
        this.timeUp = false;
        this.nodes = 0;

        clearKillerMoves();
        clearHistory();
        prepareAnalysisTables(position);
        gameHistory.clear();
        contempt = 0;
        positionEval.setEngineSide(-1);

        Board board = position.copy();
        List<Move> rootMoves = board.generateLegalMoves();
        if (rootMoves.isEmpty()) {
            return;
        }
        pathKeys[0] = board.getZobristKey();

        boolean whiteToMove = board.isWhiteToMove();
        Map<Move, Integer> previous = new HashMap<>();

        for (int depth = 1; depth <= maxDepth; depth++) {
            final Map<Move, Integer> order = previous;
            rootMoves.sort((a, b) -> Integer.compare(
                    order.getOrDefault(b, -INFINITY), order.getOrDefault(a, -INFINITY)));

            List<Scored> top = new ArrayList<>();
            Map<Move, Integer> current = new HashMap<>();
            boolean aborted = false;

            for (Move move : rootMoves) {
                int alpha = top.size() < numLines ? -INFINITY : top.get(top.size() - 1).score;

                board.makeMove(move);
                int score = -alphaBeta(board, depth - 1, 1, -INFINITY, -alpha, true);
                board.undoMove(move);

                if (timeUp) {
                    aborted = true;
                    break;
                }

                current.put(move, score);

                if (top.size() < numLines || score > alpha) {
                    top.add(new Scored(move, score));
                    top.sort((a, b) -> Integer.compare(b.score, a.score));
                    if (top.size() > numLines) {
                        top.remove(top.size() - 1);
                    }
                }
            }

            if (aborted) {
                return;
            }

            previous = current;

            List<AnalysisLine> lines = new ArrayList<>();
            for (Scored s : top) {
                lines.add(toAnalysisLine(s.score, whiteToMove, extractPrincipalVariation(board, s.move)));
            }
            listener.onDepthCompleted(depth, lines);

            if (Math.abs(top.get(0).score) > CHECKMATE_SCORE - 200) {
                return;
            }
        }
    }

    private AnalysisLine toAnalysisLine(int score, boolean whiteToMove, List<Move> pv) {
        int whiteScore = whiteToMove ? score : -score;

        if (Math.abs(score) > CHECKMATE_SCORE - 200) {
            int plies = CHECKMATE_SCORE - Math.abs(score);
            int moves = Math.max(1, (plies + 1) / 2);
            return new AnalysisLine(whiteScore, whiteScore > 0 ? moves : -moves, pv);
        }

        return new AnalysisLine(whiteScore, 0, pv);
    }

    private List<Move> extractPrincipalVariation(Board board, Move first) {
        List<Move> pv = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        board.makeMove(first);
        pv.add(first);

        for (int i = 0; i < 9; i++) {
            long key = board.getZobristKey();
            if (!seen.add(key)) {
                break;
            }

            TTEntry entry = ttGet(key);
            if (entry == null || entry.bestMove == null) {
                break;
            }

            Move next = null;
            for (Move candidate : board.generateLegalMoves()) {
                if (candidate.equals(entry.bestMove)) {
                    next = candidate;
                    break;
                }
            }
            if (next == null) {
                break;
            }

            board.makeMove(next);
            pv.add(next);
        }

        for (int i = pv.size() - 1; i >= 0; i--) {
            board.undoMove(pv.get(i));
        }

        return pv;
    }

    private boolean checkTimeout() {
        if (!timeUp && (cancelled || System.currentTimeMillis() - startTime >= maxTimeMillis)) {
            timeUp = true;
        }
        return timeUp;
    }

    private static final class PositionEval {

        private static final int P = 1, N = 2, B = 3, R = 4, Q = 5, K = 6;

        private static final int[] VALUE = {0, 100, 320, 330, 500, 900, 0};
        private static final int[] PHASE_W = {0, 0, 1, 1, 2, 4, 0};
        private static final int[] PASSED = {0, 5, 10, 20, 35, 60, 100, 0};
        private static final int[] ATTACK_W = {0, 0, 2, 2, 3, 5, 0};
        private static final int[] TROP = {0, 0, 2, 1, 1, 3, 0};

        private static final int AGGRESSION_PCT = 170;
        private static final int SELF_DEFENCE_PCT = 190;

        private static final int[][] KNIGHT = new int[64][];
        private static final int[][] KING = new int[64][];
        private static final int[][][] RAYS = new int[64][8][];
        private static final int[][][] PAWN_ATT = new int[2][64][];
        private static final boolean[] CENTER = new boolean[64];
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

        private int materialOnly; 
        private final int[] sq = new int[64];
        private final int[] kingSq = new int[2];
        private int side;
        private int castle;
        private int engineSide = -1;

        private final int[][] pawnCnt = new int[2][8];
        private final int[][] pawnMax = new int[2][8];
        private final int[][] pawnMin = new int[2][8];
        private final boolean[][] passedFile = new boolean[2][8];
        private final boolean[][] backwardOnFile = new boolean[2][8];
        private final boolean[][] zoneOf = new boolean[2][64];
        private final boolean[][] pawnAtt = new boolean[2][64];
        private final int[][] pawnOnColor = new int[2][2];
        private final int[] npMat = new int[2];
        private final int[] bishopCnt = new int[2];
        private final int[] knightCnt = new int[2];
        private final int[] lightB = new int[2];
        private final int[] kingAttackers = new int[2];
        private final int[] kingAttackUnits = new int[2];
        private final int[] kingExposure = new int[2];
        private final int[] stormPen = new int[2];
        private final int[] queenSq = new int[2];
        private final int[][] rookSq = new int[2][2];
        private final int[] rookN = new int[2];

        void setEngineSide(int engineSide) {
            this.engineSide = engineSide;
        }

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

            if (board.isHorde() && k0 == 0 && k1 == 1) {
                side = board.isWhiteToMove() ? 0 : 1;
                return hordeEvaluate();
            }

            if (k0 != 1 || k1 != 1) {
                return material;
            }

            side = board.isWhiteToMove() ? 0 : 1;

            int kpk = kpkScore(board);
            if (kpk != NO_KPK) {
                return kpk;
            }

            castle = (board.canWhiteCastleKingSide() ? 1 : 0)
                    | (board.canWhiteCastleQueenSide() ? 2 : 0)
                    | (board.canBlackCastleKingSide() ? 4 : 0)
                    | (board.canBlackCastleQueenSide() ? 8 : 0);

            return evaluate();
        }

        private static final int NO_KPK = Integer.MIN_VALUE;

        private int kpkScore(Board board) {
            if (board.isAtomic() || board.isAntiChess() || board.isRacingKings() || board.isKingOfTheHill()
                    || board.isThreeCheck() || board.isHorde()) {
                return NO_KPK;
            }

            int pawn = -1, pieces = 0;
            for (int s = 0; s < 64; s++) {
                int t = Math.abs(sq[s]);
                if (t == 0 || t == K) continue;
                if (t != P || ++pieces > 1) return NO_KPK;
                pawn = s;
            }
            if (pawn < 0) return NO_KPK;

            boolean whitePawn = sq[pawn] > 0;
            int att = whitePawn ? kingSq[0] : kingSq[1];
            int def = whitePawn ? kingSq[1] : kingSq[0];
            boolean attackerToMove = (side == 0) == whitePawn;

            if (!KpkBitbase.isWin(att, pawn, def, attackerToMove, whitePawn)) {
                return 0;
            }

            int rr = whitePawn ? (pawn >> 3) : 7 - (pawn >> 3);
            int win = 520 + 45 * rr;
            return whitePawn ? win : -win;
        }

        private static final int HORDE_PAWN = 107;

        private int hordeEvaluate() {
            int score = 0;

            for (int s = 0; s < 64; s++) {
                int p = sq[s];
                if (p == 0) continue;

                int t = Math.abs(p);
                int rank = s >> 3;

                if (p > 0) {
                    if (t == P) {
                        score += HORDE_PAWN + 3 * rank + (rank >= 5 ? (rank - 4) * 20 : 0);
                    } else {
                        score += VALUE[t] + PST[t][s ^ 56];
                    }
                } else if (t != K) {
                    score -= VALUE[t] + PST[t][s];
                }
            }

            score += side == 0 ? 10 : -10;
            return score;
        }

        private static int chebyshev(int a, int b) {
            return Math.max(Math.abs((a & 7) - (b & 7)), Math.abs((a >> 3) - (b >> 3)));
        }

        private static final int POS_KNEE = 80;     
        private static final int POS_SLOPE = 40;    

        private int evaluate() {
            int phase = 0;
            int score = 0;
            materialOnly = 0;

            for (int c = 0; c < 2; c++) {
                Arrays.fill(pawnCnt[c], 0);
                Arrays.fill(pawnMax[c], -1);
                Arrays.fill(pawnMin[c], 8);
                Arrays.fill(passedFile[c], false);
                Arrays.fill(backwardOnFile[c], false);
                Arrays.fill(zoneOf[c], false);
                Arrays.fill(pawnAtt[c], false);
                pawnOnColor[c][0] = pawnOnColor[c][1] = 0;
                npMat[c] = 0;
                bishopCnt[c] = 0;
                knightCnt[c] = 0;
                lightB[c] = 0;
                kingAttackers[c] = 0;
                kingAttackUnits[c] = 0;
                kingExposure[c] = 0;
                stormPen[c] = 0;
                queenSq[c] = -1;
                rookN[c] = 0;
            }

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
                    pawnOnColor[c][(f + r) & 1]++;
                    for (int tg : PAWN_ATT[c][s]) pawnAtt[c][tg] = true;
                } else if (t != K) {
                    npMat[c] += VALUE[t];
                    phase += PHASE_W[t];
                    if (t == B) {
                        bishopCnt[c]++;
                        if ((((s & 7) + (s >> 3)) & 1) == 1) lightB[c]++;
                    }
                    if (t == N) knightCnt[c]++;
                    if (t == Q) queenSq[c] = s;
                }

                if (t != K) {
                    int v = VALUE[t] + PST[t][c == 0 ? s ^ 56 : s];
                    score += c == 0 ? v : -v;
                    materialOnly += c == 0 ? VALUE[t] : -VALUE[t];
                }
            }
            if (phase > 24) phase = 24;

            for (int c = 0; c < 2; c++) {
                int k = kingSq[c];
                zoneOf[c][k] = true;
                for (int t : KING[k]) zoneOf[c][t] = true;

                int kf = k & 7;
                int en = 1 - c;
                int exposure = 0;
                int storm = 0;

                for (int ff = Math.max(0, kf - 1); ff <= Math.min(7, kf + 1); ff++) {
                    if (pawnCnt[c][ff] == 0) {
                        exposure += pawnCnt[en][ff] == 0 ? 2 : 1;
                    }
                    if (pawnCnt[en][ff] > 0) {
                        int adv = en == 0 ? pawnMax[0][ff] : 7 - pawnMin[1][ff];
                        if (adv >= 3) storm += (adv - 2) * (adv - 2) * 2;
                    }
                }
                kingExposure[c] = exposure;
                stormPen[c] = storm;
            }

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
                boolean isolated = !left && !right;
                if (isolated) pv -= 12;

                boolean passed = true;
                for (int ff = Math.max(0, f - 1); ff <= Math.min(7, f + 1); ff++) {
                    if (c == 0 ? pawnMax[1][ff] > r : pawnMin[0][ff] < r) {
                        passed = false;
                        break;
                    }
                }
                if (passed && (c == 0 ? pawnMax[0][f] > r : pawnMin[1][f] < r)) {
                    passed = false;
                }

                if (passed) {
                    passedFile[c][f] = true;
                    int b = PASSED[rr];
                    int passBonus = b + b * (24 - phase) / 24;

                    int stopSq = s + (c == 0 ? 8 : -8);
                    boolean blockadedByPiece = false;
                    if (stopSq >= 0 && stopSq < 64 && sq[stopSq] != 0 && (sq[stopSq] > 0) != (c == 0)
                            && Math.abs(sq[stopSq]) != K) {
                        blockadedByPiece = true;
                        if (rr == 6) passBonus = passBonus * 2 / 5;
                        else if (rr == 5) passBonus = passBonus * 3 / 4;
                    }
                    pv += passBonus;
                    if (stopSq >= 0 && stopSq < 64) {
                        if (sq[stopSq] != 0 && (sq[stopSq] > 0) != (c == 0)) pv -= b / 3;

                        if (phase < 16) {
                            int diff = chebyshev(kingSq[1 - c], stopSq) - chebyshev(kingSq[c], stopSq);
                            pv += diff * (2 + rr) * (24 - phase) / 24;
                        }
                    }

                    if (npMat[0] == npMat[1] && (npMat[0] == 500 || npMat[0] == 1000) && stopSq >= 0 && stopSq < 64) {
                        int dir = c == 0 ? 8 : -8;
                        if (kingSq[1 - c] == stopSq) {
                            pv -= 10 + 6 * rr;
                        } else {
                            int enemyRook = c == 0 ? -R : R;
                            for (int x = stopSq; x >= 0 && x < 64; x += dir) {
                                if (sq[x] == 0) continue;
                                if (sq[x] == enemyRook) pv += 12 + 3 * rr;
                                break;
                            }
                        }
                        if (Math.abs((kingSq[1 - c] & 7) - f) >= 2 && chebyshev(kingSq[c], stopSq) <= 2) {
                            pv += 20 + 8 * rr;
                        }
                    }

                    if (phase <= 12 && (f <= 1 || f >= 6)) {
                        boolean lowWing = f <= 3;
                        boolean enemyOnWing = false, ownOnOtherWing = false;
                        for (int ff = 0; ff < 8; ff++) {
                            boolean sameWing = (ff <= 3) == lowWing;
                            if (sameWing && pawnCnt[1 - c][ff] > 0) enemyOnWing = true;
                            if (!sameWing && pawnCnt[c][ff] > 0) ownOnOtherWing = true;
                        }
                        if (!enemyOnWing && ownOnOtherWing) {
                            pv += (18 + 4 * rr) * (24 - phase) / 24;
                        }
                    }

                    if (npMat[1 - c] == 0 && rr >= 1) {
                        int promo = (c == 0 ? 56 : 0) + f;
                        int steps = 7 - rr;
                        if (rr == 1) steps--;
                        int kd = chebyshev(kingSq[1 - c], promo);
                        boolean pawnToMove = side == c;
                        if (kd > steps - (pawnToMove ? 1 : 0)) {
                            pv += 450 + 15 * rr;
                        }
                    } else if (phase <= 12 && rr >= 4) {
                        pv += runawayPassedPawn(c, s, f, rr);
                    }
                }

                if (!isolated && !passed && rr >= 1 && rr <= 5) {
                    boolean supported = false;
                    for (int ff = f - 1; ff <= f + 1; ff += 2) {
                        if (ff < 0 || ff > 7 || pawnCnt[c][ff] == 0) continue;
                        if (c == 0 ? pawnMin[0][ff] <= r : pawnMax[1][ff] >= r) supported = true;
                    }
                    int stop = s + (c == 0 ? 8 : -8);
                    if (!supported && pawnAtt[1 - c][stop]) {
                        pv -= 8;
                        if (pawnCnt[1 - c][f] == 0) pv -= 6;
                        backwardOnFile[c][f] = true;
                    }
                }

                if (f < 7 && sq[s + 1] == p) pv += 6;

                for (int t : PAWN_ATT[1 - c][s]) {
                    if (sq[t] == p) pv += 5;
                }
                for (int t : PAWN_ATT[c][s]) {
                    if (CENTER[t]) pv += 5;
                }

                score += c == 0 ? pv : -pv;
            }

            for (int s = 0; s < 64; s++) {
                int p = sq[s];
                int t = Math.abs(p);
                if (t == 0 || t == P || t == K) continue;

                int c = p > 0 ? 0 : 1;
                int f = s & 7, r = s >> 3;
                int rr = c == 0 ? r : 7 - r;
                int pv = 0;
                int mob = 0, centerHits = 0;
                int zoneHits = 0;
                boolean[] enemyZone = zoneOf[1 - c];
                int sign = c == 0 ? 1 : -1;

                switch (t) {
                    case N: {
                        for (int tg : KNIGHT[s]) {
                            int tp = sq[tg] * sign;
                            if (tp <= 0 && !pawnAtt[1 - c][tg]) mob++;
                            if (CENTER[tg]) centerHits++;
                            if (enemyZone[tg]) zoneHits++;
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
                        break;
                    }
                    case B: {
                        for (int d = 4; d < 8; d++) {
                            for (int tg : RAYS[s][d]) {
                                int tp = sq[tg] * sign;
                                if (tp <= 0 && !pawnAtt[1 - c][tg]) mob++;
                                if (CENTER[tg]) centerHits++;
                                if (enemyZone[tg]) zoneHits++;
                                if (tp != 0) break;
                            }
                        }
                        pv += (mob - 6) * 3 + centerHits * 3;

                        boolean longDiag = s % 9 == 0 || (s % 7 == 0 && s != 0 && s != 63);
                        if (longDiag) pv += 10;
                        if (longDiag && zoneHits > 0) pv += 12;

                        int col = ((s & 7) + (s >> 3)) & 1;
                        pv -= pawnOnColor[c][col] * 2;

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
                        break;
                    }
                    case R: {
                        for (int d = 0; d < 4; d++) {
                            for (int tg : RAYS[s][d]) {
                                int tp = sq[tg] * sign;
                                if (tp <= 0) mob++;
                                if (enemyZone[tg]) zoneHits++;
                                if (tp != 0) break;
                            }
                        }
                        pv += (mob - 7) * 2;

                        if (pawnCnt[c][f] == 0) {
                            pv += pawnCnt[1 - c][f] == 0 ? 20 : 10;
                            if (backwardOnFile[1 - c][f]) pv += 12;
                            if (Math.abs(f - (kingSq[1 - c] & 7)) <= 1) pv += 14 * phase / 24;
                        } else {
                            boolean pawnAhead = c == 0 ? pawnMax[0][f] > r : pawnMin[1][f] < r;
                            if (pawnAhead) {
                                pv += 6;
                                if (passedFile[c][f]) pv += 15;
                            }
                        }
                        if (rr == 6) pv += 15;

                        if (rookN[c] < 2) rookSq[c][rookN[c]++] = s;

                        for (int owner = 0; owner < 2; owner++) {
                            if (!passedFile[owner][f]) continue;
                            int pawnRankAbs = owner == 0 ? pawnMax[0][f] : pawnMin[1][f];
                            boolean behind = owner == 0 ? r < pawnRankAbs : r > pawnRankAbs;
                            if (behind) pv += owner == c ? 14 : 8;
                        }

                        if (pawnCnt[c][f] == 0) {
                            for (int dir = -8; dir <= 8; dir += 16) {
                                for (int x = s + dir; x >= 0 && x < 64; x += dir) {
                                    int tp = sq[x] * sign;
                                    if (tp == 0) continue;
                                    if (tp == -R) {
                                        pv += 8;
                                    } else if (tp < 0 && (tp == -N || tp == -B)) {
                                        boolean onOurSide = c == 0 ? (x >> 3) <= 2 : (x >> 3) >= 5;
                                        if (onOurSide && pawnCnt[1 - c][f] == 0) pv += 12;
                                    }
                                    break;
                                }
                            }
                        }

                        if (f >= 2 && f <= 5) pv += 4;

                        int rk = kingSq[c];
                        boolean kingCastled = (rk >> 3) == (c == 0 ? 0 : 7) && ((rk & 7) == 6 || (rk & 7) == 2);
                        if (kingCastled && rr == 0 && Math.abs((rk & 7) - f) <= 2) pv += 10;
                        break;
                    }
                    case Q: {
                        for (int d = 0; d < 8; d++) {
                            for (int tg : RAYS[s][d]) {
                                int tp = sq[tg] * sign;
                                if (tp <= 0) mob++;
                                if (enemyZone[tg]) zoneHits++;
                                if (tp != 0) break;
                            }
                        }
                        pv += (Math.min(mob, 16) - 8) / 2;

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
                        break;
                    }
                    default:
                        break;
                }

                if (zoneHits > 0) {
                    kingAttackers[c]++;
                    kingAttackUnits[c] += ATTACK_W[t] * zoneHits;
                }

                pv += (7 - chebyshev(s, kingSq[1 - c])) * TROP[t] * phase / 24;

                score += c == 0 ? pv : -pv;
            }

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

                kv -= kingExposure[c] * 5 * phase / 24;
                kv -= stormPen[c] * phase / 24;

                int rights = c == 0 ? (castle & 3) : ((castle >> 2) & 3);
                if (rr == 0 && (kf == 6 || kf == 2)) {
                    kv += 25 * phase / 24;
                } else if (rights == 0 && rr <= 3 && kf >= 2 && kf <= 5) {
                    kv -= 20 * phase / 24;
                }

                score += c == 0 ? kv : -kv;
            }

            for (int c = 0; c < 2; c++) {
                int pv = 0;

                if (bishopCnt[c] >= 2) pv += 30;

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

                if (kingAttackers[1 - c] >= 2 && queenSq[c] >= 0 && chebyshev(queenSq[c], kingSq[c]) > 5) {
                    pv -= 10;
                }

                int units = kingAttackUnits[c];
                int attackers = kingAttackers[c];
                if (attackers >= 2 || (attackers == 1 && queenSq[c] >= 0 && units >= 8)) {
                    int danger = units * units / 4;
                    if (queenSq[c] < 0) danger /= 2;
                    danger = Math.min(danger, 600);
                    danger = danger * (8 + kingExposure[1 - c]) / 8;
                    danger = danger * phase / 24;

                    if (engineSide >= 0) {
                        if (c == engineSide) danger = danger * AGGRESSION_PCT / 100;
                        else danger = danger * SELF_DEFENCE_PCT / 100;
                    }
                    pv += danger;
                }

                if (kingAttackers[1 - c] >= 2 && queenSq[c] >= 0 && chebyshev(queenSq[c], kingSq[c]) <= 3) {
                    pv += 15;
                }

                score += c == 0 ? pv : -pv;
            }

            if (phase < 10) {
                for (int c = 0; c < 2; c++) {
                    int opponent = 1 - c;
                    if (rookN[c] == 2 && queenSq[c] < 0 && queenSq[opponent] >= 0) {
                        score += (c == 0 ? 1 : -1) * 65;
                    }
                }
            }

            for (int c = 0; c < 2; c++) {
                int opponent = 1 - c;
                if (rookN[c] == 1 && knightCnt[c] == 1 && bishopCnt[opponent] == 1 && phase < 12) {
                    score += (c == 0 ? 1 : -1) * 40;
                }
            }

            for (int c = 0; c < 2; c++) {
                if (phase < 8) {
                    int kSq = kingSq[c];
                    int kf = kSq & 7, kr = kSq >> 3;
                    double centerDistance = Math.abs(kf - 3.5) + Math.abs(kr - 3.5);
                    int centralizationBonus = (int)((7.0 - centerDistance) * 12);
                    score += (c == 0 ? 1 : -1) * centralizationBonus;
                }
            }

            int pawns0 = 0, pawns1 = 0;
            for (int f = 0; f < 8; f++) {
                pawns0 += pawnCnt[0][f];
                pawns1 += pawnCnt[1][f];
            }
            int lead = (npMat[0] + 100 * pawns0) - (npMat[1] + 100 * pawns1);
            if (phase <= 14 && Math.abs(lead) >= 150) {
                int winningSide = lead > 0 ? 0 : 1;
                int losingSide = 1 - winningSide;

                int losingKing = kingSq[losingSide];
                int winningKing = kingSq[winningSide];

                int lkf = losingKing & 7, lkr = losingKing >> 3;
                int wkf = winningKing & 7, wkr = winningKing >> 3;

                int centerDistance = Math.max(3 - lkf, lkf - 4) + Math.max(3 - lkr, lkr - 4);
                int kingDistance = Math.abs(lkf - wkf) + Math.abs(lkr - wkr);

                int mopUpScore = (centerDistance * 12) + ((14 - kingDistance) * 6);
                score += (winningSide == 0 ? mopUpScore : -mopUpScore);
            }

            if (pawns0 == 0 && pawns1 == 0) {
                int d = npMat[0] - npMat[1];
                int strong = d > 0 ? 0 : 1;
                int weak = 1 - strong;

                if (npMat[weak] == 0 && (npMat[strong] == 0 || npMat[strong] == 320 || npMat[strong] == 330
                        || (knightCnt[strong] == 2 && npMat[strong] == 640 && bishopCnt[strong] == 0))) {
                    return side == 0 ? 10 : -10;
                }

                boolean drawish = Math.abs(d) <= 340;
                if (!drawish && npMat[weak] == 0 && knightCnt[strong] == 2 && npMat[strong] == 640) {
                    drawish = true;
                }
                if (drawish) score /= 8;
            } else if (bishopCnt[0] == 1 && bishopCnt[1] == 1 && npMat[0] == 330 && npMat[1] == 330
                    && lightB[0] != lightB[1]) {
                int pd = Math.abs(pawns0 - pawns1);
                int num = pd == 0 ? 8 : pd == 1 ? 6 : pd == 2 ? 10 : 13;
                score = score * num / 16;
            } else if (bishopCnt[0] == 1 && bishopCnt[1] == 1 && rookN[0] == 1 && rookN[1] == 1
                    && npMat[0] == 830 && npMat[1] == 830 && lightB[0] != lightB[1]) {
                int pd = Math.abs(pawns0 - pawns1);
                score = score * (pd <= 1 ? 12 : 14) / 16;
            }

            for (int st = 0; st < 2; st++) {
                int wkc = 1 - st;
                int stPawns = st == 0 ? pawns0 : pawns1;
                int wkPawns = st == 0 ? pawns1 : pawns0;
                if (npMat[st] == 330 && bishopCnt[st] == 1 && npMat[wkc] == 0 && wkPawns == 0 && stPawns > 0) {
                    int rookFile = pawnCnt[st][0] == stPawns ? 0 : (pawnCnt[st][7] == stPawns ? 7 : -1);
                    if (rookFile >= 0) {
                        int corner = (st == 0 ? 56 : 0) + rookFile;
                        boolean cornerLight = (((corner & 7) + (corner >> 3)) & 1) == 1;
                        boolean bishopLight = lightB[st] == 1;
                        if (bishopLight != cornerLight) {
                            int d = chebyshev(kingSq[wkc], corner);
                            if (d <= 1) {
                                score = 0;
                            } else if (d <= 3) {
                                score = score * 3 / 16;
                            }
                        }
                    }
                }
            }

            {
                int pos = score - materialOnly;
                int ap = Math.abs(pos);
                if (ap > POS_KNEE) {
                    int capped = POS_KNEE + (ap - POS_KNEE) * POS_SLOPE / 100;
                    score = materialOnly + (pos < 0 ? -capped : capped);
                }
            }

            if (queenSq[0] < 0 && queenSq[1] < 0 && phase <= 12 && npMat[0] == npMat[1]
                    && Math.abs(materialOnly) >= 60 && Math.abs(materialOnly) <= 140) {
                int strong = materialOnly > 0 ? 0 : 1;
                boolean strongPassed = false;
                for (int f = 0; f < 8; f++) strongPassed |= passedFile[strong][f];
                if (!strongPassed) {
                    int pos = score - materialOnly;
                    int matPart = npMat[0] > 500 ? materialOnly * 65 / 100 : materialOnly;
                    score = matPart + pos * 40 / 100;
                }
            }

            score = queenEndgamePatterns(score, pawns0, pawns1);
            score = bishopKnightPatterns(score, phase, pawns0, pawns1);

            if (score != 0 && pawns0 + pawns1 > 0 && npMat[0] == 500 && npMat[1] == 500) {
                int strongC = score > 0 ? 0 : 1;
                int weakC = 1 - strongC;
                int filesS = 0, filesW = 0, bestRank = 0;
                boolean low = false, high = false;
                for (int f = 0; f < 8; f++) {
                    if (pawnCnt[strongC][f] > 0) {
                        filesS++;
                        int rk = strongC == 0 ? pawnMax[0][f] : 7 - pawnMin[1][f];
                        if (rk > bestRank) bestRank = rk;
                    }
                    if (pawnCnt[weakC][f] > 0) filesW++;
                    if (pawnCnt[0][f] + pawnCnt[1][f] > 0) {
                        if (f <= 3) low = true; else high = true;
                    }
                }
                boolean singleWing = !(low && high);

                boolean weakKingCutOff = false;
                {
                    int wkf = kingSq[weakC] & 7;
                    int minD = 8;
                    for (int f = 0; f < 8; f++) {
                        if (pawnCnt[weakC][f] > 0) minD = Math.min(minD, Math.abs(f - wkf));
                    }
                    weakKingCutOff = minD >= 3;
                }
                boolean farAdvanced = filesW == 0 && bestRank >= 5;
                boolean onePawnVsNone = pawnTotal(strongC) == 1 && pawnTotal(weakC) == 0;
                if (onePawnVsNone) {
                    int pf = 0;
                    for (int f = 0; f < 8; f++) if (pawnCnt[strongC][f] > 0) pf = f;
                    int promoSq = (strongC == 0 ? 56 : 0) + pf;
                    int dk = kingSq[weakC];
                    int dToPromo = chebyshev(dk, promoSq);
                    boolean inFront = Math.abs((dk & 7) - pf) <= 1 && (strongC == 0 ? (dk >> 3) > bestRank : (7 - (dk >> 3)) > bestRank);
                    int defRook = rookN[weakC] >= 1 ? rookSq[weakC][0] : -1;
                    int defRookRel = defRook < 0 ? -1 : (strongC == 0 ? (defRook >> 3) : 7 - (defRook >> 3));
                    int defKingRel = strongC == 0 ? (dk >> 3) : 7 - (dk >> 3);
                    boolean philidor = defRookRel == 5 && bestRank <= 4 && defKingRel >= 6 && Math.abs((dk & 7) - pf) <= 1;
                    boolean lucena = bestRank == 6 && pf != 0 && pf != 7 && chebyshev(kingSq[strongC], promoSq) <= 1
                            && Math.abs((dk & 7) - pf) >= 2;
                    if (philidor) {
                        score = score * 3 / 16;
                    } else if (lucena) {
                        score += strongC == 0 ? 110 : -110;
                    } else if (inFront && dToPromo <= 1) {
                        score = score * 3 / 16;
                    } else if (inFront && dToPromo <= 2) {
                        score = score * 5 / 16;
                    } else if ((pf == 0 || pf == 7) && dToPromo <= 3) {
                        score = score * 5 / 16;
                    } else if (Math.abs((dk & 7) - pf) >= 2) {
                        score += strongC == 0 ? 50 + 10 * bestRank : -(50 + 10 * bestRank);
                    } else {
                        score = score * 11 / 16;
                    }
                } else if (singleWing && filesS - filesW == 1 && !farAdvanced && !weakKingCutOff) {
                    score = score * 9 / 16;
                } else if (filesS - filesW == 0 && pawnTotal(strongC) > pawnTotal(weakC) && singleWing) {
                    score = score * 11 / 16;
                }
            }

            score = flearEndgamePatterns(score, phase, pawns0, pawns1);
            score = minorAgainstPawns(score);

            score += side == 0 ? 10 : -10;
            return score;
        }

        private static int promotionSquare(int c, int f) {
            return (c == 0 ? 56 : 0) + f;
        }

        private int lonePawnFile(int c) {
            int file = -1;
            for (int f = 0; f < 8; f++) {
                if (pawnCnt[c][f] == 0) continue;
                if (pawnCnt[c][f] > 1 || file >= 0) return -1;
                file = f;
            }
            return file;
        }

        private static boolean bishopOrRookFile(int f) {
            return f == 0 || f == 2 || f == 5 || f == 7;
        }

        private int queenEndgamePatterns(int score, int pw0, int pw1) {
            if (queenSq[0] < 0 && queenSq[1] < 0) {
                return score;
            }

            if (npMat[0] == 900 && npMat[1] == 900 && queenSq[0] >= 0 && queenSq[1] >= 0) {
                return queenAgainstQueen(score, pw0, pw1);
            }

            for (int st = 0; st < 2; st++) {
                int wk = 1 - st;
                if (npMat[st] != 900 || queenSq[st] < 0 || queenSq[wk] >= 0) continue;

                int sign = st == 0 ? 1 : -1;
                int stP = st == 0 ? pw0 : pw1;
                int wkP = st == 0 ? pw1 : pw0;
                int strong = score * sign;

                if (npMat[wk] == 0) {
                    strong = queenAgainstPawns(st, wk, stP, wkP, strong);
                } else if (npMat[wk] == 500 && rookN[wk] == 1) {
                    strong = queenAgainstRook(st, wk, stP, wkP, strong);
                } else if ((npMat[wk] == 320 || npMat[wk] == 330) && knightCnt[wk] + bishopCnt[wk] == 1) {
                    strong = queenAgainstMinorPiece(st, wk, stP, wkP, strong);
                }
                return strong * sign;
            }

            return score;
        }

        private int queenAgainstPawns(int st, int wk, int stP, int wkP, int strong) {
            if (wkP == 0) {
                return strong;
            }

            if (wkP == 1) {
                int f = lonePawnFile(wk);
                int rr = pawnRank(wk, f);
                int promo = promotionSquare(wk, f);
                int pawnKing = chebyshev(kingSq[wk], promo);
                int queenKing = chebyshev(kingSq[st], promo);

                if (rr >= 5 && stP == 0) {
                    boolean rookPawn = f == 0 || f == 7;
                    boolean bishopPawn = f == 2 || f == 5;

                    if (rr == 6 && (rookPawn || bishopPawn)) {
                        int needed = rookPawn ? 4 : 5;
                        if (pawnKing <= 1 && queenKing >= needed) {
                            return strong / 16;
                        }
                        if (pawnKing <= 1 && queenKing == needed - 1) {
                            return strong / 2;
                        }
                    } else if (rr == 5 && (rookPawn || bishopPawn) && pawnKing <= 2 && queenKing >= 5) {
                        return strong * 10 / 16;
                    } else if (rr == 6 && !rookPawn && !bishopPawn && pawnKing <= 1 && queenKing >= 6) {
                        return strong * 12 / 16;
                    }
                }
                return strong;
            }

            if (wkP == 2 && stP == 0) {
                int f1 = -1, f2 = -1;
                for (int f = 0; f < 8; f++) {
                    if (pawnCnt[wk][f] == 0) continue;
                    if (f1 < 0) f1 = f; else f2 = f;
                }
                if (f2 > 0 && f2 - f1 == 1) {
                    int r1 = pawnRank(wk, f1), r2 = pawnRank(wk, f2);
                    int low = Math.min(r1, r2);
                    int ownKing = Math.min(chebyshev(kingSq[wk], promotionSquare(wk, f1)),
                            chebyshev(kingSq[wk], promotionSquare(wk, f2)));
                    int queenKing = Math.min(chebyshev(kingSq[st], promotionSquare(wk, f1)),
                            chebyshev(kingSq[st], promotionSquare(wk, f2)));
                    if (low >= 6 && ownKing <= 2 && queenKing >= 3) {
                        return -250;
                    }
                    if (low >= 5 && ownKing <= 2) {
                        return strong / 4;
                    }
                    if (low >= 4 && ownKing <= 2 && queenKing >= 4) {
                        return strong * 12 / 16;
                    }
                }
                return strong;
            }

            if (wkP >= 3 && stP == 0) {
                int best = 0;
                for (int f = 0; f < 8; f++) {
                    if (pawnCnt[wk][f] > 0) best = Math.max(best, pawnRank(wk, f));
                }
                if (best >= 5) {
                    return strong * 12 / 16;
                }
            }
            return strong;
        }

        private int queenAgainstRook(int st, int wk, int stP, int wkP, int strong) {
            int rook = rookSq[wk][0];
            int defKing = kingSq[wk];

            if (wkP == 0 && stP == 0) {
                boolean rookOnEdge = (rook & 7) == 0 || (rook & 7) == 7 || (rook >> 3) == 0 || (rook >> 3) == 7;
                boolean kingOnEdge = (defKing & 7) == 0 || (defKing & 7) == 7 || (defKing >> 3) == 0 || (defKing >> 3) == 7;
                if (rookOnEdge && kingOnEdge && chebyshev(rook, defKing) <= 1) {
                    return strong * (chebyshev(kingSq[st], defKing) >= 4 ? 10 : 13) / 16;
                }
                return strong;
            }

            if (stP == 0 && wkP >= 1) {
                if (wkP == 1) {
                    int f = lonePawnFile(wk);
                    int rr = pawnRank(wk, f);
                    int promo = promotionSquare(wk, f);
                    boolean kingSupports = chebyshev(defKing, promo) <= 2;
                    boolean rookHelps = chebyshev(rook, promo) <= 3 || (rook & 7) == f;
                    if (rr >= 5 && kingSupports && rookHelps) {
                        return strong * ((f == 0 || f == 7) ? 8 : 10) / 16;
                    }
                    if (rr == 6 && chebyshev(kingSq[st], promo) >= 4 && rookHelps) {
                        return strong * 12 / 16;
                    }
                    return strong;
                }

                if (wkP == 2) {
                    int f1 = -1, f2 = -1;
                    for (int f = 0; f < 8; f++) {
                        if (pawnCnt[wk][f] == 0) continue;
                        if (f1 < 0) f1 = f; else f2 = f;
                    }
                    if (f2 > 0 && f2 - f1 == 1) {
                        int low = Math.min(pawnRank(wk, f1), pawnRank(wk, f2));
                        if (low >= 4 && chebyshev(defKing, promotionSquare(wk, f1)) <= 3) {
                            return strong * 7 / 16;
                        }
                    }
                    return strong * 13 / 16;
                }

                return strong * 12 / 16;
            }

            return strong;
        }

        private int queenAgainstMinorPiece(int st, int wk, int stP, int wkP, int strong) {
            if (wkP == 0 || stP > 0) {
                return strong;
            }

            int minor = -1;
            for (int s = 0; s < 64; s++) {
                int t = Math.abs(sq[s]);
                if ((t == N || t == B) && (sq[s] > 0) == (wk == 0)) minor = s;
            }
            if (minor < 0) {
                return strong;
            }
            boolean bishop = Math.abs(sq[minor]) == B;

            if (wkP == 1) {
                int f = lonePawnFile(wk);
                int rr = pawnRank(wk, f);
                int promo = promotionSquare(wk, f);
                boolean kingNear = chebyshev(kingSq[wk], promo) <= 2;
                boolean pieceNear = chebyshev(minor, promo) <= 2;

                if (rr >= 5 && kingNear && pieceNear) {
                    boolean rookPawn = f == 0 || f == 7;
                    boolean cornerColour = bishop && ((((promo & 7) + (promo >> 3)) & 1) == 1) == (lightB[wk] == 1);
                    if (rookPawn && (!bishop || cornerColour)) {
                        return strong * 4 / 16;
                    }
                    return strong * 8 / 16;
                }
                if (rr >= 4 && kingNear) {
                    return strong * 13 / 16;
                }
                return strong;
            }

            if (wkP == 2) {
                int f1 = -1, f2 = -1;
                for (int f = 0; f < 8; f++) {
                    if (pawnCnt[wk][f] == 0) continue;
                    if (f1 < 0) f1 = f; else f2 = f;
                }
                if (f2 > 0 && f2 - f1 == 1 && Math.min(pawnRank(wk, f1), pawnRank(wk, f2)) >= 4
                        && chebyshev(kingSq[wk], promotionSquare(wk, f1)) <= 3) {
                    return strong * 5 / 16;
                }
                return strong * 12 / 16;
            }

            return strong * 13 / 16;
        }

        private int queenAgainstQueen(int score, int pw0, int pw1) {
            score += (kingExposure[1] - kingExposure[0]) * 6;
            for (int c = 0; c < 2; c++) {
                int q = queenSq[c];
                int d = Math.max(3 - (q & 7), (q & 7) - 4) + Math.max(3 - (q >> 3), (q >> 3) - 4);
                int v = (6 - d) * 3;
                score += c == 0 ? v : -v;
            }

            int total = pw0 + pw1;
            if (total == 0) {
                return score;
            }

            int strongC = score > 0 ? 0 : 1;
            if (score == 0) {
                return score;
            }
            int weakC = 1 - strongC;
            int sign = strongC == 0 ? 1 : -1;
            int ps = strongC == 0 ? pw0 : pw1;
            int pwk = strongC == 0 ? pw1 : pw0;
            int strong = score * sign;

            int pf = -1, bestR = -1;
            for (int f = 0; f < 8; f++) {
                if (passedFile[strongC][f] && pawnRank(strongC, f) > bestR) {
                    bestR = pawnRank(strongC, f);
                    pf = f;
                }
            }

            if (ps - pwk == 1 && total <= 9) {
                if (pf >= 0) {
                    int promo = promotionSquare(strongC, pf);
                    int defKing = chebyshev(kingSq[weakC], promo);
                    int ownKing = chebyshev(kingSq[strongC], promo);
                    int stopSq = promo + (strongC == 0 ? -8 * (6 - bestR) : 8 * (6 - bestR));
                    boolean cutOff = Math.abs((kingSq[weakC] & 7) - pf) >= 2;

                    if (pf == 0 || pf == 7) {
                        if (defKing <= 2) strong = strong / 6;
                        else if (defKing == 3) strong = strong * 9 / 16;
                        else strong = strong * 12 / 16;
                    } else if (pf == 1 || pf == 6) {
                        if (defKing <= 2) strong = strong * 6 / 16;
                        else if (defKing == 3) strong = strong * 11 / 16;
                    } else if (pf == 2 || pf == 5) {
                        if (defKing <= 1 && bestR <= 5) strong = strong * 7 / 16;
                        else if (defKing <= 2) strong = strong * 11 / 16;
                    } else {
                        if (defKing <= 1 && bestR <= 4 && ownKing >= 3) strong = strong * 11 / 16;
                    }

                    if (bestR >= 5 && ownKing <= 1 && cutOff && stopSq >= 0 && stopSq < 64) {
                        strong += 90;
                    }
                    if (kingExposure[strongC] >= 3) {
                        strong = strong * 13 / 16;
                    }
                } else {
                    strong = strong * 12 / 16;
                }
            } else if (ps == pwk) {
                boolean anyPassed = false;
                for (int f = 0; f < 8; f++) {
                    if (passedFile[0][f] || passedFile[1][f]) anyPassed = true;
                }
                if (!anyPassed) strong = strong * 13 / 16;
            }

            return strong * sign;
        }

        private static boolean lightSquare(int s) {
            return (((s & 7) + (s >> 3)) & 1) == 1;
        }

        private int bishopKnightPatterns(int score, int phase, int pw0, int pw1) {
            int bs = -1;
            for (int c = 0; c < 2; c++) {
                if (bishopCnt[c] == 1 && knightCnt[c] == 0 && bishopCnt[1 - c] == 0 && knightCnt[1 - c] == 1) bs = c;
            }
            if (bs < 0 || phase > 18 || pw0 + pw1 == 0) {
                return score;
            }
            int ns = 1 - bs;
            if (npMat[bs] - 330 != npMat[ns] - 320 || (queenSq[0] >= 0) != (queenSq[1] >= 0)) {
                return score;
            }

            int bSq = -1, nSq = -1;
            for (int s = 0; s < 64; s++) {
                if (sq[s] == (bs == 0 ? B : -B)) bSq = s;
                else if (sq[s] == (ns == 0 ? N : -N)) nSq = s;
            }
            if (bSq < 0 || nSq < 0) {
                return score;
            }

            boolean bishopLight = lightSquare(bSq);
            int total = pw0 + pw1;
            int val = 0;

            int blocked = 0, blockedCentral = 0, targets = 0, fixedOnBishopColour = 0;
            boolean low = false, high = false;
            for (int s = 0; s < 64; s++) {
                if (Math.abs(sq[s]) != P) continue;
                int f = s & 7;
                if (f <= 3) low = true; else high = true;
                boolean white = sq[s] > 0;
                int ahead = s + (white ? 8 : -8);
                boolean headToHead = ahead >= 0 && ahead < 64 && sq[ahead] == -sq[s];
                if (white && headToHead) {
                    blocked++;
                    if (f >= 2 && f <= 5) blockedCentral++;
                }
                if (headToHead && lightSquare(s) == bishopLight) {
                    if ((sq[s] > 0) == (bs == 0)) fixedOnBishopColour++;
                    else targets++;
                }
                if ((sq[s] > 0) == (ns == 0) && lightSquare(s) == bishopLight && !headToHead) {
                    boolean protectedByPawn = false;
                    for (int t : PAWN_ATT[1 - ns][s]) {
                        if (sq[t] == (ns == 0 ? P : -P)) protectedByPawn = true;
                    }
                    if (!protectedByPawn) targets++;
                }
            }
            int[] opennessTable = {14, 8, 2, -4, -10};
            val += opennessTable[Math.min(blocked, 4)] - 3 * Math.min(blockedCentral, 3);
            val += (low && high) ? 14 : -6;
            val -= 6 * Math.min(fixedOnBishopColour, 3);
            val += 5 * Math.min(targets, 3);

            int nf = nSq & 7, nr = nSq >> 3;
            int nrr = ns == 0 ? nr : 7 - nr;
            if (nrr >= 3 && nrr <= 5) {
                boolean attackable = false;
                for (int ff = nf - 1; ff <= nf + 1; ff += 2) {
                    if (ff < 0 || ff > 7) continue;
                    if (ns == 0 ? pawnMax[1][ff] > nr : pawnMin[0][ff] < nr) attackable = true;
                }
                if (!attackable) {
                    boolean supported = false;
                    for (int t : PAWN_ATT[1 - ns][nSq]) {
                        if (sq[t] == (ns == 0 ? P : -P)) supported = true;
                    }
                    boolean oppositeColour = lightSquare(nSq) != bishopLight;
                    val -= supported ? 18 : 8;
                    if (oppositeColour) val -= supported ? 10 : 4;
                    if (nrr >= 4 && chebyshev(nSq, kingSq[bs]) <= 3) val -= 6;
                }
            }

            int knightMoves = 0;
            for (int tg : KNIGHT[nSq]) {
                int tp = sq[tg] * (ns == 0 ? 1 : -1);
                if (tp <= 0 && !pawnAtt[bs][tg]) knightMoves++;
            }
            if (knightMoves <= 1) val += 18;
            else if (knightMoves == 2) val += 10;

            int bishopMoves = 0;
            for (int d = 4; d < 8; d++) {
                for (int tg : RAYS[bSq][d]) {
                    int tp = sq[tg] * (bs == 0 ? 1 : -1);
                    if (tp <= 0 && !pawnAtt[ns][tg]) bishopMoves++;
                    if (tp != 0) break;
                }
            }
            if (bishopMoves <= 3) val -= 12;
            else if (bishopMoves <= 5) val -= 5;
            else if (bishopMoves >= 9) val += 5;

            boolean bishopPassed = false, knightPassed = false;
            for (int f = 0; f < 8; f++) {
                if (passedFile[bs][f]) bishopPassed = true;
                if (passedFile[ns][f]) knightPassed = true;
            }
            if (bishopPassed && knightPassed) val += 12;
            for (int s = 0; s < 64; s++) {
                if (sq[s] != (bs == 0 ? P : -P) || !passedFile[bs][s & 7]) continue;
                if (s + (bs == 0 ? 8 : -8) == nSq) val -= 10;
            }

            if (rookN[0] + rookN[1] > 0) {
                int openFiles = 0;
                for (int f = 0; f < 8; f++) {
                    if (pawnCnt[0][f] + pawnCnt[1][f] == 0) openFiles++;
                }
                val += Math.min(openFiles, 4) * 3;
            }
            if (queenSq[0] >= 0 && queenSq[1] >= 0) {
                if (kingExposure[ns] >= 2) val += 8;
                if (nrr >= 4 && chebyshev(nSq, kingSq[bs]) <= 3) val -= 8;
            }

            val = Math.max(-60, Math.min(60, val));
            return score + (bs == 0 ? val : -val);
        }

        private int minorAgainstPawns(int score) {
            for (int ps = 0; ps < 2; ps++) {
                int pw = 1 - ps;
                if ((npMat[ps] != 320 && npMat[ps] != 330) || npMat[pw] != 0) continue;
                if (queenSq[0] >= 0 || queenSq[1] >= 0 || rookN[0] + rookN[1] > 0) continue;
                int pa = pawnTotal(ps), pb = pawnTotal(pw);
                if (pb <= pa || pb == 0) continue;
                boolean pieceSideAhead = (score > 0) == (ps == 0);
                if (!pieceSideAhead) continue;

                boolean connected = false;
                int bestRank = 0;
                int pawnSqBest = -1;
                for (int f = 0; f < 8; f++) {
                    if (pawnCnt[pw][f] == 0) continue;
                    if (f < 7 && pawnCnt[pw][f + 1] > 0) connected = true;
                    int rk = pawnRank(pw, f);
                    if (rk > bestRank) {
                        bestRank = rk;
                        pawnSqBest = (pw == 0 ? pawnMax[0][f] : pawnMin[1][f]) * 8 + f;
                    }
                }
                if (pawnSqBest < 0) continue;
                int dPawnKing = chebyshev(kingSq[pw], pawnSqBest);
                int dPieceKing = chebyshev(kingSq[ps], pawnSqBest);
                boolean kingCloser = dPawnKing + 1 <= dPieceKing;

                int num = 16;
                if (pb - pa == 1) {
                    if (connected && kingCloser) num = 4;
                    else if (connected) num = 9;
                    else if (kingCloser) num = 10;
                } else {
                    if (connected && kingCloser) num = 6;
                    else if (connected) num = 11;
                    else if (kingCloser) num = 12;
                }
                if (num < 16 && bestRank >= 4) num = Math.max(2, num - 2);
                score = score * num / 16;
            }
            return score;
        }

        private int pawnRank(int c, int f) {
            return c == 0 ? pawnMax[0][f] : 7 - pawnMin[1][f];
        }

        private int flearEndgamePatterns(int score, int phase, int pw0, int pw1) {
            int total = pw0 + pw1;
            boolean noPieces = npMat[0] == 0 && npMat[1] == 0;
            boolean rookEnding = npMat[0] == 500 && npMat[1] == 500;

            if (noPieces && total > 0) {
                int k0 = kingSq[0], k1 = kingSq[1];
                int df = Math.abs((k0 & 7) - (k1 & 7)), dr = Math.abs((k0 >> 3) - (k1 >> 3));
                if ((df == 0 && dr == 2) || (dr == 0 && df == 2)) {
                    score += side == 0 ? -14 : 14;
                }
                for (int c = 0; c < 2; c++) {
                    int enemyPawn = c == 0 ? -P : P;
                    int best = 8;
                    for (int s = 0; s < 64; s++) {
                        if (sq[s] == enemyPawn) best = Math.min(best, chebyshev(kingSq[c], s));
                    }
                    if (best < 8) {
                        int v = (7 - best) * 4;
                        score += c == 0 ? v : -v;
                    }
                }
                if (Math.abs(score) >= 80) score += score / 8;
            }

            if (rookEnding && total > 0 && score != 0) {
                int strong = score > 0 ? 0 : 1;
                int weak = 1 - strong;
                int ps = strong == 0 ? pw0 : pw1;
                int pwk = strong == 0 ? pw1 : pw0;

                if (ps - pwk == 2) {
                    int first = -1, count = 0;
                    boolean connected = false, rookPawn = false;
                    for (int f = 0; f < 8; f++) {
                        if (!passedFile[strong][f]) continue;
                        count++;
                        if (f == 0 || f == 7) rookPawn = true;
                        if (first >= 0 && f == first + 1) connected = true;
                        first = f;
                    }
                    if (connected) {
                        int front = 0, frontFile = 0;
                        for (int f = 0; f < 8; f++) {
                            if (passedFile[strong][f] && pawnRank(strong, f) >= front) {
                                front = pawnRank(strong, f);
                                frontFile = f;
                            }
                        }
                        int stopSq = strong == 0 ? frontFile + 8 * Math.min(7, front + 1) : frontFile + 8 * Math.max(0, 6 - front);
                        boolean blockaded = chebyshev(kingSq[weak], stopSq) <= 1 && front <= 4;
                        if (blockaded) {
                            score = score * 11 / 16;
                        } else {
                            score += strong == 0 ? 35 : -35;
                        }
                    } else if (count >= 2 && rookPawn) {
                        score = score * 12 / 16;
                    }
                }

                if (ps - pwk == 1) {
                    for (int pf = 0; pf <= 7; pf += 7) {
                        if (!passedFile[strong][pf] || rookN[weak] < 1) continue;
                        int pr = pawnRank(strong, pf);
                        int wr = rookSq[weak][0];
                        boolean behind = (wr & 7) == pf && (strong == 0 ? (wr >> 3) < pr : (7 - (wr >> 3)) < pr);
                        int wkf = kingSq[weak] & 7;
                        boolean kingReady = wkf >= 2 && wkf <= 5;
                        if (behind && kingReady) score = score * 12 / 16;
                    }
                }

                boolean low = false, high = false;
                for (int f = 0; f < 8; f++) {
                    if (pawnCnt[strong][f] > 0) {
                        if (f <= 3) low = true; else high = true;
                    }
                }
                if (low && high && ps > pwk) score += strong == 0 ? 10 : -10;
            }

            if (rookEnding && total > 0) {
                for (int c = 0; c < 2; c++) {
                    if (rookN[c] < 1) continue;
                    int rf = rookSq[c][0] & 7;
                    int rrk = c == 0 ? (rookSq[c][0] >> 3) : 7 - (rookSq[c][0] >> 3);
                    int en = 1 - c;
                    int ef = kingSq[en] & 7;
                    int anchor = kingSq[c] & 7;
                    int bestR = -1;
                    for (int f = 0; f < 8; f++) {
                        if (passedFile[c][f] && pawnRank(c, f) > bestR) {
                            bestR = pawnRank(c, f);
                            anchor = f;
                        }
                    }
                    boolean cut = (ef < rf && rf < anchor) || (anchor < rf && rf < ef);
                    int v = cut ? 18 + 4 * Math.min(4, Math.abs(ef - rf)) : 0;
                    int enemyKingRel = en == 0 ? (kingSq[en] >> 3) : 7 - (kingSq[en] >> 3);
                    if (rrk == 6 && enemyKingRel == 7) v += 18;
                    score += c == 0 ? v : -v;
                }
            }

            boolean onlyMinors = queenSq[0] < 0 && queenSq[1] < 0 && rookN[0] == 0 && rookN[1] == 0
                    && bishopCnt[0] + bishopCnt[1] + knightCnt[0] + knightCnt[1] > 0;

            for (int c = 0; c < 2; c++) {
                int en = 1 - c;
                if (npMat[en] == 320 && knightCnt[en] == 1 && npMat[c] <= 330) {
                    for (int f = 0; f <= 7; f += 7) {
                        if (passedFile[c][f]) {
                            int v = 12 + 6 * pawnRank(c, f);
                            score += c == 0 ? v : -v;
                        }
                    }
                }
            }

            if (onlyMinors && total > 0) {
                int w = (knightCnt[0] + knightCnt[1] == 0) ? 7 : 4;
                for (int c = 0; c < 2; c++) {
                    int kr = c == 0 ? (kingSq[c] >> 3) : 7 - (kingSq[c] >> 3);
                    if (kr >= 4) {
                        int v = (kr - 3) * w;
                        score += c == 0 ? v : -v;
                    }
                }
            }

            for (int rs = 0; rs < 2; rs++) {
                int ms = 1 - rs;
                if (npMat[rs] != 500 || rookN[rs] != 1 || queenSq[rs] >= 0 || queenSq[ms] >= 0) continue;
                if (npMat[ms] != 320 && npMat[ms] != 330) continue;
                if (bishopCnt[ms] + knightCnt[ms] != 1) continue;
                int pr = rs == 0 ? pw0 : pw1;
                int pm = rs == 0 ? pw1 : pw0;
                boolean favoursRook = (score > 0) == (rs == 0);

                if (pr == pm && total > 0 && favoursRook) {
                    boolean low = false, high = false;
                    for (int f = 0; f < 8; f++) {
                        if (pawnCnt[0][f] + pawnCnt[1][f] > 0) {
                            if (f <= 3) low = true; else high = true;
                        }
                    }
                    if (!(low && high)) score = score * 8 / 16;
                }
                if (pr > pm && knightCnt[ms] == 1 && (passedFile[rs][0] || passedFile[rs][7])) {
                    score += rs == 0 ? 20 : -20;
                }
            }

            return score;
        }

        private int runawayPassedPawn(int c, int s, int f, int rr) {
            int en = 1 - c;
            int dir = c == 0 ? 8 : -8;
            int promo = (c == 0 ? 56 : 0) + f;
            int steps = 7 - rr;
            boolean pawnToMove = side == c;

            if (chebyshev(kingSq[en], promo) <= steps - (pawnToMove ? 1 : 0)) {
                return 0;
            }

            for (int x = s + dir; x >= 0 && x < 64; x += dir) {
                if (sq[x] != 0) return 0;
            }

            for (int x = 0; x < 64; x++) {
                int p = sq[x];
                if (p == 0 || (p > 0) == (c == 0)) continue;
                int t = Math.abs(p);
                if (t == P || t == K) continue;

                if (t == R || t == Q) {
                    if (x == s) return 0;
                    if ((x & 7) == f) {
                        int step = x > s ? 8 : -8;
                        boolean clear = true;
                        for (int y = s + step; y != x; y += step) {
                            if (sq[y] != 0) { clear = false; break; }
                        }
                        if (clear) return 0;
                    }
                    int xr = x >> 3, pr = s >> 3;
                    boolean ahead = c == 0 ? xr > pr : xr < pr;
                    if (ahead) {
                        int fx = x & 7;
                        int lo = Math.min(fx, f), hi = Math.max(fx, f);
                        boolean clear = true;
                        for (int y = lo + 1 + 8 * xr; y < hi + 8 * xr; y++) {
                            if (sq[y] != 0) { clear = false; break; }
                        }
                        if (clear) return 0;
                    }
                } else if (chebyshev(x, promo) <= steps + 2) {
                    return 0;
                }
            }

            return rr == 4 ? 80 : rr == 5 ? 150 : 240;
        }

        private int pawnTotal(int c) {
            int n = 0;
            for (int f = 0; f < 8; f++) n += pawnCnt[c][f];
            return n;
        }
    }
}