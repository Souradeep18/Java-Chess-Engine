package com.chess.engine.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Random;

/*
  Coordinates trainer. "Find square": the name of a square is shown and you click it.
  "Name square": a square is highlighted and you type (or click) its name.
  Untimed or 30 seconds, from white's, black's or a random side.
 */
@SuppressWarnings("serial")
public class CoordinatesPanel extends JPanel {

    private static final Color ACCENT = UiKit.ORANGE;
    private static final long TIMED_MS = 30_000L;
    private static final String START_POS = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR";

    // options
    private boolean nameMode = true;
    private boolean timed = true;
    private int colorChoice = 1;                 // 0 white, 1 random, 2 black
    private final UiKit.Toggle showCoordinates = new UiKit.Toggle(false, this::optionsChanged);
    private final UiKit.Toggle everySquare = new UiKit.Toggle(false, this::optionsChanged);
    private final UiKit.Toggle showPieces = new UiKit.Toggle(true, this::optionsChanged);

    // widgets
    private final CoordBoard boardView = new CoordBoard();
    private final JPanel infoCard = UiKit.darkPanel(null);
    private final JPanel runCard = UiKit.darkPanel(null);
    private final JPanel rightCards = new JPanel(new CardLayout());
    private final JLabel targetLabel = UiKit.label(" ", Font.BOLD, 64, UiKit.BRIGHT);
    private final JLabel typedLabel = UiKit.label(" ", Font.BOLD, 40, UiKit.BRIGHT);
    private final JLabel scoreLabel = UiKit.label("0", Font.BOLD, 54, UiKit.GOOD);
    private final JLabel timeLabel = UiKit.label("0:30", Font.BOLD, 54, UiKit.BRIGHT);
    private final JLabel resultLabel = UiKit.label(" ", Font.PLAIN, 15, UiKit.TEXT);
    private final JLabel explain = new JLabel();
    private final JPanel nameKeys = new JPanel(new GridLayout(2, 8, 4, 4));
    private final UiKit.FlatButton startButton = new UiKit.FlatButton("START TRAINING", UiKit.BLUE);
    private final UiKit.FlatButton stopButton = new UiKit.FlatButton("Stop", UiKit.PANEL_HI);

    // run state
    private final Random rnd = new Random();
    private boolean running;
    private boolean whiteBottom = true;
    private int target = -1;
    private int score;
    private int mistakes;
    private char pendingFile = 0;
    private long endAt;
    private Timer clock;
    private Timer flashTimer;

