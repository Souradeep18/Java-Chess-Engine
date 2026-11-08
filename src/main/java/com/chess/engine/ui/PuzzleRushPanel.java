package com.chess.engine.ui;

import com.chess.engine.engine.Board;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/*
  Puzzle Rush: solve as many puzzles as possible. 3 minutes, 5 minutes, or survival (no clock).
  Every solved puzzle is one point, three wrong answers end the run, and the puzzles get harder as the score rises.
 */
@SuppressWarnings("serial")
public class PuzzleRushPanel extends JPanel implements PuzzleSession.Listener {

    private static final String START = "start";
    private static final String RUN = "run";
    private static final String[] MODE_KEY = {"3min", "5min", "survival"};
    private static final String[] MODE_NAME = {"3 min", "5 min", "Unlimited"};
    private static final String[] MODE_ICON = {"⚡", "⏱", "⚔"};
    private static final long[] MODE_TIME = {180_000L, 300_000L, 0L};
    private static final int MAX_STRIKES = 3;

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    // start screen
    private final ModeCard[] modeCards = new ModeCard[3];
    private int mode = 1;
    private final JLabel bestLabel = UiKit.label(" ", Font.PLAIN, 14, UiKit.TEXT);

    // run screen
    private final ChessBoardPanel board = new ChessBoardPanel(new Board());
    private final PuzzleSession session = new PuzzleSession(board, this);
    private final JLabel timeLabel = UiKit.label("5:00", Font.BOLD, 54, UiKit.BRIGHT);
    private final JLabel scoreLabel = UiKit.label("0", Font.BOLD, 54, UiKit.GOOD);
    private final JLabel modeLabel = UiKit.label(" ", Font.BOLD, 14, UiKit.TEXT_DIM);
    private final JLabel promptTitle = UiKit.label(" ", Font.BOLD, 20, UiKit.BRIGHT);
    private final JLabel promptDetail = UiKit.label(" ", Font.PLAIN, 14, UiKit.TEXT);
    private final JLabel levelLabel = UiKit.label(" ", Font.PLAIN, 12, UiKit.TEXT_DIM);
    private final StrikeBar strikeBar = new StrikeBar();
    private final JPanel resultPanel = UiKit.darkPanel(new GridLayout(0, 1, 0, 8));
    private final JLabel resultTitle = UiKit.label(" ", Font.BOLD, 24, UiKit.BRIGHT);
    private final JLabel resultDetail = UiKit.label(" ", Font.PLAIN, 15, UiKit.TEXT);
    private final UiKit.FlatButton abortButton = new UiKit.FlatButton("Abort run", UiKit.PANEL_HI);

    private final Random rnd = new Random();
    private final Set<Integer> seen = new HashSet<>();
    private Timer clockTimer;
    private Timer nextTimer;
    private boolean running;
    private int score;
    private int strikes;
    private long remainingMs;
    private long lastTick;

    public PuzzleRushPanel() {
        super(new BorderLayout());
        setBackground(UiKit.BG);
        root.setOpaque(false);
        root.add(buildStart(), START);
        root.add(buildRun(), RUN);
        add(root, BorderLayout.CENTER);
        refreshBest();
    }

    /* Called when the screen is shown: returns to the start card unless a run is in progress. 
     Called after the theme was changed in the Themes window. */
    public void applyTheme() {
        board.applyTheme();
    }

    public void activate() {
        if (!running) {
            cards.show(root, START);
            refreshBest();
        }
    }

    /* Called when the screen is hidden. */
    public void deactivate() {
        if (running) finishRun("Run aborted");
        session.stop();
    }

    //  start screen

    private JPanel buildStart() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setOpaque(false);

        JPanel box = UiKit.darkPanel(null);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(new EmptyBorder(26, 30, 26, 30));
        box.setPreferredSize(new Dimension(430, 520));

        JLabel title = UiKit.label("Puzzle Rush", Font.BOLD, 32, UiKit.BRIGHT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(title);
        JLabel sub = UiKit.label("<html><div style='width:340px'>Solve as many puzzles as you can. They get harder as your score rises. "
                + "Three wrong answers end the run"+" (or time runs out in the timed modes).</div></html>", Font.PLAIN, 14, UiKit.TEXT);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(Box.createVerticalStrut(8));
        box.add(sub);
        box.add(Box.createVerticalStrut(22));

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            modeCards[i] = new ModeCard(MODE_ICON[i], MODE_NAME[i], () -> selectMode(idx));
            modeCards[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            modeCards[i].setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
            box.add(modeCards[i]);
            box.add(Box.createVerticalStrut(10));
        }
        selectMode(1);

        bestLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(Box.createVerticalStrut(6));
        box.add(bestLabel);
        box.add(Box.createVerticalGlue());

        UiKit.FlatButton play = new UiKit.FlatButton("Play", UiKit.GREEN);
        play.setFont(UiKit.font(Font.BOLD, 22));
        play.setAlignmentX(Component.LEFT_ALIGNMENT);
        play.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        play.addActionListener(e -> startRun());
        box.add(play);

        outer.add(box);
        return outer;
    }

