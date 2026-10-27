package com.chess.engine.analysis;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.chess.engine.engine.Board;
import com.chess.engine.engine.ChessEngine;
import com.chess.engine.engine.Move;
import com.chess.engine.engine.MoveGenerator;

/*
  Reviews a whole game with the engine.
 
  Every position of the game is searched once (best two lines). For each move that was played the review
  compares the position before and after it in terms of winning chances, which gives
   - a classification (brilliant, great, best, excellent, good, book, inaccuracy, mistake, blunder),
   - the accuracy of the move, and from those the accuracy of each player,
   - the evaluation curve that the graph draws.
 */
public class GameAnlyser {

    
	//  result types

    public enum MoveClass {
        BRILLIANT("Brilliant", "!!"),
        GREAT("Great", "!"),
        BEST("Best", ""),
        EXCELLENT("Excellent", ""),
        GOOD("Good", ""),
        BOOK("Book", ""),
        INACCURACY("Inaccuracy", "?!"),
        MISTAKE("Mistake", "?"),
        MISS("Miss", "X"),
        BLUNDER("Blunder", "??");

        public final String label;
        public final String symbol;

        MoveClass(String label, String symbol) {
            this.label = label;
            this.symbol = symbol;
        }
    }

    /* What the review says about one move of the game. */
    public static final class MoveReview {
        public final int ply;                 // 1 = White's first move
        public final boolean white;           // who played it
        public final Move move;
        public final String san;              // as written in the PGN
        public final MoveClass moveClass;
        public final int evalBefore;          // centipawns from White's point of view, position before the move
        public final int evalAfter;           // ... and after it
        public final double winLoss;          // winning chances lost by the mover (0..100)
        public final double accuracy;         // 0..100
        public final Move bestMove;           // the engine's choice (may equal the played move)

        public MoveReview(int ply, boolean white, Move move, String san, MoveClass moveClass, int evalBefore, int evalAfter,
                   double winLoss, double accuracy, Move bestMove) {
            this.ply = ply;
            this.white = white;
            this.move = move;
            this.san = san;
            this.moveClass = moveClass;
            this.evalBefore = evalBefore;
            this.evalAfter = evalAfter;
            this.winLoss = winLoss;
            this.accuracy = accuracy;
            this.bestMove = bestMove;
        }

        /* "12. Qe3" or "12... Nf6". */
        public String label() {
            return ((ply + 1) / 2) + (white ? ". " : "... ") + san;
        }
    }

    /* The finished (or, while the analysis runs, partial) review of a game. */
    public static final class GameReview {
        /** Evaluation (centipawns, White's view) of the positions 0..n; n = number of moves analysed so far. */
        public final List<Integer> evals = new ArrayList<>();
        public final List<MoveReview> moves = new ArrayList<>();
        public double whiteAccuracy = 100;
        public double blackAccuracy = 100;
        public int totalMoves;

        public int count(boolean white, MoveClass c) {
            int n = 0;
            for (MoveReview m : moves) {
                if (m.white == white && m.moveClass == c) n++;
            }
            return n;
        }

        /* A snapshot that can be read on another thread while the analysis goes on. */
        public GameReview copy() {
            GameReview c = new GameReview();
            c.evals.addAll(evals);
            c.moves.addAll(moves);
            c.whiteAccuracy = whiteAccuracy;
            c.blackAccuracy = blackAccuracy;
            c.totalMoves = totalMoves;
            return c;
        }

        /* The chance (0..1) that White wins, as the graph shows it. */
        public double whiteShare(int position) {
            return winPercent(evals.get(position)) / 100.0;
        }
    }

    public interface Listener {
        /** Called on the analysing thread after each position; {@code done} of {@code total} positions are searched. */
        void onProgress(int done, int total, GameReview partial);
    }

    
	//  settings

    private static final int MATE = 10000;
    private static final int MAX_MATE = 8; // longer mates are not counted as "missed"
    private static final int DEFAULT_DEPTH = 24;
    private static final long DEFAULT_MILLIS = 1800;

    private final ChessEngine engine;
    private final int depth;
    private final long millisPerPosition;
    private volatile boolean cancelled = false;

    public GameAnlyser(int depth) {
        this(depth, DEFAULT_MILLIS);
    }

    public GameAnlyser() {
        this(DEFAULT_DEPTH, DEFAULT_MILLIS);
    }

