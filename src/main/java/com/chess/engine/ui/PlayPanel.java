package com.chess.engine.ui;

import com.chess.engine.engine.AntiChess;
import com.chess.engine.engine.AtomicChess;
import com.chess.engine.engine.Board;
import com.chess.engine.engine.ChessEngine;
import com.chess.engine.engine.HordeChess;
import com.chess.engine.engine.KingOfTheHill;
import com.chess.engine.engine.RacingKings;
import com.chess.engine.engine.Move;
import com.chess.engine.engine.MoveGenerator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/*
  A game against the engine at one of eight strengths, with clocks. The engine budgets its thinking time
  from its own clock (a small fraction of what is left plus most of the increment), so it cannot lose on time.
 */
@SuppressWarnings("serial")
public class PlayPanel extends JPanel {

    private final ChessBoardPanel board = new ChessBoardPanel(new Board());
    private final Random rnd = new Random();

    // game state
    private ChessEngine engine;
    private PlayConfig cfg;
    private boolean humanWhite = true;
    private GameClock clock;
    private final List<Board> positions = new ArrayList<>();      // positions[0] = start, positions[k] = after ply k
    private final List<Boolean> irreversible = new ArrayList<>(); // per ply: pawn move or capture
    private int generation;
    private boolean over = true;
    private boolean engineThinking;
    private Timer uiTimer;

    // widgets
    private final JLabel topName = UiKit.label(" ", Font.BOLD, 15, UiKit.BRIGHT);
    private final JLabel bottomName = UiKit.label(" ", Font.BOLD, 15, UiKit.BRIGHT);
    private final JLabel topCaptured = UiKit.label(" ", Font.PLAIN, 16, UiKit.TEXT);
    private final JLabel bottomCaptured = UiKit.label(" ", Font.PLAIN, 16, UiKit.TEXT);
    private final ClockLabel topClock = new ClockLabel();
    private final ClockLabel bottomClock = new ClockLabel();
    private final JLabel titleLabel = UiKit.label("Play against the computer", Font.BOLD, 20, UiKit.BRIGHT);
    private final JLabel infoLabel = UiKit.label(" ", Font.PLAIN, 13, UiKit.TEXT);
    private final JLabel statusLabel = UiKit.label(" ", Font.BOLD, 15, UiKit.BRIGHT);
    private final MoveListView moves = new MoveListView();
    private final UiKit.FlatButton resignButton = new UiKit.FlatButton("Resign", UiKit.PANEL_HI);
    private final UiKit.FlatButton drawButton = new UiKit.FlatButton("Offer draw", UiKit.PANEL_HI);
    private final UiKit.FlatButton takebackButton = new UiKit.FlatButton("Take back", UiKit.PANEL_HI);
    private final UiKit.FlatButton flipButton = new UiKit.FlatButton("Flip board", UiKit.PANEL_HI);
    private final UiKit.FlatButton newGameButton = new UiKit.FlatButton("New game", UiKit.GREEN);
    private final JPanel resultPanel = UiKit.darkPanel(new GridLayout(0, 1, 0, 6));
    private final JLabel resultTitle = UiKit.label(" ", Font.BOLD, 22, UiKit.BRIGHT);
    private final JLabel resultDetail = UiKit.label(" ", Font.PLAIN, 14, UiKit.TEXT);

    public PlayPanel() {
        super(new BorderLayout(18, 0));
        setBackground(UiKit.BG);
        setBorder(new EmptyBorder(16, 20, 16, 20));

        JPanel boardArea = new JPanel(new BorderLayout(0, 8));
        boardArea.setOpaque(false);
        boardArea.add(playerBar(topName, topCaptured, topClock), BorderLayout.NORTH);
        boardArea.add(new SquareHolder(board), BorderLayout.CENTER);
        boardArea.add(playerBar(bottomName, bottomCaptured, bottomClock), BorderLayout.SOUTH);
        add(boardArea, BorderLayout.CENTER);
        add(buildSide(), BorderLayout.EAST);

        board.addPropertyChangeListener("boardMove", this::onBoardMove);
        board.setMovesEnabled(false);
        statusLabel.setText("Press PLAY in the menu bar to start a game.");
    }

