package com.chess.engine.ui;

import com.chess.engine.engine.Board;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/*
  Rated puzzles. The player has points (starting at 1000). Puzzles get harder as the points rise, and the
  Easy / Normal / Hard sets give and take different amounts:
  Easy gives little and takes a lot, Normal sits in the middle, Hard gives a lot and takes little.
 */
@SuppressWarnings("serial")
public class PuzzlePanel extends JPanel implements PuzzleSession.Listener {

    private enum Difficulty {
        EASY("Easy", +6, -14, -150),
        NORMAL("Normal", +12, -9, 0),
        HARD("Hard", +40, -6, +150);

        final String label;
        final int win;
        final int loss;
        final int ratingOffset;

        Difficulty(String label, int win, int loss, int ratingOffset) {
            this.label = label;
            this.win = win;
            this.loss = loss;
            this.ratingOffset = ratingOffset;
        }
    }

    private final ChessBoardPanel board = new ChessBoardPanel(new Board());
    private final PuzzleSession session = new PuzzleSession(board, this);
    private final Random rnd = new Random();
    private final Set<Integer> seen = new HashSet<>();

    private final JLabel pointsLabel = UiKit.label("1000", Font.BOLD, 40, UiKit.BRIGHT);
    private final JLabel deltaLabel = UiKit.label(" ", Font.BOLD, 16, UiKit.GOOD);
    private final JLabel rulesLabel = UiKit.label(" ", Font.PLAIN, 12, UiKit.TEXT_DIM);
    private final JLabel promptTitle = UiKit.label("Loading...", Font.BOLD, 22, UiKit.BRIGHT);
    private final JLabel promptDetail = UiKit.label(" ", Font.PLAIN, 14, UiKit.TEXT);
    private final JLabel infoLabel = UiKit.label(" ", Font.PLAIN, 12, UiKit.TEXT_DIM);
    private final PromptIcon promptIcon = new PromptIcon();
    private final MoveListView moves = new MoveListView();
    private final JComboBox<String> themeBox = new JComboBox<>();
    private final UiKit.Segmented difficultyBar;
    private final JButton hintButton = linkButton("GET A HINT");
    private final JButton solutionButton = linkButton("VIEW THE SOLUTION");
    private final UiKit.FlatButton nextButton = new UiKit.FlatButton("NEXT PUZZLE", UiKit.GREEN);

    private Difficulty difficulty = Difficulty.NORMAL;
    private List<String> themeKeys;
    private int points;
    private boolean penaltyApplied;
    private boolean started;

    public PuzzlePanel() {
        super(new BorderLayout(18, 0));
        setBackground(UiKit.BG);
        setBorder(new EmptyBorder(16, 20, 16, 20));

        board.setPreferredSize(new Dimension(640, 640));
        add(new SquareHolder(board), BorderLayout.CENTER);

        difficultyBar = new UiKit.Segmented(new String[]{"Easy", "Normal", "Hard"}, 1, i -> {
            difficulty = Difficulty.values()[i];
            updateRules();
            loadNext();
        });

        add(buildSide(), BorderLayout.EAST);
        points = PuzzleProfile.getPoints();
        updatePoints(0);
        updateRules();
    }

    // Called when the screen is shown.
    // Called after the theme was changed in the Themes window.
    public void applyTheme() {
        board.applyTheme();
    }

    public void activate() {
        if (!started) {
            themeKeys = PuzzleLibrary.themes(8);
            for (String t : themeKeys) {
                themeBox.addItem(PuzzleLibrary.label(t) + "  (" + PuzzleLibrary.count(t) + ")");
            }
            started = true;
            loadNext();
        }
    }

    private JPanel buildSide() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setOpaque(false);
        side.setPreferredSize(new Dimension(380, 640));

        JPanel head = UiKit.darkPanel(new BorderLayout());
        head.setBorder(new EmptyBorder(14, 18, 14, 18));
        JPanel pts = new JPanel(new BorderLayout());
        pts.setOpaque(false);
        pts.add(UiKit.label("PUZZLE POINTS", Font.BOLD, 12, UiKit.TEXT_DIM), BorderLayout.NORTH);
        pts.add(pointsLabel, BorderLayout.CENTER);
        head.add(pts, BorderLayout.WEST);
        head.add(deltaLabel, BorderLayout.EAST);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        side.add(head);
        side.add(Box.createVerticalStrut(8));