    public GameAnlyser(int depth, long millisPerPosition) {
        this.engine = new ChessEngine();
        this.engine.setAnalysisThreads(ChessEngine.recommendedThreads());
        this.depth = depth;
        this.millisPerPosition = millisPerPosition;
    }

    /** Stops a running {@link #review} as soon as possible. */
    public void cancel() {
        cancelled = true;
        engine.cancel();
    }


    //  winning chances and accuracy

    /* Winning chances in percent (0..100) for White, from an evaluation in centipawns. */
    public static double winPercent(int centipawns) {
        double cp = Math.max(-MATE, Math.min(MATE, centipawns));
        return 50.0 + 50.0 * (2.0 / (1.0 + Math.exp(-0.00368208 * cp)) - 1.0);
    }

    /* Accuracy (0..100) of a move that lost {@code winLoss} percent of winning chances. */
    public static double moveAccuracy(double winLoss) {
        double a = 103.1668 * Math.exp(-0.04354 * Math.max(0, winLoss)) - 3.1669;
        return Math.max(0, Math.min(100, a));
    }

    //  the review
    private static final class PositionInfo {
        int eval;                 // best line, White's view
        Move best;
        List<Move> bestPv = new ArrayList<>();
        Move secondMove;
        int secondEval;
        boolean hasSecond;
        final Map<Move, Integer> lineEvals = new java.util.HashMap<>();
    }

    /* Reviews the game. Blocks until finished (call it from a worker thread). */
    public GameReview review(Board start, List<Move> moves, List<String> sans, Listener listener) {
        cancelled = false;

        GameReview review = new GameReview();
        review.totalMoves = moves.size();

        // all positions
        List<Board> boards = new ArrayList<>();
        Board b = start.copy();
        boards.add(b.copy());
        for (Move m : moves) {
            MoveGenerator.makeMove(b, m);
            boards.add(b.copy());
        }

        PositionInfo[] info = new PositionInfo[boards.size()];
        boolean inBook = true;

        for (int i = 0; i < boards.size(); i++) {
            if (cancelled) {
                break;
            }

            info[i] = searchPosition(boards.get(i));
            review.evals.add(info[i].eval);

            if (i > 0) {
                boolean whiteMoved = boards.get(i - 1).isWhiteToMove();
                String san = sans != null && i - 1 < sans.size() ? sans.get(i - 1) : moves.get(i - 1).toString();

                boolean book = false;
                if (inBook) {
                    book = engine.isTheoryMove(boards.get(i - 1), moves.get(i - 1));
                    if (!book) {
                        inBook = false;
                    }
                }

                review.moves.add(judge(i, whiteMoved, boards.get(i - 1), moves.get(i - 1), san, info[i - 1], info[i],
                        book));
                computeAccuracy(review);
            }

            if (listener != null) {
                listener.onProgress(i + 1, boards.size(), review);
            }
        }

        return review;
    }

    /* The old entry point: one line of text per move. */
    public List<String> analyzeGame(Board startingPosition, List<Move> moves) {
        GameReview review = review(startingPosition, moves, null, null);
        List<String> results = new ArrayList<>();
        for (MoveReview m : review.moves) {
            results.add(m.label() + " -> " + m.moveClass.label.toUpperCase() + " (lost winning chances: "
                    + String.format("%.1f", m.winLoss) + "%)");
        }
        return results;
    }

    private PositionInfo searchPosition(Board board) {
        PositionInfo p = new PositionInfo();

        if (MoveGenerator.generateLegalMoves(board).isEmpty()) {
            boolean inCheck = MoveGenerator.isKingInCheck(board, board.isWhiteToMove());
            p.eval = inCheck ? (board.isWhiteToMove() ? -MATE : MATE) : 0;
            return p;
        }

        // keep the last two completed depths: averaging them removes the odd/even-depth swing of the evaluation
        final List<ChessEngine.AnalysisLine> last = new ArrayList<>();
        final List<ChessEngine.AnalysisLine> previous = new ArrayList<>();
        engine.analyse(board.copy(), 2, depth, millisPerPosition, (d, lines) -> {
            synchronized (last) {
                previous.clear();
                previous.addAll(last);
                last.clear();
                last.addAll(lines);
            }
        });

        synchronized (last) {
            if (last.isEmpty()) {
                return p;
            }
            ChessEngine.AnalysisLine first = last.get(0);
            p.eval = smoothed(first, previous);
            if (!first.pv.isEmpty()) {
                p.best = first.pv.get(0);
                p.bestPv = new ArrayList<>(first.pv);
            }
            for (ChessEngine.AnalysisLine line : last) {
                if (!line.pv.isEmpty()) {
                    p.lineEvals.put(line.pv.get(0), smoothed(line, previous));
                }
            }
            if (last.size() > 1 && !last.get(1).pv.isEmpty()) {
                p.hasSecond = true;
                p.secondMove = last.get(1).pv.get(0);
                p.secondEval = smoothed(last.get(1), previous);
            }
        }
        return p;
    }

