package com.chess.engine.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/*
  The "Themes" window: three tabs (Boards, Pieces, Presets) and a sample board on the right.
  Nothing is applied until Save is pressed; Cancel (or Esc) closes the window and keeps the old look.
 */
@SuppressWarnings("serial")
public class ThemesDialog {

    private ThemesDialog() {
    }

    /** Opens the window (modal). {@code onSaved} runs after the new theme has been saved. */
    public static void open(Window owner, Runnable onSaved) {
        JDialog dialog = new JDialog(owner, "Themes", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        ThemesPanel panel = new ThemesPanel(() -> {
            if (onSaved != null) {
                onSaved.run();
            }
            dialog.dispose();
        }, dialog::dispose);

        dialog.setContentPane(panel);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    // =====================================================================================

    static final Color BG = new Color(38, 36, 33);
    static final Color TILE_BG = new Color(52, 50, 47);
    static final Color TEXT = new Color(196, 194, 188);
    static final Color TEXT_DIM = new Color(150, 148, 142);
    static final Color ACCENT = new Color(127, 166, 80);
    static final Color SAVE_GREEN = new Color(98, 153, 56);
    static final Color SAVE_GREEN_HOVER = new Color(112, 172, 66);
    static final Color CANCEL_GRAY = new Color(66, 64, 61);
    static final Color CANCEL_GRAY_HOVER = new Color(82, 80, 76);

    /** The whole content of the window (public so it can be embedded or rendered on its own). */
    public static class ThemesPanel extends JPanel {

        private Themes.BoardTheme pendingBoard = Themes.getBoard();
        private Themes.PieceSet pendingPieces = Themes.getPieces();

        private final CardLayout cards = new CardLayout();
        private final JPanel cardPanel = new JPanel(cards);
        private final PreviewBoard preview = new PreviewBoard();
        private final String[] tabNames = {"Boards", "Pieces", "Presets"};
        private final TabLabel[] tabLabels = new TabLabel[3];
        private String activeTab = "Boards";

        private final JPanel boardsGrid = new JPanel();
        private final JPanel piecesGrid = new JPanel();
        private final JPanel presetsGrid = new JPanel();

        public ThemesPanel(Runnable onSave, Runnable onCancel) {
            setBackground(BG);
            setLayout(new BorderLayout(28, 0));
            setBorder(BorderFactory.createEmptyBorder(26, 30, 26, 30));

            // ---------- left: header, tabs, grids, buttons ----------
            JPanel left = new JPanel(new BorderLayout(0, 12));
            left.setOpaque(false);

            JLabel header = new JLabel("Customize the look and feel of your chess set.");
            header.setForeground(TEXT_DIM);
            header.setFont(new Font("SansSerif", Font.PLAIN, 14));

            JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            tabBar.setOpaque(false);
            for (int i = 0; i < tabNames.length; i++) {
                final String name = tabNames[i];
                tabLabels[i] = new TabLabel(name);
                tabLabels[i].addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        showTab(name);
                    }
                });
                tabBar.add(tabLabels[i]);
                if (i < tabNames.length - 1) {
                    tabBar.add(Box.createHorizontalStrut(26));
                }
            }

            JPanel top = new JPanel(new BorderLayout(0, 14));
            top.setOpaque(false);
            top.add(header, BorderLayout.NORTH);
            top.add(tabBar, BorderLayout.CENTER);

            buildBoardsGrid();
            buildPiecesGrid();
            buildPresetsGrid();

            cardPanel.setOpaque(false);
            cardPanel.add(scroll(boardsGrid), "Boards");
            cardPanel.add(scroll(piecesGrid), "Pieces");
            cardPanel.add(scroll(presetsGrid), "Presets");

            FlatButton cancel = new FlatButton("Cancel", CANCEL_GRAY, CANCEL_GRAY_HOVER, Color.WHITE, false);
            FlatButton save = new FlatButton("Save", SAVE_GREEN, SAVE_GREEN_HOVER, Color.WHITE, true);
            cancel.addActionListener(e -> onCancel.run());
            save.addActionListener(e -> {
                Themes.save(pendingBoard, pendingPieces);
                onSave.run();
            });

            JPanel buttons = new JPanel(new GridLayout(1, 2, 18, 0));
            buttons.setOpaque(false);
            buttons.add(cancel);
            buttons.add(save);
            buttons.setPreferredSize(new Dimension(100, 50));

            left.add(top, BorderLayout.NORTH);
            left.add(cardPanel, BorderLayout.CENTER);
            left.add(buttons, BorderLayout.SOUTH);
            left.setPreferredSize(new Dimension(560, 600));

            // ---------- right: sample board ----------
            JPanel right = new JPanel(new BorderLayout());
            right.setOpaque(false);
            right.add(preview, BorderLayout.NORTH);
            preview.setPreferredSize(new Dimension(420, 420));

            add(left, BorderLayout.CENTER);
            add(right, BorderLayout.EAST);

            showTab("Boards");
        }