        JPanel setup = UiKit.darkPanel(new GridLayout(0, 1, 0, 6));
        setup.setBorder(new EmptyBorder(12, 18, 12, 18));
        setup.add(difficultyBar);
        setup.add(rulesLabel);
        themeBox.setFont(UiKit.font(Font.PLAIN, 14));
        PuzzleLibrary.preload();     // the puzzles load in the background; the theme list is filled when the screen opens
        themeBox.addActionListener(e -> {
            if (started) loadNext();
        });
        setup.add(themeBox);
        setup.setAlignmentX(Component.LEFT_ALIGNMENT);
        setup.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        side.add(setup);
        side.add(Box.createVerticalStrut(8));

        moves.setAlignmentX(Component.LEFT_ALIGNMENT);
        moves.setPreferredSize(new Dimension(380, 200));
        side.add(moves);
        side.add(Box.createVerticalStrut(8));

        JPanel prompt = UiKit.darkPanel(new BorderLayout(14, 0));
        prompt.setBorder(new EmptyBorder(14, 18, 14, 18));
        prompt.add(promptIcon, BorderLayout.WEST);
        JPanel texts = new JPanel(new GridLayout(0, 1, 0, 2));
        texts.setOpaque(false);
        texts.add(promptTitle);
        texts.add(promptDetail);
        texts.add(infoLabel);
        prompt.add(texts, BorderLayout.CENTER);
        prompt.setAlignmentX(Component.LEFT_ALIGNMENT);
        prompt.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        side.add(prompt);
        side.add(Box.createVerticalStrut(8));

        JPanel links = UiKit.darkPanel(new FlowLayout(FlowLayout.CENTER, 24, 8));
        links.add(hintButton);
        links.add(solutionButton);
        links.add(nextButton);
        links.setAlignmentX(Component.LEFT_ALIGNMENT);
        links.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        side.add(links);
        nextButton.setVisible(false);