    /* The evaluation of a line, averaged with the same first move one depth earlier (never for mate scores). */
    private static int smoothed(ChessEngine.AnalysisLine line, List<ChessEngine.AnalysisLine> previous) {
        int now = lineEval(line);
        if (line.mateIn != 0 || line.pv.isEmpty()) {
            return now;
        }
        for (ChessEngine.AnalysisLine old : previous) {
            if (old.mateIn == 0 && !old.pv.isEmpty() && old.pv.get(0).equals(line.pv.get(0))) {
                return (now + lineEval(old)) / 2;
            }
        }
        return now;
    }

    private static int lineEval(ChessEngine.AnalysisLine line) {
        if (line.mateIn > 0) return MATE - line.mateIn;
        if (line.mateIn < 0) return -MATE - line.mateIn;
        return Math.max(-2500, Math.min(2500, line.scoreCp));   // never mistaken for a mate score
    }

    //  judging one move

    private MoveReview judge(int ply, boolean white, Board before, Move played, String san, PositionInfo pre,
                             PositionInfo post, boolean book) {
        int evalBefore = pre.eval;

        // the position after the move: the line of the search that started with it when there is one
        Integer inLine = pre.lineEvals.get(played);
        boolean isBest = pre.best != null && pre.best.equals(played);
        int evalAfter = inLine != null ? inLine : post.eval;
        if (isBest) {
            evalAfter = evalBefore;
        }

        double wBefore = winPercent(evalBefore);
        double wAfter = winPercent(evalAfter);
        double loss = white ? wBefore - wAfter : wAfter - wBefore;
        if (loss < 0 || isBest) {
            loss = 0;
        }

        // the mover's own winning chances
        double moverBefore = white ? wBefore : 100 - wBefore;
        double moverAfter = white ? wAfter : 100 - wAfter;

        // a forced mate was there and the move played does not keep it: a miss, however good the position still is
        boolean missedMate = !isBest && missedMate(evalBefore, evalAfter, white);
        if (missedMate) {
            loss = Math.max(loss, 10);
        }

        MoveClass cls = classify(loss, moverBefore, moverAfter, book, isBest, missedMate);

        if (!book && (cls == MoveClass.BEST || cls == MoveClass.EXCELLENT) && loss <= 2) {
            if (isBrilliant(before, white, played, pre, moverBefore, moverAfter, isBest)) {
                cls = MoveClass.BRILLIANT;
            } else if (isBest && isGreat(white, pre, moverBefore)) {
                cls = MoveClass.GREAT;
            }
        }

        double accuracy = book ? 100 : moveAccuracy(loss);
        return new MoveReview(ply, white, played, san, cls, evalBefore, evalAfter, loss, accuracy, pre.best);
    }

    /*
      The kind of a move from the winning chances (in percent, from the mover's point of view) before and after it.
      A "miss" is a move that gives up a winning position: the mover had a huge advantage, but the move played
      neither wins nor keeps that advantage (and is not bad enough to be a blunder that loses the game).
     */
    public static MoveClass classify(double loss, double moverBefore, double moverAfter, boolean book, boolean isBest,
                                     boolean missedMate) {
        if (book) {
            return MoveClass.BOOK;
        }
        if (!isBest && missedMate && moverAfter >= 35) {
            return MoveClass.MISS;
        }
        if (!isBest && moverBefore >= 75 && moverAfter < 70 && moverAfter >= 35 && loss > 5) {
            return MoveClass.MISS;
        }
        if (loss > 20) {
            return MoveClass.BLUNDER;
        }
        if (loss > 10) {
            return MoveClass.MISTAKE;
        }
        if (loss > 5) {
            return MoveClass.INACCURACY;
        }
        if (loss > 2) {
            return MoveClass.GOOD;
        }
        return isBest ? MoveClass.BEST : MoveClass.EXCELLENT;
    }

