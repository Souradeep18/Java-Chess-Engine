package com.chess.engine.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/*
  The move list of the Play screen: numbered rows with White's and Black's move side by side. The move being
  looked at is highlighted, and clicking a move (see {@link #setOnSelect}) shows the position after it.
  Drawn by hand, so it does not depend on any shared HTML style sheet.
 */
@SuppressWarnings("serial")
final class PlayMoveList extends JPanel {

    private static final int ROW_HEIGHT = 38;
    private static final int NUMBER_WIDTH = 46;
    private static final Color NUMBER_BG = new Color(0x2f, 0x2d, 0x2a);
    private static final Color SELECTED = new Color(0x36, 0x92, 0xe7);

    private final List<String> sans = new ArrayList<>();
    private int selected = 0;                 // half-moves played in the position shown; 0 = the start
    private IntConsumer onSelect;

    private final JComponent canvas = new JComponent() {
        @Override public Dimension getPreferredSize() {
            return new Dimension(200, Math.max(ROW_HEIGHT, rows() * ROW_HEIGHT + 4));
        }

        @Override protected void paintComponent(Graphics g) {
            paintRows((Graphics2D) g.create(), this);
        }
    };
    private final JScrollPane scroll = new JScrollPane(canvas, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    PlayMoveList() {
        super(new BorderLayout());
        setOpaque(false);
        scroll.setBorder(BorderFactory.createLineBorder(UiKit.LINE));
        scroll.getViewport().setBackground(UiKit.PANEL);
        scroll.getVerticalScrollBar().setUnitIncrement(ROW_HEIGHT);
        add(scroll, BorderLayout.CENTER);
        setPreferredSize(new Dimension(340, 300));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 10_000));

        canvas.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        canvas.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int ply = plyAt(e.getX(), e.getY());
                if (ply > 0 && onSelect != null) onSelect.accept(ply);
            }
        });
    }

    /* Called with the number of half-moves (1 = after White's first move) when the player clicks a move. */
    void setOnSelect(IntConsumer listener) {
        this.onSelect = listener;
    }

    void clear() {
        sans.clear();
        selected = 0;
        refresh();
    }

    void add(String san) {
        sans.add(san);
        refresh();
    }

    void removeLast() {
        if (!sans.isEmpty()) sans.remove(sans.size() - 1);
        if (selected > sans.size()) selected = sans.size();
        refresh();
    }

    int count() {
        return sans.size();
    }

    /* Highlights the position after {@code ply} half-moves (0 = no move highlighted) and scrolls to it. */
    void select(int ply) {
        selected = Math.max(0, Math.min(ply, sans.size()));
        refresh();
    }

    int getSelected() {
        return selected;
    }

    private int rows() {
        return (sans.size() + 1) / 2;
    }

    private void refresh() {
        canvas.revalidate();
        canvas.repaint();
        if (selected > 0) {
            int row = (selected - 1) / 2;
            final Rectangle r = new Rectangle(0, row * ROW_HEIGHT, 10, ROW_HEIGHT);
            SwingUtilities.invokeLater(() -> canvas.scrollRectToVisible(r));
        } else {
            SwingUtilities.invokeLater(() -> canvas.scrollRectToVisible(new Rectangle(0, 0, 10, ROW_HEIGHT)));
        }
    }

    private int cellWidth() {
        return Math.max(40, (canvas.getWidth() - NUMBER_WIDTH) / 2);
    }

    /* The half-move number of the move drawn at a point, or 0. */
    private int plyAt(int x, int y) {
        if (x < NUMBER_WIDTH || y < 0) return 0;
        int row = y / ROW_HEIGHT;
        int col = (x - NUMBER_WIDTH) / cellWidth();
        if (col > 1) col = 1;
        int ply = row * 2 + col + 1;
        return ply <= sans.size() ? ply : 0;
    }

    private void paintRows(Graphics2D g, JComponent c) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = c.getWidth(), h = c.getHeight();
        g.setColor(UiKit.PANEL);
        g.fillRect(0, 0, w, h);
        g.setColor(NUMBER_BG);
        g.fillRect(0, 0, NUMBER_WIDTH, h);

        Font numberFont = UiKit.font(Font.PLAIN, 16);
        Font moveFont = UiKit.font(Font.PLAIN, 19);
        Rectangle clip = g.getClipBounds();
        int first = Math.max(0, clip.y / ROW_HEIGHT);
        int last = Math.min(rows() - 1, (clip.y + clip.height) / ROW_HEIGHT);
        int cw = cellWidth();
        for (int row = first; row <= last; row++) {
            int y = row * ROW_HEIGHT;
            g.setFont(numberFont);
            g.setColor(UiKit.TEXT_DIM);
            String num = String.valueOf(row + 1);
            FontMetrics nfm = g.getFontMetrics();
            g.drawString(num, (NUMBER_WIDTH - nfm.stringWidth(num)) / 2, y + (ROW_HEIGHT + nfm.getAscent() - nfm.getDescent()) / 2);
            for (int col = 0; col < 2; col++) {
                int ply = row * 2 + col + 1;
                if (ply > sans.size()) break;
                int x = NUMBER_WIDTH + col * cw;
                boolean sel = ply == selected;
                g.setFont(moveFont.deriveFont(sel ? Font.BOLD : Font.PLAIN));
                FontMetrics fm = g.getFontMetrics();
                String san = sans.get(ply - 1);
                if (sel) {
                    g.setColor(SELECTED);
                    g.fillRoundRect(x + 6, y + 4, Math.min(cw - 12, fm.stringWidth(san) + 22), ROW_HEIGHT - 8, 6, 6);
                }
                g.setColor(sel ? Color.WHITE : UiKit.BRIGHT);
                g.drawString(san, x + 17, y + (ROW_HEIGHT + fm.getAscent() - fm.getDescent()) / 2);
            }
        }
        g.dispose();
    }
}