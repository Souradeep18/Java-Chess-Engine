package com.chess.engine.ui;

import com.chess.engine.analysis.GameAnlyser;
import com.chess.engine.analysis.GameAnlyser.GameReview;
import com.chess.engine.analysis.GameAnlyser.MoveClass;
import com.chess.engine.analysis.GameAnlyser.MoveReview;
import com.chess.engine.analysis.PgnGame;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Path2D;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * The game review window: the advantage graph (white area = how well White stands, one dot per move coloured
 * by its quality), the accuracy of both players and the number of moves of every kind.
 * The analysis runs in the background and the window fills up while it goes; clicking the graph shows that
 * position on the board.
 */
@SuppressWarnings("serial")
public class GameAnalysisDialog extends JDialog {

    // colours of the move kinds (also used by the legend)
    private static final Map<MoveClass, Color> COLORS = new EnumMap<>(MoveClass.class);

    static {
        COLORS.put(MoveClass.BRILLIANT, new Color(38, 194, 175));    // turquoise
        COLORS.put(MoveClass.GREAT, new Color(52, 96, 176));         // dark blue
        COLORS.put(MoveClass.BEST, new Color(104, 164, 62));         // green
        COLORS.put(MoveClass.EXCELLENT, new Color(104, 164, 62));    // green
        COLORS.put(MoveClass.GOOD, new Color(170, 214, 128));        // light green
        COLORS.put(MoveClass.BOOK, new Color(160, 112, 78));         // brown
        COLORS.put(MoveClass.INACCURACY, new Color(247, 220, 110));  // light yellow
        COLORS.put(MoveClass.MISTAKE, new Color(240, 148, 52));      // orange
        COLORS.put(MoveClass.MISS, new Color(242, 134, 134));        // light red
        COLORS.put(MoveClass.BLUNDER, new Color(214, 48, 48));       // red
    }

    public static Color colorOf(MoveClass c) {
        return COLORS.get(c);
    }

    public enum ReviewMode {
        BOTH("Review as both"),
        WHITE("Review as White"),
        BLACK("Review as Black");

        public final String label;
        ReviewMode(String label) {
            this.label = label;
        }
    }

    private final GameAnlyser analyser;
    private final PgnGame game;
    private final GraphPanel graph = new GraphPanel();
    private final JLabel whiteAccuracy = accuracyBox(true);
    private final JLabel blackAccuracy = accuracyBox(false);
    private final JLabel[][] counts = new JLabel[MoveClass.values().length][2];
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JLabel status = new JLabel("Starting the engine...");
    private SwingWorker<Void, GameReview> worker;
    private ReviewMode currentReviewMode = ReviewMode.BOTH;