    /*
      The mover had a forced mate in at most {@code MAX_MATE} moves, and after the move that mate is gone: the
      position after it is no longer a mate for the mover that is one move shorter.
     */
    public static boolean missedMate(int evalBefore, int evalAfter, boolean white) {
        int before = white ? evalBefore : -evalBefore;          // positive = the mover mates
        int after = white ? evalAfter : -evalAfter;

        if (before < MATE - MAX_MATE) {
            return false;                                      // no short forced mate was available
        }
        int distance = MATE - before;                          // the mover mates in this many moves
        if (after >= MATE - MAX_MATE && MATE - after <= distance - 1) {
            return false;                                      // another move that mates just as fast
        }
        return true;
    }

    /* The only move (or nearly): every other move loses a lot of winning chances. */
    private boolean isGreat(boolean white, PositionInfo pre, double moverBefore) {
        if (!pre.hasSecond) {
            return false;                                      // a single legal move is not a great move
        }
        if (moverBefore > 92) {
            return false;                                      // the game was won anyway
        }
        double best = winPercent(pre.eval);
        double second = winPercent(pre.secondEval);
        double gap = white ? best - second : second - best;
        return gap >= 15;
    }

    /*
      A sound sacrifice: the move is the engine's choice (or as good), the mover ends up with clearly less
      material in the line that follows, and still has at least equal chances.
     */
    private boolean isBrilliant(Board before, boolean white, Move played, PositionInfo pre, double moverBefore,
                                double moverAfter, boolean isBest) {
        if (!isBest || pre.bestPv.size() < 3) {
            return false;
        }
        if (moverAfter < 45 || moverBefore > 92) {
            return false;                                      // a lost position, or one that was already won
        }

        Board b = before.copy();
        int start = material(b, white);
        int worst = 0;
        int plies = Math.min(pre.bestPv.size(), 6);
        int diff = 0;

        try {
            for (int k = 0; k < plies; k++) {
                MoveGenerator.makeMove(b, pre.bestPv.get(k));
                diff = material(b, white) - start;
                if (k % 2 == 1) {
                    worst = Math.min(worst, diff);             // after the opponent's answer
                }
            }
        } catch (RuntimeException ex) {
            return false;
        }

        // material given away that is not won back at the end of the line: a real sacrifice
        return worst <= -2 && diff <= -2;
    }

    /* Material of the mover minus the opponent's (pawn = 1, minor = 3, rook = 5, queen = 9). */
    private static int material(Board b, boolean white) {
        int total = 0;
        for (int s = 0; s < 64; s++) {
            char c = b.getPiece(s);
            if (c == '.' || c == ' ') continue;
            int v;
            switch (Character.toLowerCase(c)) {
                case 'p': v = 1; break;
                case 'n':
                case 'b': v = 3; break;
                case 'r': v = 5; break;
                case 'q': v = 9; break;
                default: v = 0;
            }
            total += (Character.isUpperCase(c) == white) ? v : -v;
        }
        return total;
    }

    //  accuracy of the players

    private static void computeAccuracy(GameReview review) {
        review.whiteAccuracy = playerAccuracy(review, true);
        review.blackAccuracy = playerAccuracy(review, false);
    }

    private static double playerAccuracy(GameReview review, boolean white) {
        double sum = 0;
        double inverse = 0;
        int n = 0;
        for (MoveReview m : review.moves) {
            if (m.white != white) continue;
            sum += m.accuracy;
            inverse += 1.0 / Math.max(1.0, m.accuracy);
            n++;
        }
        if (n == 0) {
            return 100;
        }
        double arithmetic = sum / n;
        double harmonic = n / inverse;
        return Math.round((arithmetic + harmonic) / 2.0 * 10.0) / 10.0;
    }

    /* Counts of every class for the summary table. */
    public static Map<MoveClass, Integer> summary(GameReview review, boolean white) {
        Map<MoveClass, Integer> map = new EnumMap<>(MoveClass.class);
        for (MoveClass c : MoveClass.values()) {
            map.put(c, review.count(white, c));
        }
        return map;
    }
}