    private JPanel playerBar(JLabel name, JLabel captured, ClockLabel clockLabel) {
        JPanel bar = UiKit.darkPanel(new BorderLayout(10, 0));
        bar.setBorder(new EmptyBorder(6, 12, 6, 8));
        JPanel left = new JPanel(new GridLayout(0, 1));
        left.setOpaque(false);
        left.add(name);
        left.add(captured);
        bar.add(left, BorderLayout.CENTER);
        bar.add(clockLabel, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildSide() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setOpaque(false);
        side.setPreferredSize(new Dimension(340, 640));

        JPanel head = UiKit.darkPanel(new GridLayout(0, 1, 0, 4));
        head.setBorder(new EmptyBorder(14, 18, 14, 18));
        head.add(titleLabel);
        head.add(infoLabel);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        side.add(head);
        side.add(Box.createVerticalStrut(8));

        moves.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(moves);
        side.add(Box.createVerticalStrut(8));

        JPanel status = UiKit.darkPanel(new BorderLayout());
        status.setBorder(new EmptyBorder(10, 18, 10, 18));
        status.add(statusLabel, BorderLayout.CENTER);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        status.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        side.add(status);
        side.add(Box.createVerticalStrut(8));

        JPanel controls = new JPanel(new GridLayout(2, 2, 8, 8));
        controls.setOpaque(false);
        controls.add(takebackButton);
        controls.add(drawButton);
        controls.add(flipButton);
        controls.add(resignButton);
        controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        side.add(controls);
        side.add(Box.createVerticalStrut(8));

        resultPanel.setBorder(new EmptyBorder(14, 18, 14, 18));
        resultPanel.add(resultTitle);
        resultPanel.add(resultDetail);
        resultPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        resultPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        resultPanel.setVisible(false);
        side.add(resultPanel);
        side.add(Box.createVerticalStrut(8));

        newGameButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        newGameButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        side.add(newGameButton);

        resignButton.addActionListener(e -> resign());
        drawButton.addActionListener(e -> offerDraw());
        takebackButton.addActionListener(e -> takeBack());
        flipButton.addActionListener(e -> {
            board.setFlipped(!board.isFlipped());
            updateBars();
        });
        newGameButton.addActionListener(e -> showDialogAndStart());
        setControlsEnabled(false);
        return side;
    }

    // ------------------------------------------------------------------ starting a game

    /** Opens the setup dialog and starts a game with the chosen settings. */
    /** Called after the theme was changed in the Themes window. */
    public void applyTheme() {
        board.applyTheme();
    }

    public void showDialogAndStart() {
        PlayConfig c = PlayDialog.show(SwingUtilities.getWindowAncestor(this));
        if (c != null) startGame(c);
    }

    public boolean isGameInProgress() {
        return !over;
    }

    public void startGame(PlayConfig config) {
        generation++;
        stopEngine();
        cfg = config;
        humanWhite = config.side == 0 || (config.side == 2 && rnd.nextBoolean());
        engine = new ChessEngine();
        if (config.level < 8) engine.setThreads(1);
        Board start = config.variant.newBoard(rnd);
        positions.clear();
        positions.add(start.copy());
        irreversible.clear();
        clock = new GameClock(config.unlimited, config.baseMs, config.incrementMs);
        over = false;
        engineThinking = false;

        board.setFlipped(!humanWhite);
        board.setBoard(start);
        moves.reset(true, 1);
        resultPanel.setVisible(false);
        titleLabel.setText("vs Computer: " + EngineLevel.title(config.level));
        infoLabel.setText("You play " + (humanWhite ? "white" : "black") + "   -   " + config.timeText()
                + (config.variant == PlayVariant.STANDARD ? "" : "   -   " + config.variant.label));
        setControlsEnabled(true);
        takebackButton.setEnabled(false);
        updateBars();
        updateStatus();

        if (uiTimer != null) uiTimer.stop();
        uiTimer = new Timer(100, e -> tick());
        uiTimer.start();

        if (humanWhite) {
            board.setMovesEnabled(true);
        } else {
            board.setMovesEnabled(false);
            engineMove();
        }
    }

    /* Stops timers and the engine (the screen is being left). */
    public void deactivate() {
        // a running game keeps its clocks, so the player cannot gain time by leaving; nothing to stop here
    }

    private void stopEngine() {
        if (engine != null) engine.cancel();
    }

    private void setControlsEnabled(boolean on) {
        resignButton.setEnabled(on);
        drawButton.setEnabled(on);
        takebackButton.setEnabled(on);
        flipButton.setEnabled(true);
    }

    // moves

    private boolean humanTurn() {
        return positions.get(positions.size() - 1).isWhiteToMove() == humanWhite;
    }

    /* True when the player's pieces are at the bottom of the board. */
    private boolean humanAtBottom() {
        return board.isFlipped() == !humanWhite;
    }

    private int engineSide() {
        return humanWhite ? 1 : 0;
    }

    private void onBoardMove(PropertyChangeEvent evt) {
        if (over || engineThinking || !humanTurn()) {
            board.setBoard(positions.get(positions.size() - 1));
            return;
        }
        Board before = (Board) evt.getOldValue();
        Board after = (Board) evt.getNewValue();
        Move m = ChessUtil.findMoveBetween(before, after);
        if (m == null) {
            board.setBoard(before);
            return;
        }
        commit(m, ChessUtil.san(before, m), before, true);
    }

    /* Records a move that has already been played on the board. */
    private void commit(Move m, String san, Board before, boolean byHuman) {
        Board after = board.getBoard().copy();
        char piece = before.getPiece(m.from);
        boolean pawn = Character.toUpperCase(piece) == 'P';
        boolean capture = before.getPiece(m.to) != '.' || (pawn && (m.from & 7) != (m.to & 7));
        positions.add(after);
        irreversible.add(pawn || capture);
        moves.add(san);

        int ply = positions.size() - 1;
        int mover = before.isWhiteToMove() ? 0 : 1;
        if (!cfg.unlimited) {
            if (ply == 2) clock.start(0);           // the clocks start once both sides have moved
            else if (ply > 2) clock.moved(mover);
        }

        takebackButton.setEnabled(true);
        updateBars();
        if (checkGameEnd(after, mover)) return;
        updateStatus();

        if (byHuman) {
            engineMove();
        } else {
            engineThinking = false;
            board.setMovesEnabled(true);
        }
    }

    private void engineMove() {
        if (over) return;
        engineThinking = true;
        board.setMovesEnabled(false);
        statusLabel.setText("Computer is thinking...");

        final int myGen = generation;
        final Board pos = positions.get(positions.size() - 1).copy();
        final List<Board> history = new ArrayList<>();
        for (Board b : positions) history.add(b.copy());
        final int side = engineSide();
        final long remaining = clock.remaining(side);
        final long budget = thinkTime(remaining);
        final int level = (!cfg.unlimited && remaining < 400) ? Math.min(cfg.level, 2) : cfg.level;
        final ChessEngine eng = engine;

        SwingWorker<Move, Void> worker = new SwingWorker<Move, Void>() {
            @Override protected Move doInBackground() {
                if (level >= EngineLevel.COUNT) return eng.findBestMove(pos, ChessEngine.MOVE_MAX_DEPTH, budget, history);
                return eng.findLevelMove(pos, level, budget, history);
            }

            @Override protected void done() {
                if (myGen != generation || over) return;
                try {
                    Move m = get();
                    if (m == null) {
                        checkGameEnd(pos, humanWhite ? 1 : 0);
                        return;
                    }
                    Board before = board.getBoard().copy();
                    String san = ChessUtil.san(before, m);
                    board.playMove(m);
                    commit(m, san, before, false);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    engineThinking = false;
                    board.setMovesEnabled(true);
                }
            }
        };
        worker.execute();
    }

    /*
      How long the engine may think. A small share of its remaining time plus most of the increment, never
      more than 1/25 of the clock (the full engine may extend a search to about twice the request), and a
      very small budget when it is nearly out of time.
     */
    private long thinkTime(long remaining) {
        if (cfg.unlimited) return 4000;
        long inc = cfg.incrementMs;
        long base = remaining / 30 + (inc * 7) / 10;
        long budget = Math.min(base, remaining / 25);
        if (remaining < 1500) budget = Math.max(10, remaining / 40);
        return Math.max(10, Math.min(budget, 20_000));
    }

    // end of game

    /* Returns true when the game is over. {@code mover} is the side that just moved (0 white, 1 black). */
    private boolean checkGameEnd(Board after, int mover) {
        // variant wins first: a king blown up, three checks, a king on the hill or on the eighth rank, ...
        if (after.isAtomic()) {
            if (AtomicChess.hasLostKing(after, true)) { decided(-1, "King blown up", false); return true; }
            if (AtomicChess.hasLostKing(after, false)) { decided(1, "King blown up", false); return true; }
        }
        if (after.isThreeCheck()) {
            if (after.getWhiteChecks() >= 3) { decided(1, "Three checks", false); return true; }
            if (after.getBlackChecks() >= 3) { decided(-1, "Three checks", false); return true; }
        }
        if (after.isRacingKings()) {
            switch (RacingKings.getResult(after)) {
                case WHITE_WINS: decided(1, "King reached the eighth rank", false); return true;
                case BLACK_WINS: decided(-1, "King reached the eighth rank", false); return true;
                case DRAW_BOTH_REACHED: finish(0, "Draw", "Both kings reached the eighth rank"); return true;
                case STALEMATE: finish(0, "Draw", "Stalemate"); return true;
                default: break;
            }
        }
        if (after.isKingOfTheHill()) {
            if (KingOfTheHill.hasKingOnHill(after, true)) { decided(1, "King on the hill", false); return true; }
            if (KingOfTheHill.hasKingOnHill(after, false)) { decided(-1, "King on the hill", false); return true; }
        }
        if (after.isAntiChess()) {
            if (AntiChess.hasNoPieces(after, true)) { decided(1, "All pieces lost", false); return true; }
            if (AntiChess.hasNoPieces(after, false)) { decided(-1, "All pieces lost", false); return true; }
        }

        List<Move> legal = MoveGenerator.generateLegalMoves(after);
        if (legal.isEmpty()) {
            boolean whiteToMove = after.isWhiteToMove();
            if (after.isAntiChess()) {
                decided(whiteToMove ? 1 : -1, "No legal moves left", false);     // the stalemated side wins
            } else if (after.isHorde() && HordeChess.whiteHasNoPieces(after)) {
                decided(-1, "All white pieces captured", false);
            } else if (ChessUtil.inCheck(after, whiteToMove)) {
                decided(whiteToMove ? -1 : 1, "Checkmate", false);
            } else {
                finish(0, "Draw", "Stalemate");
            }
            return true;
        }
        if (cfg.variant.standardLike() && !after.isThreeCheck() && ChessUtil.insufficientMaterial(after)) {
            finish(0, "Draw", "Insufficient material");
            return true;
        }
        long key = after.getZobristKey();
        int count = 0;
        for (Board b : positions) if (b.getZobristKey() == key) count++;
        if (count >= 3) {
            finish(0, "Draw", "Threefold repetition");
            return true;
        }
        if (halfmoveClock() >= 100) {
            finish(0, "Draw", "Fifty-move rule");
            return true;
        }
        return false;
    }

    /* Ends the game with a winner: whiteWins is 1 when White won, -1 when Black won. */
    private void decided(int whiteWins, String detail, boolean ignored) {
        boolean humanWon = (whiteWins > 0) == humanWhite;
        finish(humanWon ? 1 : -1, humanWon ? "You won" : "You lost", detail);
    }

    private int halfmoveClock() {
        int n = 0;
        for (int i = irreversible.size() - 1; i >= 0 && !irreversible.get(i); i--) n++;
        return n;
    }

    private void finish(int result, String headline, String detail) {
        over = true;
        engineThinking = false;
        clock.stop();
        stopEngine();
        board.setMovesEnabled(false);
        setControlsEnabled(false);
        resultTitle.setText(headline);
        resultTitle.setForeground(result > 0 ? UiKit.GOOD : result < 0 ? UiKit.BAD : UiKit.BRIGHT);
        resultDetail.setText(detail + (result == 0 ? "" : result > 0 ? " - well played!" : ""));
        resultPanel.setVisible(true);
        statusLabel.setText(headline + " - " + detail);
        updateBars();
    }

    private void resign() {
        if (over) return;
        int ok = JOptionPane.showConfirmDialog(this, "Resign this game?", "Resign", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            generation++;
            finish(-1, "You resigned", "The computer wins");
        }
    }

    /* The computer accepts a draw when it is not clearly better (equal or worse material) after move 15. */
    private void offerDraw() {
        if (over) return;
        if (positions.size() < 30) {
            JOptionPane.showMessageDialog(this, "The computer declines: it is too early for a draw.", "Draw offer", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Board cur = positions.get(positions.size() - 1);
        int engineMat = 0, humanMat = 0;
        for (int s = 0; s < 64; s++) {
            char p = cur.getPiece(s);
            if (p == '.') continue;
            boolean white = Character.isUpperCase(p);
            if (white == humanWhite) humanMat += ChessUtil.pieceValue(p); else engineMat += ChessUtil.pieceValue(p);
        }
        if (engineMat <= humanMat) {
            generation++;
            finish(0, "Draw", "Draw agreed");
        } else {
            JOptionPane.showMessageDialog(this, "The computer declines the draw.", "Draw offer", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // Takes back the player's last move (and the computer's reply).
    private void takeBack() {
        if (over || positions.size() < 2) return;
        generation++;
        stopEngine();
        engine = new ChessEngine();
        if (cfg.level < 8) engine.setThreads(1);
        engineThinking = false;
        int remove = humanTurn() ? 2 : 1;
        remove = Math.min(remove, positions.size() - 1);
        for (int i = 0; i < remove; i++) {
            positions.remove(positions.size() - 1);
            irreversible.remove(irreversible.size() - 1);
            moves.removeLast();
        }
        board.setBoard(positions.get(positions.size() - 1));
        if (positions.size() - 1 < 2 && !cfg.unlimited) {
            // back before the clocks started
            clock.stop();
        }
        takebackButton.setEnabled(positions.size() > 1);
        updateBars();
        updateStatus();
        if (humanTurn()) board.setMovesEnabled(true); else engineMove();
    }

    // display

    private void tick() {
        updateClocks();
        if (over || cfg == null || cfg.unlimited) return;
        for (int side = 0; side < 2; side++) {
            if (positions.size() - 1 >= 2 && clock.flagged(side)) {
                boolean winnerWhite = side == 1;
                boolean humanWon = winnerWhite == humanWhite;
                generation++;
                if (cfg.variant.standardLike() && !canMate(positions.get(positions.size() - 1), winnerWhite)) {
                    finish(0, "Draw", "Time out, but the opponent cannot checkmate");
                } else {
                    finish(humanWon ? 1 : -1, humanWon ? "You won" : "You lost", "Out of time");
                }
                return;
            }
        }
    }

    private static boolean canMate(Board b, boolean white) {
        int minors = 0;
        for (int s = 0; s < 64; s++) {
            char p = b.getPiece(s);
            if (p == '.' || Character.isUpperCase(p) != white) continue;
            char t = Character.toUpperCase(p);
            if (t == 'P' || t == 'R' || t == 'Q') return true;
            if (t == 'N' || t == 'B') minors++;
        }
        return minors >= 2;
    }

    private void updateStatus() {
        if (over) return;
        Board cur = positions.get(positions.size() - 1);
        String turn = humanTurn() ? "Your turn" : "Computer's turn";
        String extra = "";
        if (cur.isThreeCheck()) {
            int mine = humanWhite ? cur.getWhiteChecks() : cur.getBlackChecks();
            int theirs = humanWhite ? cur.getBlackChecks() : cur.getWhiteChecks();
            extra = "  (checks: you " + mine + ", computer " + theirs + ")";
        } else if (cur.isAntiChess()) {
            extra = "  (captures are forced)";
        }
        statusLabel.setText(turn + (ChessUtil.inCheck(cur, cur.isWhiteToMove()) ? " - check!" : "") + extra);
    }

    private void updateClocks() {
        if (clock == null) return;
        // the human is shown at the bottom unless the board has been flipped
        ClockLabel humanClock = humanAtBottom() ? bottomClock : topClock;
        ClockLabel engineClock = humanAtBottom() ? topClock : bottomClock;
        boolean humanToMove = !over && humanTurn();
        humanClock.set(clock.remaining(humanWhite ? 0 : 1), humanToMove && positions.size() > 2, cfg != null && cfg.unlimited);
        engineClock.set(clock.remaining(engineSide()), !over && !humanToMove && positions.size() > 2, cfg != null && cfg.unlimited);
    }

    private void updateBars() {
        if (cfg == null) return;
        boolean humanAtBottom = humanAtBottom();
        String human = "You";
        String comp = "Computer - " + EngineLevel.title(cfg.level);
        bottomName.setText(humanAtBottom ? human : comp);
        topName.setText(humanAtBottom ? comp : human);
        updateCaptured();
        updateClocks();
    }

    // Shows the pieces each side has captured, and the material lead.
    private void updateCaptured() {
        Board cur = positions.get(positions.size() - 1);
        int[] white = new int[6];
        int[] black = new int[6];
        String order = "PNBRQK";
        for (int s = 0; s < 64; s++) {
            char p = cur.getPiece(s);
            if (p == '.') continue;
            int idx = order.indexOf(Character.toUpperCase(p));
            if (Character.isUpperCase(p)) white[idx]++; else black[idx]++;
        }
        int[] startWhite = new int[6];
        int[] startBlack = new int[6];
        Board first = positions.get(0);
        for (int s = 0; s < 64; s++) {
            char p = first.getPiece(s);
            if (p == '.') continue;
            int idx = order.indexOf(Character.toUpperCase(p));
            if (Character.isUpperCase(p)) startWhite[idx]++; else startBlack[idx]++;
        }
        int whiteMat = 0, blackMat = 0;
        StringBuilder capByWhite = new StringBuilder();   // black pieces white has taken
        StringBuilder capByBlack = new StringBuilder();
        String sym = "♟♞♝♜♛";
        for (int i = 4; i >= 0; i--) {
            for (int k = black[i]; k < startBlack[i]; k++) capByWhite.append(sym.charAt(i));
            for (int k = white[i]; k < startWhite[i]; k++) capByBlack.append(sym.charAt(i));
        }
        for (int i = 0; i < 5; i++) {
            int v = new int[]{1, 3, 3, 5, 9}[i];
            whiteMat += white[i] * v;
            blackMat += black[i] * v;
        }
        int diff = whiteMat - blackMat;
        String w = capByWhite + (diff > 0 ? "  +" + diff : "");
        String b = capByBlack + (diff < 0 ? "  +" + (-diff) : "");
        boolean whiteAtBottom = !board.isFlipped();
        bottomCaptured.setText(whiteAtBottom ? w : b);
        topCaptured.setText(whiteAtBottom ? b : w);
        bottomCaptured.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 16));
        topCaptured.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 16));
    }

    /* A digital clock face. */
    private static final class ClockLabel extends JComponent {
        private String text = "--:--";
        private boolean active;
        private boolean low;

        ClockLabel() { setPreferredSize(new Dimension(120, 44)); }

        void set(long ms, boolean active, boolean unlimited) {
            String t = unlimited ? "∞" : GameClock.format(ms);
            boolean lowNow = !unlimited && ms < 20_000;
            if (!t.equals(text) || active != this.active || lowNow != low) {
                text = t;
                this.active = active;
                this.low = lowNow;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(active ? (low ? new Color(0x8f, 0x2a, 0x2a) : new Color(0x4c, 0x6e, 0x23)) : new Color(0x3a, 0x38, 0x35));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.setColor(active ? Color.WHITE : UiKit.TEXT);
            g2.setFont(new Font("Monospaced", Font.BOLD, 26));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, getWidth() - fm.stringWidth(text) - 12, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }
}