        // ---------- tabs ----------

        private void showTab(String name) {
            activeTab = name;
            cards.show(cardPanel, name);
            for (TabLabel t : tabLabels) {
                t.repaint();
            }
            // the piece tiles show the chosen board, so redraw them when their tab opens
            piecesGrid.repaint();
        }

        private class TabLabel extends JLabel {
            private final String name;

            TabLabel(String name) {
                super(name);
                this.name = name;
                setFont(new Font("SansSerif", Font.BOLD, 17));
                setBorder(BorderFactory.createEmptyBorder(6, 0, 9, 0));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            @Override
            protected void paintComponent(Graphics g) {
                boolean active = name.equals(activeTab);
                setForeground(active ? Color.WHITE : TEXT_DIM);
                super.paintComponent(g);
                if (active) {
                    g.setColor(Color.WHITE);
                    g.fillRect(0, getHeight() - 3, getWidth(), 3);
                }
            }
        }

        // ---------- grids ----------

        private JScrollPane scroll(JPanel grid) {
            JScrollPane sp = new JScrollPane(grid, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            sp.setBorder(BorderFactory.createEmptyBorder());
            sp.setOpaque(false);
            sp.getViewport().setOpaque(false);
            sp.getVerticalScrollBar().setUnitIncrement(24);
            return sp;
        }

        private void prepareGrid(JPanel grid, int columns, int count, int tileW, int tileH, int gap) {
            grid.removeAll();
            grid.setOpaque(false);
            grid.setLayout(new GridLayout(0, columns, gap, gap));
            int rows = (count + columns - 1) / columns;
            grid.setPreferredSize(new Dimension(columns * tileW + (columns - 1) * gap + 4,
                    rows * tileH + (rows - 1) * gap + 8));
        }

        private void buildBoardsGrid() {
            List<Themes.BoardTheme> boards = Themes.BOARDS;
            prepareGrid(boardsGrid, 5, boards.size(), 84, 84, 14);
            for (Themes.BoardTheme b : boards) {
                Tile t = new Tile(b.name, () -> {
                    pendingBoard = b;
                    refreshAll();
                }) {
                    @Override
                    void paintContent(Graphics2D g, int w, int h) {
                        paintMiniBoard(g, 0, 0, w, h, b.light, b.dark);
                    }

                    @Override
                    boolean selected() {
                        return pendingBoard == b;
                    }
                };
                t.checkInCentre = true;
                boardsGrid.add(t);
            }
        }

        private void buildPiecesGrid() {
            List<Themes.PieceSet> sets = Themes.PIECES;
            prepareGrid(piecesGrid, 4, sets.size(), 112, 96, 14);
            for (Themes.PieceSet p : sets) {
                Tile t = new Tile(p.name, () -> {
                    pendingPieces = p;
                    refreshAll();
                }) {
                    @Override
                    void paintContent(Graphics2D g, int w, int h) {
                        int sq = Math.min(w / 2, h - 28);
                        int x0 = (w - 2 * sq) / 2;
                        int y0 = 6;
                        g.setColor(pendingBoard.light);
                        g.fillRect(x0, y0, sq, sq);
                        g.setColor(pendingBoard.dark);
                        g.fillRect(x0 + sq, y0, sq, sq);
                        p.paint(g, 'N', x0, y0, sq);
                        p.paint(g, 'n', x0 + sq, y0, sq);
                        g.setColor(TEXT);
                        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                        FontMetrics fm = g.getFontMetrics();
                        g.drawString(p.name, (w - fm.stringWidth(p.name)) / 2, h - 8);
                    }

                    @Override
                    boolean selected() {
                        return pendingPieces == p;
                    }
                };
                piecesGrid.add(t);
            }
        }

        private void buildPresetsGrid() {
            List<Themes.Preset> presets = Themes.PRESETS;
            prepareGrid(presetsGrid, 3, presets.size(), 150, 164, 18);
            for (Themes.Preset pr : presets) {
                Themes.BoardTheme pb = Themes.findBoard(pr.boardId);
                Themes.PieceSet pp = Themes.findPieces(pr.pieceId);
                if (pb == null || pp == null) {
                    continue;
                }
                Tile t = new Tile(pr.name, () -> {
                    pendingBoard = pb;
                    pendingPieces = pp;
                    refreshAll();
                }) {
                    @Override
                    void paintContent(Graphics2D g, int w, int h) {
                        int size = Math.min(w, h - 26);
                        int sq = size / 2;
                        int x0 = (w - 2 * sq) / 2;
                        int y0 = 0;
                        paintMiniBoard(g, x0, y0, 2 * sq, 2 * sq, pb.light, pb.dark);
                        pp.paint(g, 'n', x0, y0, sq);
                        pp.paint(g, 'N', x0 + sq, y0, sq);
                        pp.paint(g, 'p', x0, y0 + sq, sq);
                        pp.paint(g, 'P', x0 + sq, y0 + sq, sq);
                        g.setColor(TEXT);
                        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
                        FontMetrics fm = g.getFontMetrics();
                        g.drawString(pr.name, (w - fm.stringWidth(pr.name)) / 2, h - 6);
                    }

                    @Override
                    boolean selected() {
                        return pendingBoard == pb && pendingPieces == pp;
                    }
                };
                presetsGrid.add(t);
            }
        }

        private void refreshAll() {
            boardsGrid.repaint();
            piecesGrid.repaint();
            presetsGrid.repaint();
            preview.repaint();
        }

        // ---------- shared drawing ----------

        /** A 2 x 2 mini board (light top-left), rounded corners. */
        static void paintMiniBoard(Graphics2D g, int x, int y, int w, int h, Color light, Color dark) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.clip(new RoundRectangle2D.Float(x, y, w, h, 12, 12));
            int hw = w / 2, hh = h / 2;
            g2.setColor(light);
            g2.fillRect(x, y, hw, hh);
            g2.fillRect(x + hw, y + hh, w - hw, h - hh);
            g2.setColor(dark);
            g2.fillRect(x + hw, y, w - hw, hh);
            g2.fillRect(x, y + hh, hw, h - hh);
            g2.dispose();
        }

