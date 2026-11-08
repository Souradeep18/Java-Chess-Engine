package com.chess.engine.ui;

import com.chess.engine.engine.Board;
import com.chess.engine.engine.Move;

import javax.swing.*;
import java.beans.PropertyChangeEvent;

/*
  Runs one puzzle on a board panel: shows the position, checks the solver's moves against the solution,
  plays the opponent's replies, and offers hints and the solution. The owning screen decides about points.
 */
public class PuzzleSession {

    interface Listener {
        /* A move was played on the board (by the solver, the opponent or the solution animation). */
        void onMove(String san, boolean whiteMoved);

        /* The solver played something other than the solution (the board has been reset). */
        void onWrong(String san);

        /* The whole solution has been played. {@code clean}: no mistake, hint or solution was used. */
        void onFinished(boolean clean, boolean solutionShown);

        /* Text for the "Your turn / Find the best move" box. */
        void onPrompt(String title, String detail);
    }

    private static final int REPLY_DELAY_MS = 450;

    private final ChessBoardPanel panel;
    private final Listener listener;

    private Puzzle puzzle;
    private int index;
    private boolean mistake;
    private boolean hintUsed;
    private boolean solutionShown;
    private boolean finished;
    private boolean busy;
    private Timer timer;
    private int generation;

    PuzzleSession(ChessBoardPanel panel, Listener listener) {
        this.panel = panel;
        this.listener = listener;
        panel.addPropertyChangeListener("boardMove", this::onBoardMove);
    }

    Puzzle current() { return puzzle; }
    boolean isFinished() { return finished; }
    boolean hadMistake() { return mistake; }
    boolean hintWasUsed() { return hintUsed; }

    void start(Puzzle p) {
        cancelTimer();
        generation++;
        puzzle = p;
        index = 0;
        mistake = false;
        hintUsed = false;
        solutionShown = false;
        finished = false;
        busy = false;

        Board b = p.board();
        panel.setFlipped(!p.whiteToSolve());
        panel.setBoard(b);
        if (p.lastMoveUci != null && p.lastMoveUci.length() >= 4) {
            panel.setLastMove(ChessUtil.squareIndex(p.lastMoveUci.substring(0, 2)),
                    ChessUtil.squareIndex(p.lastMoveUci.substring(2, 4)));
        }
        panel.setMovesEnabled(true);
        prompt();
    }

    private void prompt() {
        String side = puzzle.whiteToSolve() ? "white" : "black";
        listener.onPrompt("Your turn", "Find the best move for " + side + ".");
    }

    private void onBoardMove(PropertyChangeEvent evt) {
        if (puzzle == null || finished || busy) return;
        Board before = (Board) evt.getOldValue();
        Board after = (Board) evt.getNewValue();
        Move played = ChessUtil.findMoveBetween(before, after);
        if (played == null) {
            panel.setBoard(before);
            return;
        }
        String san = ChessUtil.san(before, played);
        String uci = ChessUtil.uci(played);
        String expected = puzzle.solution[index];

        boolean correct = uci.equals(expected) || ChessUtil.isCheckmate(after);
        if (!correct) {
            mistake = true;
            panel.setBoard(before);
            restoreLastMoveMarks();
            panel.setMovesEnabled(true);
            listener.onWrong(san);
            return;
        }

        listener.onMove(san, before.isWhiteToMove());
        index++;
        panel.setHintSquare(-1);
        if (ChessUtil.isCheckmate(after) || index >= puzzle.solution.length) {
            finish();
            return;
        }
        replyAfterDelay();
    }

    private void restoreLastMoveMarks() {
        // after a wrong move the board is rebuilt, so mark the opponent's previous move again
        String last = index == 0 ? puzzle.lastMoveUci : puzzle.solution[index - 1];
        if (last != null && last.length() >= 4) {
            panel.setLastMove(ChessUtil.squareIndex(last.substring(0, 2)), ChessUtil.squareIndex(last.substring(2, 4)));
        }
    }

    private void replyAfterDelay() {
        busy = true;
        panel.setMovesEnabled(false);
        final int gen = generation;
        cancelTimer();
        timer = new Timer(REPLY_DELAY_MS, e -> {
            if (gen != generation) return;
            playSolutionMove();
            busy = false;
            if (index >= puzzle.solution.length) {
                finish();
            } else {
                panel.setMovesEnabled(true);
                prompt();
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void playSolutionMove() {
        Board b = panel.getBoard();
        Move m = ChessUtil.fromUci(b, puzzle.solution[index]);
        if (m == null) {
            index = puzzle.solution.length;
            return;
        }
        String san = ChessUtil.san(b, m);
        boolean white = b.isWhiteToMove();
        panel.playMove(m);
        listener.onMove(san, white);
        index++;
    }

    private void finish() {
        finished = true;
        busy = false;
        panel.setMovesEnabled(false);
        panel.setHintSquare(-1);
        boolean clean = !mistake && !hintUsed && !solutionShown;
        listener.onFinished(clean, solutionShown);
    }

    /** Highlights the piece that has to move. */
    void hint() {
        if (puzzle == null || finished || busy) return;
        hintUsed = true;
        String u = puzzle.solution[index];
        panel.setHintSquare(ChessUtil.squareIndex(u.substring(0, 2)));
    }

    /* Plays the rest of the solution move by move. */
    void showSolution() {
        if (puzzle == null || finished) return;
        solutionShown = true;
        busy = true;
        panel.setMovesEnabled(false);
        panel.setHintSquare(-1);
        final int gen = generation;
        cancelTimer();
        timer = new Timer(650, null);
        timer.addActionListener(e -> {
            if (gen != generation) {
                timer.stop();
                return;
            }
            if (index >= puzzle.solution.length) {
                timer.stop();
                finish();
                return;
            }
            playSolutionMove();
            if (index >= puzzle.solution.length) {
                timer.stop();
                finish();
            }
        });
        timer.setInitialDelay(300);
        timer.start();
    }

    void stop() {
        cancelTimer();
        generation++;
        busy = false;
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }
}