    public CoordinatesPanel() {
        super(new BorderLayout(20, 0));
        setBackground(UiKit.BG);
        setBorder(new EmptyBorder(24, 30, 24, 30));
        setFocusable(true);

        add(buildOptions(), BorderLayout.WEST);
        add(new SquareHolder(boardView), BorderLayout.CENTER);
        add(buildRight(), BorderLayout.EAST);

        boardView.setOnClick(this::squareClicked);
        addKeyListener(new KeyAdapter() {
            @Override public void keyTyped(KeyEvent e) { typed(e.getKeyChar()); }
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && running) finish();
                if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) { pendingFile = 0; updateTyped(); }
            }
        });
        optionsChanged();
    }

    public void activate() {
        if (!running) refreshPreview();
        requestFocusInWindow();
    }

    public void deactivate() {
        if (running) finish();
    }

    //  layout

    private JPanel buildOptions() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(340, 600));

        UiKit.Segmented mode = new UiKit.Segmented(new String[]{"Find square", "Name square"}, 1, i -> {
            nameMode = i == 1;
            optionsChanged();
        }, ACCENT);
        UiKit.Segmented time = new UiKit.Segmented(new String[]{"∞", "0:30"}, 1, i -> timed = i == 1, ACCENT);
        UiKit.Segmented color = new UiKit.Segmented(new String[]{"♔ White", "♕ Random", "♚ Black"}, 1, i -> {
            colorChoice = i;
            optionsChanged();
        }, ACCENT);
        for (JComponent c : new JComponent[]{mode, time, color}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
            p.add(c);
            p.add(Box.createVerticalStrut(14));
        }
        p.add(toggleRow("Show coordinates", showCoordinates));
        p.add(toggleRow("Coordinates on every square", everySquare));
        p.add(toggleRow("Show pieces", showPieces));
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel toggleRow(String text, UiKit.Toggle t) {
        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setOpaque(false);
        row.add(t, BorderLayout.WEST);
        row.add(UiKit.label(text, Font.PLAIN, 17, UiKit.TEXT), BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        row.setBorder(new EmptyBorder(8, 4, 8, 4));
        return row;
    }

    private JPanel buildRight() {
        JPanel right = new JPanel(new BorderLayout(0, 14));
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(380, 600));

        // info card (idle)
        infoCard.setLayout(new BorderLayout());
        infoCard.setBorder(new EmptyBorder(22, 24, 22, 24));
        JLabel title = UiKit.label("Coordinates", Font.PLAIN, 36, UiKit.BRIGHT);
        infoCard.add(title, BorderLayout.NORTH);
        explain.setVerticalAlignment(SwingConstants.TOP);
        infoCard.add(explain, BorderLayout.CENTER);
        infoCard.add(resultLabel, BorderLayout.SOUTH);

        // run card
        runCard.setLayout(new BoxLayout(runCard, BoxLayout.Y_AXIS));
        runCard.setBorder(new EmptyBorder(18, 24, 18, 24));
        JPanel stats = new JPanel(new GridLayout(1, 2, 12, 0));
        stats.setOpaque(false);
        stats.add(statBox("SCORE", scoreLabel));
        stats.add(statBox("TIME", timeLabel));
        stats.setAlignmentX(Component.LEFT_ALIGNMENT);
        runCard.add(stats);
        runCard.add(Box.createVerticalStrut(16));
        targetLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        typedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        runCard.add(targetLabel);
        runCard.add(typedLabel);
        runCard.add(Box.createVerticalStrut(10));
        for (char f = 'a'; f <= 'h'; f++) nameKeys.add(keyButton(String.valueOf(f)));
        for (char r = '1'; r <= '8'; r++) nameKeys.add(keyButton(String.valueOf(r)));
        nameKeys.setOpaque(false);
        nameKeys.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameKeys.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        runCard.add(nameKeys);
        runCard.add(Box.createVerticalGlue());
        stopButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        stopButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        stopButton.addActionListener(e -> finish());
        runCard.add(stopButton);

        rightCards.setOpaque(false);
        rightCards.add(infoCard, "info");
        rightCards.add(runCard, "run");
        right.add(rightCards, BorderLayout.CENTER);

        startButton.setFont(UiKit.font(Font.BOLD, 22));
        startButton.setPreferredSize(new Dimension(300, 64));
        startButton.addActionListener(e -> start());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        south.setOpaque(false);
        south.add(startButton);
        right.add(south, BorderLayout.SOUTH);
        return right;
    }

    private JPanel statBox(String caption, JLabel value) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(UiKit.label(caption, Font.BOLD, 12, UiKit.TEXT_DIM), BorderLayout.NORTH);
        p.add(value, BorderLayout.CENTER);
        return p;
    }

    private JButton keyButton(String text) {
        UiKit.FlatButton b = new UiKit.FlatButton(text, UiKit.PANEL_HI);
        b.setBorder(new EmptyBorder(6, 0, 6, 0));
        b.setFocusable(false);
        b.addActionListener(e -> typed(text.charAt(0)));
        return b;
    }

    private void updateExplanation() {
        String mode = nameMode
                ? "<b>Name square</b><br>A square is highlighted on the board and you must enter its coordinate (e.g. \"e4\"). "
                  + "Type it on the keyboard or use the buttons."
                : "<b>Find square</b><br>The name of a square is shown (e.g. \"e4\") and you must click that square on the board.";
        explain.setText("<html><body style='width:265px;color:#bababa;font-family:SansSerif;font-size:13px'>"
                + "Knowing the chessboard coordinates is a very important skill for several reasons:"
                + "<ul style='margin-left:14px'>"
                + "<li>Most chess courses and exercises use the algebraic notation extensively.</li>"
                + "<li>It makes it easier to talk to your chess friends, since you both understand the 'language of chess'.</li>"
                + "<li>You can analyse a game more effectively if you can quickly recognise coordinates.</li></ul>"
                + mode + "<br><br>"
                + (timed ? "You have 30 seconds to correctly map as many squares as possible!"
                         : "There is no time limit: press Stop (or Esc) when you are done.")
                + "<br><br><span style='color:#8a8885'>Best (30 s): Find " + best(false, true) + ", Name " + best(true, true)
                + "</span></body></html>");
    }

    //  options / preview

    private void optionsChanged() {
        updateExplanation();
        refreshPreview();
    }

    private void refreshPreview() {
        boolean whiteAtBottom = running ? whiteBottom : colorChoice != 2;
        boardView.configure(whiteAtBottom, showPieces.isOn() ? START_POS : null,
                showCoordinates.isOn(), everySquare.isOn());
        if (!running) {
            boardView.setHighlight(-1, null);
        }
    }

    //  training

    private void start() {
        running = true;
        whiteBottom = colorChoice == 0 || (colorChoice == 1 && rnd.nextBoolean());
        score = 0;
        mistakes = 0;
        pendingFile = 0;
        scoreLabel.setText("0");
        timeLabel.setText(timed ? "0:30" : "∞");
        timeLabel.setForeground(UiKit.BRIGHT);
        resultLabel.setText(" ");
        ((CardLayout) rightCards.getLayout()).show(rightCards, "run");
        startButton.setVisible(false);
        nameKeys.setVisible(nameMode);
        refreshPreview();
        nextTarget();
        if (timed) {
            endAt = System.currentTimeMillis() + TIMED_MS;
            clock = new Timer(100, e -> tick());
            clock.start();
        }
        requestFocusInWindow();
    }

    private void tick() {
        long left = endAt - System.currentTimeMillis();
        if (left <= 0) {
            timeLabel.setText("0:00");
            finish();
            return;
        }
        long s = (left + 999) / 1000;
        timeLabel.setText("0:" + (s < 10 ? "0" : "") + s);
        timeLabel.setForeground(left < 6000 ? UiKit.BAD : UiKit.BRIGHT);
    }

    private void nextTarget() {
        int t;
        do {
            t = rnd.nextInt(64);
        } while (t == target);
        target = t;
        pendingFile = 0;
        String name = ChessUtil.squareName(t);
        if (nameMode) {
            targetLabel.setText(" ");
            boardView.setHighlight(t, null);
        } else {
            targetLabel.setText(name);
            boardView.setHighlight(-1, null);
        }
        updateTyped();
    }

    private void updateTyped() {
        typedLabel.setText(nameMode ? (pendingFile == 0 ? "_ _" : pendingFile + " _") : " ");
    }

    private void typed(char c) {
        if (!running || !nameMode) return;
        c = Character.toLowerCase(c);
        if (c >= 'a' && c <= 'h') {
            pendingFile = c;
            updateTyped();
        } else if (c >= '1' && c <= '8' && pendingFile != 0) {
            answer(ChessUtil.squareIndex("" + pendingFile + c));
        }
    }

    private void squareClicked(int square) {
        if (!running || nameMode) return;
        answer(square);
    }

    private void answer(int square) {
        boolean ok = square == target;
        if (ok) score++; else mistakes++;
        scoreLabel.setText(String.valueOf(score));
        int flashSquare = nameMode ? target : square;
        boardView.setHighlight(flashSquare, ok ? UiKit.GOOD : UiKit.BAD);
        if (flashTimer != null) flashTimer.stop();
        flashTimer = new Timer(180, e -> {
            if (running) nextTarget();
        });
        flashTimer.setRepeats(false);
        flashTimer.start();
        target = -2;      // ignore input during the flash
        if (!ok && !nameMode) {
            // keep showing what was asked until the flash is over
            return;
        }
    }

    private void finish() {
        if (!running) return;
        running = false;
        if (clock != null) clock.stop();
        if (flashTimer != null) flashTimer.stop();
        boolean record = score > best(nameMode, timed) && score > 0;
        if (record) PuzzleProfile.putInt(bestKey(nameMode, timed), score);
        resultLabel.setText("<html><body style='width:265px;color:#bababa'>Score: <b style='color:#ffffff'>" + score
                + "</b>   Mistakes: " + mistakes + (record ? "<br><b style='color:#6ab04c'>New best!</b>" : "")
                + "</body></html>");
        ((CardLayout) rightCards.getLayout()).show(rightCards, "info");
        startButton.setVisible(true);
        startButton.setText("START TRAINING");
        targetLabel.setText(" ");
        updateExplanation();
        refreshPreview();
    }

    private static String bestKey(boolean name, boolean timed) {
        return "coord.best." + (name ? "name" : "find") + (timed ? ".30" : ".inf");
    }

    private static int best(boolean name, boolean timed) {
        return PuzzleProfile.getInt(bestKey(name, timed), 0);
    }

    //  the board

    /** Board with optional pieces, edge or per-square coordinates, a highlighted square and click support. */
    private static final class CoordBoard extends JComponent {
        private boolean whiteAtBottom = true;
        private String placement;
        private boolean edgeCoords;
        private boolean everyCoords;
        private int highlight = -1;
        private Color highlightColor;
        private java.util.function.IntConsumer onClick;

        CoordBoard() {
            setPreferredSize(new Dimension(600, 600));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    int sq = squareAt(e.getX(), e.getY());
                    if (sq >= 0 && onClick != null) onClick.accept(sq);
                }
            });
        }

        void setOnClick(java.util.function.IntConsumer c) { onClick = c; }

        void configure(boolean whiteAtBottom, String placement, boolean edge, boolean every) {
            this.whiteAtBottom = whiteAtBottom;
            this.placement = placement;
            this.edgeCoords = edge;
            this.everyCoords = every;
            repaint();
        }

        void setHighlight(int sq, Color color) {
            this.highlight = sq;
            this.highlightColor = color;
            repaint();
        }

        private int squareAt(int x, int y) {
            int cell = Math.min(getWidth(), getHeight()) / 8;
            if (cell <= 0) return -1;
            int col = x / cell, row = y / cell;
            if (col < 0 || col > 7 || row < 0 || row > 7) return -1;
            int file = whiteAtBottom ? col : 7 - col;
            int rank = whiteAtBottom ? 7 - row : row;
            return rank * 8 + file;
        }

        private char pieceAt(int sq) {
            if (placement == null) return 0;
            int rank = sq >> 3, file = sq & 7;
            String[] rows = placement.split("/");
            String row = rows[7 - rank];
            int f = 0;
            for (char c : row.toCharArray()) {
                if (Character.isDigit(c)) f += c - '0';
                else {
                    if (f == file) return c;
                    f++;
                }
            }
            return 0;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int cell = Math.min(getWidth(), getHeight()) / 8;
            Color lightC = Themes.getBoard().light, darkC = Themes.getBoard().dark;
            Themes.PieceSet pieces = Themes.getPieces();
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    int file = whiteAtBottom ? col : 7 - col;
                    int rank = whiteAtBottom ? 7 - row : row;
                    int sq = rank * 8 + file;
                    boolean dark = (file + rank) % 2 == 0;
                    int x = col * cell, y = row * cell;
                    g2.setColor(dark ? darkC : lightC);
                    g2.fillRect(x, y, cell, cell);
                    if (sq == highlight) {
                        g2.setColor(highlightColor != null ? new Color(highlightColor.getRed(), highlightColor.getGreen(), highlightColor.getBlue(), 190)
                                : new Color(0xa8, 0xa0, 0x30, 210));
                        g2.fillRect(x, y, cell, cell);
                    }
                    char p = pieceAt(sq);
                    if (p != 0) pieces.paint(g2, p, x, y, cell);
                    g2.setFont(UiKit.font(Font.BOLD, Math.max(11, cell / 5)));
                    g2.setColor(dark ? lightC : darkC);
                    FontMetrics fm = g2.getFontMetrics();
                    if (everyCoords) {
                        String name = ChessUtil.squareName(sq);
                        g2.drawString(name, x + 4, y + fm.getAscent() + 2);
                    } else if (edgeCoords) {
                        if (row == 7) g2.drawString(String.valueOf((char) ('a' + file)), x + cell - fm.stringWidth("a") - 4, y + cell - 4);
                        if (col == 0) g2.drawString(String.valueOf(rank + 1), x + 4, y + fm.getAscent() + 2);
                    }
                }
            }
            g2.dispose();
        }
    }
}