        // ---------- tiles ----------

        private abstract class Tile extends JComponent {
            private final Runnable onClick;
            boolean checkInCentre = false;
            private boolean hover = false;

            Tile(String tooltip, Runnable onClick) {
                this.onClick = onClick;
                setToolTipText(tooltip);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        Tile.this.onClick.run();
                    }

                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hover = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hover = false;
                        repaint();
                    }
                });
            }

            abstract void paintContent(Graphics2D g, int w, int h);

            abstract boolean selected();

            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();
                boolean sel = selected();

                if (!checkInCentre) {
                    g.setColor(hover ? new Color(64, 62, 58) : TILE_BG);
                    g.fill(new RoundRectangle2D.Float(0, 0, w, h, 14, 14));
                }

                Graphics2D content = (Graphics2D) g.create();
                int pad = checkInCentre ? 0 : 6;
                content.translate(pad, pad);
                paintContent(content, w - 2 * pad, h - 2 * pad);
                content.dispose();

                if (sel) {
                    g.setColor(ACCENT);
                    g.setStroke(new BasicStroke(3f));
                    g.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3, h - 3, 14, 14));
                    int d = 26;
                    int cx = checkInCentre ? (w - d) / 2 : w - d - 6;
                    int cy = checkInCentre ? (h - d) / 2 : 6;
                    g.setColor(ACCENT);
                    g.fillOval(cx, cy, d, d);
                    g.setColor(Color.WHITE);
                    g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawPolyline(new int[]{cx + 7, cx + 11, cx + 19}, new int[]{cy + 13, cy + 18, cy + 8}, 3);
                } else if (hover && checkInCentre) {
                    g.setColor(new Color(255, 255, 255, 110));
                    g.setStroke(new BasicStroke(2f));
                    g.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 14, 14));
                }
                g.dispose();
            }
        }

        // ---------- the sample board on the right ----------

        private class PreviewBoard extends JComponent {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int cell = Math.min(getWidth(), getHeight()) / 3;
                char[][] sample = {
                        {'b', 'q', 'p'},
                        {'.', '.', '.'},
                        {'N', 'K', 'R'}
                };
                int[] ranks = {8, 7, 6};

                g.clip(new RoundRectangle2D.Float(0, 0, cell * 3, cell * 3, 14, 14));
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        boolean light = (r + c) % 2 == 0;
                        g.setColor(light ? pendingBoard.light : pendingBoard.dark);
                        g.fillRect(c * cell, r * cell, cell, cell);
                    }
                }
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        if (sample[r][c] != '.') {
                            pendingPieces.paint(g, sample[r][c], c * cell, r * cell, cell);
                        }
                    }
                    boolean light = r % 2 == 0;
                    g.setFont(new Font("SansSerif", Font.BOLD, 15));
                    g.setColor(light ? pendingBoard.dark : pendingBoard.light);
                    g.drawString(String.valueOf(ranks[r]), 8, r * cell + 20);
                }
                g.dispose();
            }
        }
    }

    // =====================================================================================

    /** A flat rounded button (the system look and feel would ignore the colours). */
    static class FlatButton extends JButton {
        private final Color normal;
        private final Color hover;
        private final Color text;
        private final boolean bold;
        private boolean over = false;

        FlatButton(String label, Color normal, Color hover, Color text, boolean bold) {
            super(label);
            this.normal = normal;
            this.hover = hover;
            this.text = text;
            this.bold = bold;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    over = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    over = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(over ? hover : normal);
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
            g.setColor(text);
            g.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, 18));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }
}