    /**
     * @param onSelectPly called with the number of moves played (0 = start) when a point of the graph is clicked
     */
    public GameAnalysisDialog(Frame owner, PgnGame game, IntConsumer onSelectPly) {
        super(owner, "Game Analysis", false);
        this.game = game;
        this.analyser = new GameAnlyser();

        graph.setOnSelect(onSelectPly);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(new EmptyBorder(12, 14, 12, 14));
        content.add(graph, BorderLayout.NORTH);
        content.add(buildSummary(), BorderLayout.CENTER);

        progress.setStringPainted(true);
        JButton close = new JButton("Close");
        close.addActionListener(e -> closeWindow());
        JPanel south = new JPanel(new BorderLayout(10, 0));
        JPanel progressBox = new JPanel(new BorderLayout(0, 3));
        progressBox.add(status, BorderLayout.NORTH);
        progressBox.add(progress, BorderLayout.CENTER);
        south.add(progressBox, BorderLayout.CENTER);
        south.add(close, BorderLayout.EAST);
        content.add(south, BorderLayout.SOUTH);

        setContentPane(content);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeWindow();
            }
        });

        pack();
        setMinimumSize(new Dimension(560, 560));
        setLocationRelativeTo(owner);
    }

    // ------------------------------------------------------------------------------------------
    //  layout
    // ------------------------------------------------------------------------------------------

    private static JLabel accuracyBox(boolean white) {
        JLabel l = new JLabel("-", SwingConstants.CENTER);
        l.setOpaque(true);
        l.setFont(new Font("SansSerif", Font.BOLD, 22));
        l.setBackground(white ? Color.WHITE : new Color(48, 46, 44));
        l.setForeground(white ? new Color(30, 30, 30) : Color.WHITE);
        l.setBorder(BorderFactory.createLineBorder(new Color(150, 150, 150)));
        l.setPreferredSize(new Dimension(96, 42));
        return l;
    }

    private JComponent buildSummary() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        // header: players with review mode dropdown button
        c.gridy = 0;
        c.gridx = 0;
        c.weightx = 0.2;
        
        JPanel playersHeaderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        playersHeaderPanel.setOpaque(false);
        playersHeaderPanel.add(rowLabel("Players"));

        JButton filterBtn = new JButton("Review as both ▾");
        filterBtn.setFont(filterBtn.getFont().deriveFont(Font.PLAIN, 11f));
        filterBtn.setMargin(new Insets(2, 6, 2, 6));

        JPopupMenu popup = new JPopupMenu();
        for (ReviewMode mode : ReviewMode.values()) {
            JMenuItem item = new JMenuItem(mode.label);
            item.addActionListener(e -> {
                currentReviewMode = mode;
                filterBtn.setText(mode.label + " ▾");
                graph.setReviewMode(mode);
            });
            popup.add(item);
        }

        filterBtn.addActionListener(e -> {
            popup.show(filterBtn, 0, filterBtn.getHeight());
        });

        playersHeaderPanel.add(filterBtn);
        p.add(playersHeaderPanel, c);

        c.gridx = 1;
        c.weightx = 0.4;
        p.add(playerName(game.getWhiteName(), game.getTag("WhiteElo", null)), c);
        c.gridx = 2;
        p.add(playerName(game.getBlackName(), game.getTag("BlackElo", null)), c);

        // accuracy
        c.gridy = 1;
        c.gridx = 0;
        p.add(rowLabel("Accuracy"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.NONE;
        p.add(whiteAccuracy, c);
        c.gridx = 2;
        p.add(blackAccuracy, c);
        c.fill = GridBagConstraints.HORIZONTAL;

        // the kinds of moves, best ones first
        MoveClass[] order = {MoveClass.BRILLIANT, MoveClass.GREAT, MoveClass.BEST, MoveClass.EXCELLENT,
                MoveClass.GOOD, MoveClass.BOOK, MoveClass.INACCURACY, MoveClass.MISTAKE, MoveClass.MISS,
                MoveClass.BLUNDER};
        int row = 2;
        for (MoveClass mc : order) {
            c.gridy = row++;
            c.gridx = 0;
            p.add(legend(mc), c);

            for (int side = 0; side < 2; side++) {
                JLabel n = new JLabel("0", SwingConstants.CENTER);
                n.setFont(n.getFont().deriveFont(Font.BOLD, 13f));
                counts[mc.ordinal()][side] = n;
                c.gridx = 1 + side;
                p.add(n, c);
            }
        }

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(p, BorderLayout.NORTH);
        return wrap;
    }

    private static JLabel rowLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        return l;
    }

    private static JLabel playerName(String name, String elo) {
        JLabel l = new JLabel(elo == null ? name : name + " (" + elo + ")", SwingConstants.CENTER);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 14f));
        return l;
    }

    private static JComponent legend(MoveClass mc) {
        JLabel l = new JLabel(mc.label);
        l.setIcon(new Icon() {
            @Override
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(COLORS.get(mc));
                g2.fillOval(x, y + 1, 12, 12);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 14;
            }

            @Override
            public int getIconHeight() {
                return 14;
            }
        });
        l.setIconTextGap(8);
        return l;
    }

    //  running the analysis

    /* Starts the background analysis (call once, after the dialog is shown). */
    public void start() {
        graph.setTotalMoves(game.getMoves().size());
        status.setText("Analysing the game...");

        worker = new SwingWorker<Void, GameReview>() {
            @Override
            protected Void doInBackground() {
                analyser.review(game.getStartBoard(), game.getMoves(), game.getSans(),
                        (done, total, partial) -> {
                            publish(partial.copy());
                            setProgress(Math.min(100, done * 100 / Math.max(1, total)));
                        });
                return null;
            }

            @Override
            protected void process(List<GameReview> chunks) {
                if (!chunks.isEmpty()) {
                    show(chunks.get(chunks.size() - 1), false);
                }
            }

            @Override
            protected void done() {
                status.setText(isCancelled() ? "Analysis stopped." : "Analysis complete - click the graph to jump to a move.");
                progress.setValue(isCancelled() ? progress.getValue() : 100);
            }
        };
        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                progress.setValue((Integer) evt.getNewValue());
            }
        });
        worker.execute();
    }

    private void show(GameReview review, boolean finished) {
        graph.setReview(review);

        boolean any = !review.moves.isEmpty();
        whiteAccuracy.setText(any ? String.format("%.1f", review.whiteAccuracy) : "-");
        blackAccuracy.setText(any ? String.format("%.1f", review.blackAccuracy) : "-");

        for (MoveClass mc : MoveClass.values()) {
            counts[mc.ordinal()][0].setText(String.valueOf(review.count(true, mc)));
            counts[mc.ordinal()][1].setText(String.valueOf(review.count(false, mc)));
        }

        status.setText("Analysing the game... move " + review.moves.size() + " of " + review.totalMoves);
    }

    private void closeWindow() {
        analyser.cancel();
        if (worker != null) {
            worker.cancel(false);
        }
        dispose();
    }

    //  the graph

    /* The advantage graph. Public so it can be reused (and rendered for tests). */
    public static final class GraphPanel extends JPanel {

        private static final Color BACKGROUND = new Color(52, 50, 47);
        private static final Color MIDDLE = new Color(118, 116, 112);

        private GameReview review;
        private int totalMoves = 1;
        private int hoverPly = -1;
        private IntConsumer onSelect;
        private ReviewMode reviewMode = ReviewMode.BOTH;

        public GraphPanel() {
            setPreferredSize(new Dimension(620, 150));
            setBackground(BACKGROUND);
            setToolTipText("");

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int ply = plyAt(e.getX());
                    if (ply != hoverPly) {
                        hoverPly = ply;
                        repaint();
                    }
                    setCursor(Cursor.getPredefinedCursor(ply >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverPly = -1;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    int ply = plyAt(e.getX());
                    if (ply >= 0 && onSelect != null) {
                        onSelect.accept(ply);
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        public void setOnSelect(IntConsumer onSelect) {
            this.onSelect = onSelect;
        }

        public void setTotalMoves(int n) {
            totalMoves = Math.max(1, n);
            repaint();
        }

        public void setReview(GameReview review) {
            this.review = review;
            this.totalMoves = Math.max(1, review.totalMoves);
            repaint();
        }

        public void setReviewMode(ReviewMode mode) {
            this.reviewMode = mode;
            repaint();
        }

        private int left() {
            return 8;
        }

        private int width() {
            return getWidth() - 16;
        }

        private double xOf(int ply) {
            return left() + (double) ply / totalMoves * width();
        }

        /* The position (number of moves played) under the mouse, or -1 when it is not analysed yet. */
        private int plyAt(int x) {
            if (review == null || review.evals.isEmpty()) {
                return -1;
            }
            int ply = (int) Math.round((x - left()) / (double) width() * totalMoves);
            ply = Math.max(0, Math.min(totalMoves, ply));
            return ply < review.evals.size() ? ply : -1;
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            int ply = plyAt(e.getX());
            if (ply < 0 || review == null) {
                return null;
            }
            if (ply == 0) {
                return "Start position";
            }
            if (ply - 1 >= review.moves.size()) {
                return null;
            }
            MoveReview m = review.moves.get(ply - 1);
            return m.label() + m.moveClass.symbol + " - " + m.moveClass.label + "  (" + evalText(m.evalAfter) + ")";
        }

        private static String evalText(int cp) {
            if (Math.abs(cp) >= 9000) {
                return (cp > 0 ? "+" : "-") + "M" + (10000 - Math.abs(cp));
            }
            return String.format("%+.2f", cp / 100.0);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int h = getHeight();
            int top = 6;
            int bottom = h - 6;
            int plotH = bottom - top;

            g2.setColor(BACKGROUND);
            g2.fillRoundRect(0, 0, getWidth(), h, 10, 10);

            // the white area: White's share of the winning chances, growing from the bottom
            if (review != null && !review.evals.isEmpty()) {
                Path2D.Double area = new Path2D.Double();
                int n = review.evals.size();
                area.moveTo(xOf(0), bottom);
                for (int i = 0; i < n; i++) {
                    double y = bottom - review.whiteShare(i) * plotH;
                    area.lineTo(xOf(i), y);
                }
                area.lineTo(xOf(n - 1), bottom);
                area.closePath();
                g2.setColor(Color.WHITE);
                g2.fill(area);
            }

            // the equal line
            g2.setColor(MIDDLE);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawLine(left(), top + plotH / 2, left() + width(), top + plotH / 2);

            // hovered position
            if (hoverPly >= 0) {
                g2.setColor(new Color(255, 255, 255, 90));
                int x = (int) Math.round(xOf(hoverPly));
                g2.drawLine(x, top, x, bottom);
            }

            // filtered dots per move
            if (review != null) {
                int lastWhiteBookPly = -1;
                int lastBlackBookPly = -1;
                for (MoveReview m : review.moves) {
                    if (m.moveClass == MoveClass.BOOK) {
                        if (m.white) {
                            lastWhiteBookPly = m.ply;
                        } else {
                            lastBlackBookPly = m.ply;
                        }
                    }
                }

                for (MoveReview m : review.moves) {
                    if (reviewMode == ReviewMode.WHITE && !m.white) continue;
                    if (reviewMode == ReviewMode.BLACK && m.white) continue;

                    if (m.moveClass == MoveClass.EXCELLENT || m.moveClass == MoveClass.BEST) {
                        continue;
                    }
                    if (m.moveClass == MoveClass.BOOK) {
                        if (m.white && m.ply != lastWhiteBookPly) continue;
                        if (!m.white && m.ply != lastBlackBookPly) continue;
                    }

                    int ply = m.ply;
                    if (ply >= review.evals.size()) continue;
                    double x = xOf(ply);
                    double y = bottom - review.whiteShare(ply) * plotH;
                    double r = ply == hoverPly ? 6 : 4.2;
                    g2.setColor(COLORS.get(m.moveClass));
                    g2.fill(new java.awt.geom.Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
                    g2.setColor(new Color(0, 0, 0, 110));
                    g2.setStroke(new BasicStroke(0.8f));
                    g2.draw(new java.awt.geom.Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
                }
            }

            g2.dispose();
        }
    }
}