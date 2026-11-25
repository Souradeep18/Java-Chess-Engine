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
import java.util.function.Consumer;

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
    private final List<Move> played = new ArrayList<>();           // played.get(k) leads from positions[k] to positions[k+1]
    private int viewIndex;                                         // the position on the board; the last one is the live game
    private String resultPgn = "*";
    private Consumer<GameRecord> analysisHandler;

    /** A finished (or running) game as the Analysis Board needs it. */
    public static final class GameRecord {
        public final Board start;
        public final List<Move> moves;
        public final List<String> san;
        public final String white, black, result, variant;
        public final boolean standard;

        GameRecord(Board start, List<Move> moves, List<String> san, String white, String black, String result,
                   String variant, boolean standard) {
            this.start = start;
            this.moves = moves;
            this.san = san;
            this.white = white;
            this.black = black;
            this.result = result;
            this.variant = variant;
            this.standard = standard;
        }
    }

    /* Who receives the game when "Analysis Board" is pressed (the main window opens the analysis board). */
    public void setAnalysisHandler(Consumer<GameRecord> handler) {
        this.analysisHandler = handler;
    }

    private final List<String> sanList = new ArrayList<>();

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
    private final PlayMoveList moves = new PlayMoveList();
    private final UiKit.FlatButton resignButton = new UiKit.FlatButton("Resign", UiKit.PANEL_HI);
    private final UiKit.FlatButton drawButton = new UiKit.FlatButton("Offer draw", UiKit.PANEL_HI);
    private final UiKit.FlatButton takebackButton = new UiKit.FlatButton("Take back", UiKit.PANEL_HI);
    private final UiKit.FlatButton flipButton = new UiKit.FlatButton("Flip board", UiKit.PANEL_HI);
    private final UiKit.FlatButton newGameButton = new UiKit.FlatButton("New game", UiKit.GREEN);
    private final UiKit.FlatButton rematchButton = new UiKit.FlatButton("Rematch", UiKit.PANEL_HI);
    private final UiKit.FlatButton analysisButton = new UiKit.FlatButton("Analysis Board", new Color(0x3b, 0x7d, 0xd8));
    private final UiKit.FlatButton firstButton = new UiKit.FlatButton("|\u25C0", UiKit.PANEL_HI);
    private final UiKit.FlatButton prevButton = new UiKit.FlatButton("\u25C0", UiKit.PANEL_HI);
    private final UiKit.FlatButton nextButton = new UiKit.FlatButton("\u25B6", UiKit.PANEL_HI);
    private final UiKit.FlatButton lastButton = new UiKit.FlatButton("\u25B6|", UiKit.PANEL_HI);
    private final JPanel resultPanel = UiKit.darkPanel(new BorderLayout(12, 0));
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
        MovePreferences.addChangeListener(() -> SwingUtilities.invokeLater(this::syncPremoves));

        // looking through the game: click a move, the arrow buttons, or the arrow keys
        moves.setOnSelect(this::goTo);
        bindKey("LEFT", () -> goTo(viewIndex - 1));
        bindKey("RIGHT", () -> goTo(viewIndex + 1));
        bindKey("HOME", () -> goTo(0));
        bindKey("END", () -> goTo(positions.size() - 1));
        firstButton.addActionListener(e -> goTo(0));
        prevButton.addActionListener(e -> goTo(viewIndex - 1));
        nextButton.addActionListener(e -> goTo(viewIndex + 1));
        lastButton.addActionListener(e -> goTo(positions.size() - 1));
        rematchButton.setEnabled(false);
        updateNav();
    }

    private void bindKey(String key, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), "play-" + key);
        getActionMap().put("play-" + key, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isShowing() && !positions.isEmpty()) action.run();
            }
        });
    }

    // ------------------------------------------------------------------ looking at earlier positions

    private boolean atLive() {
        return viewIndex == positions.size() - 1;
    }

    /*
      Shows the position after {@code index} half-moves (0 = the start). Any earlier position can be looked at
      while the game goes on, but moves can only be played in the live position (the last one).
     */
    private void goTo(int index) {
        if (positions.isEmpty()) return;
        viewIndex = Math.max(0, Math.min(index, positions.size() - 1));
        moves.select(viewIndex);
        showViewedPosition();
    }

    private void showViewedPosition() {
        Board shown = positions.get(viewIndex);
        board.setMovesEnabled(false);
        board.setBoard(shown);
        if (viewIndex > 0) {
            Move m = played.get(viewIndex - 1);
            board.setLastMove(m.from, m.to);
        }
        if (atLive()) {
            board.setMovesEnabled(!over && !engineThinking && humanTurn());
        } else {
            board.setPremoveSide(null);          // no premoves while looking back
        }
        syncPremoves();
        updateCaptured();
        updateNav();
        if (over) {
            if (atLive()) {
                if (lastHeadline != null) statusLabel.setText(lastHeadline);
            } else {
                statusLabel.setText("Looking back (move " + viewIndex + " of " + (positions.size() - 1) + ")");
            }
        } else {
            updateStatus();
        }
    }

    private String lastHeadline;

    private void updateNav() {
        boolean any = positions.size() > 1;
        firstButton.setEnabled(any && viewIndex > 0);
        prevButton.setEnabled(any && viewIndex > 0);
        nextButton.setEnabled(any && viewIndex < positions.size() - 1);
        lastButton.setEnabled(any && viewIndex < positions.size() - 1);
    }

    /* Premove time charge for the move being committed (-1: the real elapsed time). */
    private long premoveCharge = -1;

    /** Premoves are allowed for the human side while a game runs and Move Preferences allows them. */
    private void syncPremoves() {
        boolean on = MovePreferences.premovesEnabled() && !over && cfg != null && atLive();
        board.setPremoveSide(on ? Boolean.valueOf(humanWhite) : null);
    }

    /*
      After the computer's reply: plays the oldest queued premove if it is still legal (otherwise all
      premoves are dropped). The move costs 0 s (single premove) or 0.1 s (multiple premoves) on the clock.
     */
    private void runPremove() {
        if (over || !humanTurn() || !atLive()) return;
        if (!MovePreferences.premovesEnabled()) {
            board.clearPremoves();
            return;
        }
        Move pm = board.pollPremove();
        if (pm == null) return;
        Board cur = board.getBoard();
        Move legal = null;
        for (Move c : MoveGenerator.generateLegalMoves(cur)) {
            if (c.from == pm.from && c.to == pm.to && c.promotion == pm.promotion) {
                legal = c;
                break;
            }
        }
        if (legal == null) {
            board.clearPremoves();            // the opponent's move made it impossible
            return;
        }
        Board before = cur.copy();
        String san = ChessUtil.san(before, legal);
        board.playMove(legal);
        premoveCharge = MovePreferences.premoveCostMs();
        commit(legal, san, before, board.getBoard().copy(), true);
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
        side.add(Box.createVerticalStrut(6));

        JPanel nav = new JPanel(new GridLayout(1, 4, 6, 0));
        nav.setOpaque(false);
        for (UiKit.FlatButton b : new UiKit.FlatButton[]{firstButton, prevButton, nextButton, lastButton}) {
            b.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 14));
            b.setPreferredSize(new Dimension(60, 34));
            b.setToolTipText("Look through the game (arrow keys also work)");
            nav.add(b);
        }
        nav.setAlignmentX(Component.LEFT_ALIGNMENT);
        nav.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        side.add(nav);
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

        resultPanel.setBorder(new EmptyBorder(14, 18, 14, 14));
        JPanel resultTexts = new JPanel(new GridLayout(0, 1, 0, 6));
        resultTexts.setOpaque(false);
        resultTexts.add(resultTitle);
        resultTexts.add(resultDetail);
        resultPanel.add(resultTexts, BorderLayout.CENTER);
        analysisButton.setFont(UiKit.font(Font.BOLD, 14));
        analysisButton.setPreferredSize(new Dimension(138, 44));
        analysisButton.setToolTipText("Open this game on the Analysis Board with a game review");
        JPanel analysisHolder = new JPanel(new GridBagLayout());
        analysisHolder.setOpaque(false);
        analysisHolder.add(analysisButton);
        resultPanel.add(analysisHolder, BorderLayout.EAST);
        resultPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        resultPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        resultPanel.setVisible(false);
        side.add(resultPanel);
        side.add(Box.createVerticalStrut(8));

        JPanel gameButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        gameButtons.setOpaque(false);
        gameButtons.add(newGameButton);
        gameButtons.add(rematchButton);
        gameButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        gameButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        side.add(gameButtons);

        resignButton.addActionListener(e -> resign());
        drawButton.addActionListener(e -> offerDraw());
        takebackButton.addActionListener(e -> takeBack());
        flipButton.addActionListener(e -> {
            board.setFlipped(!board.isFlipped());
            updateBars();
        });
        newGameButton.addActionListener(e -> showDialogAndStart());
        rematchButton.addActionListener(e -> rematch());
        analysisButton.addActionListener(e -> {
            if (analysisHandler != null && !positions.isEmpty()) analysisHandler.accept(record());
        });
        setControlsEnabled(false);
        return side;
    }

    // starting a game

    // Opens the setup dialog and starts a game with the chosen settings. 
    // Called after the theme was changed in the Themes window. 
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
        startGame(config, null);
    }

    /*
      Rematch: the same opponent level, time control and variant (and the same starting position), with the
      colours swapped.
     */
    private void rematch() {
        if (cfg == null || positions.isEmpty()) return;
        if (!over) {
            int ok = JOptionPane.showConfirmDialog(this, "Abandon this game and play a rematch?", "Rematch",
                    JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) return;
        }
        PlayConfig next = new PlayConfig(cfg.level, humanWhite ? 1 : 0, cfg.unlimited, cfg.baseMs, cfg.incrementMs, cfg.variant);
        startGame(next, positions.get(0).copy());
    }

    /* The game so far, for the Analysis Board. */
    private GameRecord record() {
        String human = "You";
        String comp = "Computer - " + EngineLevel.title(cfg.level);
        // the game review works for standard chess and Chess960
        boolean standard = cfg.variant == PlayVariant.STANDARD || cfg.variant == PlayVariant.CHESS960;
        return new GameRecord(positions.get(0).copy(), new ArrayList<>(played), new ArrayList<>(sanList),
                humanWhite ? human : comp, humanWhite ? comp : human, over ? resultPgn : "*",
                cfg.variant.label, standard);
    }

    private void startGame(PlayConfig config, Board presetStart) {
        generation++;
        stopEngine();
        cfg = config;
        humanWhite = config.side == 0 || (config.side == 2 && rnd.nextBoolean());
        engine = new ChessEngine();
        if (config.level < 8) engine.setThreads(1);
        Board start = presetStart != null ? presetStart : config.variant.newBoard(rnd);
        positions.clear();
        positions.add(start.copy());
        irreversible.clear();
        played.clear();
        sanList.clear();
        viewIndex = 0;
        resultPgn = "*";
        lastHeadline = null;
        rematchButton.setEnabled(true);
        clock = new GameClock(config.unlimited, config.baseMs, config.incrementMs);
        over = false;
        engineThinking = false;

        board.setFlipped(!humanWhite);
        board.setBoard(start);
        syncPremoves();
        moves.clear();
        updateNav();
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

    /** Stops timers and the engine (the screen is being left). */
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
        commit(m, ChessUtil.san(before, m), before, after, true);
    }

    /* Records a move that has already been played on the board. */
    private void commit(Move m, String san, Board before, Board after, boolean byHuman) {
        final boolean following = atLive();        // the player is looking at the live position
        char piece = before.getPiece(m.from);
        boolean pawn = Character.toUpperCase(piece) == 'P';
        boolean capture = before.getPiece(m.to) != '.' || (pawn && (m.from & 7) != (m.to & 7));
        positions.add(after);
        irreversible.add(pawn || capture);
        played.add(m);
        sanList.add(san);
        moves.add(san);
        if (following) viewIndex = positions.size() - 1;     // otherwise the player keeps looking at the old position
        moves.select(viewIndex);
        updateNav();

        int ply = positions.size() - 1;
        int mover = before.isWhiteToMove() ? 0 : 1;
        if (!cfg.unlimited) {
            long charge = premoveCharge;
            if (ply == 2) clock.start(0);           // the clocks start once both sides have moved
            else if (ply > 2) clock.moved(mover, charge);
        }
        premoveCharge = -1;

        takebackButton.setEnabled(true);
        updateBars();
        if (checkGameEnd(after, mover)) return;
        updateStatus();

        if (byHuman) {
            engineMove();
        } else {
            engineThinking = false;
            if (atLive()) {
                board.setMovesEnabled(true);
                runPremove();
            }
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
                    Board before = positions.get(positions.size() - 1).copy();
                    String san = ChessUtil.san(before, m);
                    Board after = before.copy();
                    MoveGenerator.makeMove(after, m);
                    if (atLive()) board.playMove(m);       // when looking at an older position the board is left alone
                    commit(m, san, before, after, false);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    engineThinking = false;
                    if (atLive()) board.setMovesEnabled(true);
                }
            }
        };
        worker.execute();
    }

    /**
     * How long the engine may think. A small share of its remaining time plus most of the increment, never
     * more than 1/25 of the clock (the full engine may extend a search to about twice the request), and a
     * very small budget when it is nearly out of time.
     */
    private long thinkTime(long remaining) {
        if (cfg.unlimited) return 4000;
        long inc = cfg.incrementMs;
        long base = remaining / 30 + (inc * 7) / 10;
        long budget = Math.min(base, remaining / 25);
        if (remaining < 1500) budget = Math.max(10, remaining / 40);
        return Math.max(10, Math.min(budget, 20_000));
    }

    //  end of game

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
        resultPgn = result == 0 ? "1/2-1/2" : (result > 0) == humanWhite ? "1-0" : "0-1";
        lastHeadline = headline + " - " + detail;
        clock.stop();
        stopEngine();
        board.setMovesEnabled(false);
        syncPremoves();
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

    /* Takes back the player's last move (and the computer's reply). */
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
            played.remove(played.size() - 1);
            sanList.remove(sanList.size() - 1);
            moves.removeLast();
        }
        viewIndex = positions.size() - 1;
        moves.select(viewIndex);
        board.setBoard(positions.get(positions.size() - 1));
        if (!played.isEmpty()) {
            Move lm = played.get(played.size() - 1);
            board.setLastMove(lm.from, lm.to);
        }
        updateNav();
        if (positions.size() - 1 < 2 && !cfg.unlimited) {
            // back before the clocks started
            clock.stop();
        }
        takebackButton.setEnabled(positions.size() > 1);
        updateBars();
        updateStatus();
        if (humanTurn()) board.setMovesEnabled(true); else engineMove();
    }

    //  display

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
        if (!atLive()) {
            statusLabel.setText("Looking back (move " + viewIndex + " of " + (positions.size() - 1) + ") - go to the last move to play");
            return;
        }
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

    /* Shows the pieces each side has captured, and the material lead. */
    private void updateCaptured() {
        Board cur = positions.get(Math.min(viewIndex, positions.size() - 1));
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

    /** A digital clock face. */
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