        hintButton.addActionListener(e -> session.hint());
        solutionButton.addActionListener(e -> {
            if (!penaltyApplied && !session.isFinished()) applyPenalty();
            session.showSolution();
        });
        nextButton.addActionListener(e -> loadNext());
        return side;
    }

    private static JButton linkButton(String text) {
        JButton b = new JButton(text);
        b.setFont(UiKit.font(Font.BOLD, 13));
        b.setForeground(UiKit.BLUE);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void updateRules() {
        rulesLabel.setText(difficulty.label + ": correct " + signed(difficulty.win) + " points, wrong "
                + signed(difficulty.loss) + " points  -  puzzles near " + (points + difficulty.ratingOffset));
    }

    private static String signed(int v) {
        return v > 0 ? "+" + v : String.valueOf(v);
    }

    private void updatePoints(int delta) {
        pointsLabel.setText(String.valueOf(points));
        if (delta == 0) {
            deltaLabel.setText(" ");
        } else {
            deltaLabel.setText(signed(delta));
            deltaLabel.setForeground(delta > 0 ? UiKit.GOOD : UiKit.BAD);
        }
    }

    private void applyPenalty() {
        penaltyApplied = true;
        int before = points;
        points = Math.max(PuzzleProfile.MIN_POINTS, points + difficulty.loss);
        PuzzleProfile.setPoints(points);
        updatePoints(points - before);
        updateRules();
    }

    private void loadNext() {
        int idx = Math.max(0, themeBox.getSelectedIndex());
        String theme = themeKeys.get(Math.min(idx, themeKeys.size() - 1));
        int target = points + difficulty.ratingOffset;
        Puzzle p = PuzzleLibrary.pick(theme, target, seen, rnd);
        if (p == null) {
            promptTitle.setText("No puzzles");
            promptDetail.setText("The puzzle list is empty.");
            return;
        }
        seen.add(p.id);
        penaltyApplied = false;
        moves.reset(p.whiteToSolve(), Integer.parseInt(p.fen.split(" ")[5]));
        nextButton.setVisible(false);
        hintButton.setVisible(true);
        solutionButton.setVisible(true);
        infoLabel.setText(" ");
        promptIcon.setState(PromptIcon.TURN, p.whiteToSolve());
        deltaLabel.setText(" ");
        session.start(p);
    }

    // PuzzleSession.Listener

    @Override
    public void onMove(String san, boolean whiteMoved) {
        moves.add(san);
    }

    @Override
    public void onWrong(String san) {
        if (!penaltyApplied) applyPenalty();
        promptIcon.setState(PromptIcon.WRONG, session.current().whiteToSolve());
        promptTitle.setText("That's not it");
        promptDetail.setText(san + " is not the best move. Try again.");
    }

    @Override
    public void onFinished(boolean clean, boolean solutionShown) {
        Puzzle p = session.current();
        hintButton.setVisible(false);
        solutionButton.setVisible(false);
        nextButton.setVisible(true);
        infoLabel.setText("Puzzle rating " + p.rating + "  -  " + themeText(p));
        if (clean || (!penaltyApplied && !solutionShown)) {
            // solved: full points, half when a hint was used
            int win = session.hintWasUsed() ? Math.max(1, difficulty.win / 2) : difficulty.win;
            int before = points;
            points += win;
            PuzzleProfile.setPoints(points);
            updatePoints(points - before);
            updateRules();
            promptIcon.setState(PromptIcon.OK, p.whiteToSolve());
            promptTitle.setText("Success!");
            promptDetail.setText(session.hintWasUsed() ? "Solved with a hint (half points)." : "Puzzle solved. Keep going!");
        } else {
            promptIcon.setState(PromptIcon.WRONG, p.whiteToSolve());
            promptTitle.setText(solutionShown ? "Solution shown" : "Solved after a mistake");
            promptDetail.setText("No points for this one.");
        }
    }

    @Override
    public void onPrompt(String title, String detail) {
        promptIcon.setState(PromptIcon.TURN, session.current().whiteToSolve());
        promptTitle.setText(title);
        promptDetail.setText(detail);
    }

    private static String themeText(Puzzle p) {
        StringBuilder sb = new StringBuilder();
        for (String t : p.themes) {
            if (t.equals("mateIn1") || t.equals("mateIn2") || t.equals("mateIn3") || t.equals("mate")) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(PuzzleLibrary.label(t));
        }
        if (p.themes.contains("mate")) {
            String mate = p.themes.contains("mateIn1") ? "Mate in 1" : p.themes.contains("mateIn2") ? "Mate in 2" : "Mate in 3+";
            sb.insert(0, sb.length() > 0 ? mate + ", " : mate);
        }
        return sb.toString();
    }

    // The round king / tick / cross icon beside the prompt. 
    static final class PromptIcon extends JComponent {
        static final int TURN = 0, OK = 1, WRONG = 2;
        private int state = TURN;
        private boolean white = true;

        PromptIcon() { setPreferredSize(new Dimension(56, 56)); }

        void setState(int state, boolean white) {
            this.state = state;
            this.white = white;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (state == TURN) {
                Themes.getPieces().paint(g2, white ? 'K' : 'k', 0, 0, Math.min(w, h));
            } else {
                g2.setColor(state == OK ? UiKit.GOOD : UiKit.BAD);
                g2.fillOval(4, 4, w - 8, h - 8);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (state == OK) {
                    g2.drawLine(w / 2 - 12, h / 2, w / 2 - 3, h / 2 + 9);
                    g2.drawLine(w / 2 - 3, h / 2 + 9, w / 2 + 13, h / 2 - 9);
                } else {
                    g2.drawLine(w / 2 - 10, h / 2 - 10, w / 2 + 10, h / 2 + 10);
                    g2.drawLine(w / 2 - 10, h / 2 + 10, w / 2 + 10, h / 2 - 10);
                }
            }
            g2.dispose();
        }
    }
}