    private void selectMode(int i) {
        mode = i;
        for (int k = 0; k < modeCards.length; k++) modeCards[k].setSelected(k == i);
    }

    private void refreshBest() {
        bestLabel.setText("Best scores:   3 min " + PuzzleProfile.getBest(MODE_KEY[0])
                + "   -   5 min " + PuzzleProfile.getBest(MODE_KEY[1])
                + "   -   Unlimited " + PuzzleProfile.getBest(MODE_KEY[2]));
    }

    // run screen

    private JPanel buildRun() {
        JPanel run = new JPanel(new BorderLayout(18, 0));
        run.setOpaque(false);
        run.setBorder(new EmptyBorder(16, 20, 16, 20));
        run.add(new SquareHolder(board), BorderLayout.CENTER);

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setOpaque(false);
        side.setPreferredSize(new Dimension(360, 640));

        JPanel top = UiKit.darkPanel(new BorderLayout());
        top.setBorder(new EmptyBorder(14, 20, 14, 20));
        modeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel two = new JPanel(new GridLayout(1, 2, 10, 0));
        two.setOpaque(false);
        two.add(stat("TIME", timeLabel));
        two.add(stat("SCORE", scoreLabel));
        top.add(modeLabel, BorderLayout.NORTH);
        top.add(two, BorderLayout.CENTER);
        top.add(strikeBar, BorderLayout.SOUTH);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        side.add(top);
        side.add(Box.createVerticalStrut(10));

        JPanel prompt = UiKit.darkPanel(new GridLayout(0, 1, 0, 4));
        prompt.setBorder(new EmptyBorder(14, 20, 14, 20));
        prompt.add(promptTitle);
        prompt.add(promptDetail);
        prompt.add(levelLabel);
        prompt.setAlignmentX(Component.LEFT_ALIGNMENT);
        prompt.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        side.add(prompt);
        side.add(Box.createVerticalStrut(10));

        resultPanel.setBorder(new EmptyBorder(16, 20, 16, 20));
        resultPanel.add(resultTitle);
        resultPanel.add(resultDetail);
        UiKit.FlatButton again = new UiKit.FlatButton("Play again", UiKit.GREEN);
        again.addActionListener(e -> startRun());
        UiKit.FlatButton back = new UiKit.FlatButton("Change mode", UiKit.PANEL_HI);
        back.addActionListener(e -> {
            cards.show(root, START);
            refreshBest();
        });
        resultPanel.add(again);
        resultPanel.add(back);
        resultPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        resultPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 230));
        resultPanel.setVisible(false);
        side.add(resultPanel);

        side.add(Box.createVerticalGlue());
        abortButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        abortButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        abortButton.addActionListener(e -> {
            if (running) finishRun("Run aborted");
        });
        side.add(abortButton);

        run.add(side, BorderLayout.EAST);
        return run;
    }

    private static JPanel stat(String caption, JLabel value) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(UiKit.label(caption, Font.BOLD, 12, UiKit.TEXT_DIM), BorderLayout.NORTH);
        p.add(value, BorderLayout.CENTER);
        return p;
    }

    //the run

    private void startRun() {
        stopTimers();
        running = true;
        score = 0;
        strikes = 0;
        seen.clear();
        remainingMs = MODE_TIME[mode];
        lastTick = System.currentTimeMillis();
        scoreLabel.setText("0");
        scoreLabel.setForeground(UiKit.GOOD);
        strikeBar.setStrikes(0);
        modeLabel.setText("PUZZLE RUSH  -  " + MODE_NAME[mode].toUpperCase());
        resultPanel.setVisible(false);
        abortButton.setVisible(true);
        updateTimeLabel();
        cards.show(root, RUN);

        if (MODE_TIME[mode] > 0) {
            clockTimer = new Timer(100, e -> tick());
            clockTimer.start();
        }
        loadNext();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        remainingMs -= now - lastTick;
        lastTick = now;
        if (remainingMs <= 0) {
            remainingMs = 0;
            updateTimeLabel();
            finishRun("Time's up!");
            return;
        }
        updateTimeLabel();
    }

    private void updateTimeLabel() {
        if (MODE_TIME[mode] == 0) {
            timeLabel.setText("∞");
            return;
        }
        long s = (remainingMs + 999) / 1000;
        timeLabel.setText((s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60));
        timeLabel.setForeground(remainingMs < 15_000 ? UiKit.BAD : UiKit.BRIGHT);
    }

    /* Difficulty ramps with the score: 650 at the start, +55 per solved puzzle. */
    private int targetRating() {
        return Math.min(2300, 650 + score * 55);
    }

    private void loadNext() {
        if (!running) return;
        Puzzle p = PuzzleLibrary.pick(PuzzleLibrary.MIXED, targetRating(), seen, rnd);
        if (p == null) {
            finishRun("No puzzles available");
            return;
        }
        seen.add(p.id);
        levelLabel.setText("Puzzle " + (score + strikes + 1) + "  -  difficulty about " + targetRating());
        session.start(p);
    }

    private void finishRun(String title) {
        running = false;
        stopTimers();
        session.stop();
        board.setMovesEnabled(false);
        boolean record = PuzzleProfile.submitBest(MODE_KEY[mode], score);
        resultTitle.setText(title);
        resultDetail.setText("<html>Score: <b>" + score + "</b><br>"
                + (record ? "New best for " + MODE_NAME[mode] + "!" : "Best for " + MODE_NAME[mode] + ": "
                + PuzzleProfile.getBest(MODE_KEY[mode])) + "</html>");
        resultPanel.setVisible(true);
        abortButton.setVisible(false);
        promptTitle.setText("Run over");
        promptDetail.setText(" ");
        refreshBest();
    }

    private void stopTimers() {
        if (clockTimer != null) {
            clockTimer.stop();
            clockTimer = null;
        }
        if (nextTimer != null) {
            nextTimer.stop();
            nextTimer = null;
        }
    }

    private void nextAfter(int ms) {
        if (nextTimer != null) nextTimer.stop();
        nextTimer = new Timer(ms, e -> loadNext());
        nextTimer.setRepeats(false);
        nextTimer.start();
    }

    // --- PuzzleSession.Listener ---

    @Override
    public void onMove(String san, boolean whiteMoved) {
        // no move list in the rush
    }

    @Override
    public void onWrong(String san) {
        if (!running) return;
        strikes++;
        strikeBar.setStrikes(strikes);
        session.stop();
        board.setMovesEnabled(false);
        promptTitle.setText("Wrong!");
        promptDetail.setText(san + " is not the move.   Strike " + strikes + " of " + MAX_STRIKES);
        if (strikes >= MAX_STRIKES) {
            finishRun("Three strikes!");
        } else {
            nextAfter(900);
        }
    }

    @Override
    public void onFinished(boolean clean, boolean solutionShown) {
        if (!running) return;
        score++;
        scoreLabel.setText(String.valueOf(score));
        promptTitle.setText("Correct!");
        promptDetail.setText("+1 point");
        nextAfter(350);
    }

    @Override
    public void onPrompt(String title, String detail) {
        if (!running) return;
        promptTitle.setText(title);
        promptDetail.setText(detail);
    }

    //  small widgets

    /* One selectable mode on the start screen. */
    private static final class ModeCard extends JComponent {
        private final String icon;
        private final String name;
        private boolean selected;
        private boolean hover;

        ModeCard(String icon, String name, Runnable onClick) {
            this.icon = icon;
            this.name = name;
            setPreferredSize(new Dimension(300, 60));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { onClick.run(); }
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        void setSelected(boolean s) { selected = s; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(hover ? new Color(0x3d, 0x3b, 0x38) : new Color(0x33, 0x31, 0x2e));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            if (selected) {
                g2.setColor(UiKit.GREEN_HI);
                g2.setStroke(new BasicStroke(2.5f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12);
            }
            g2.setColor(UiKit.BRIGHT);
            g2.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 26));
            g2.drawString(icon, 22, getHeight() / 2 + 9);
            g2.setFont(UiKit.font(Font.BOLD, 20));
            g2.drawString(name, 70, getHeight() / 2 + 7);
            g2.dispose();
        }
    }

    /* Three boxes that fill with a red cross for every strike. */
    private static final class StrikeBar extends JComponent {
        private int strikes;

        StrikeBar() { setPreferredSize(new Dimension(200, 44)); }

        void setStrikes(int s) { strikes = s; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = 0; i < MAX_STRIKES; i++) {
                int x = i * 52, y = 6, s = 36;
                g2.setColor(new Color(0x3a, 0x38, 0x35));
                g2.fillRoundRect(x, y, s, s, 8, 8);
                if (i < strikes) {
                    g2.setColor(UiKit.BAD);
                    g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(x + 9, y + 9, x + s - 9, y + s - 9);
                    g2.drawLine(x + 9, y + s - 9, x + s - 9, y + 9);
                }
            }
            g2.dispose();
        }
    }
}