package com.chess.engine.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.function.*;

@SuppressWarnings("serial")
/* Shared colours, fonts and small widgets for the navbar-driven screens (Play, Puzzles, Learn, ...). */
public class UiKit {

    private UiKit() { }

    static final Color BG        = new Color(0x16, 0x15, 0x12);
    static final Color NAV_BG    = new Color(0x26, 0x24, 0x21);
    static final Color PANEL     = new Color(0x26, 0x24, 0x21);
    static final Color PANEL_HI  = new Color(0x34, 0x32, 0x2f);
    static final Color LINE      = new Color(0x3d, 0x3a, 0x37);
    static final Color TEXT      = new Color(0xba, 0xba, 0xba);
    static final Color TEXT_DIM  = new Color(0x8a, 0x88, 0x85);
    static final Color BRIGHT    = new Color(0xf2, 0xf2, 0xf2);
    static final Color GREEN     = new Color(0x62, 0x99, 0x24);
    static final Color GREEN_HI  = new Color(0x74, 0xb0, 0x2c);
    static final Color BLUE      = new Color(0x36, 0x92, 0xe7);
    static final Color BLUE_HI   = new Color(0x4a, 0xa3, 0xf2);
    static final Color ORANGE    = new Color(0xd8, 0x50, 0x00);
    static final Color RED       = new Color(0xcc, 0x33, 0x33);
    static final Color GOOD      = new Color(0x6a, 0xb0, 0x4c);
    static final Color BAD       = new Color(0xd6, 0x4f, 0x4f);

    static Font font(int style, int size) {
        return new Font("SansSerif", style, size);
    }

    /* Flat, rounded, hover-aware button used on the dark screens. */
    static final class FlatButton extends JButton {
        private Color base;
        private Color hover;
        private boolean selectedLook;
        private Color selectedColor = GREEN;
        private int arc = 8;

        FlatButton(String text, Color base) {
            super(text);
            this.base = base;
            this.hover = brighten(base);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setForeground(BRIGHT);
            setFont(font(Font.BOLD, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(8, 16, 8, 16));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { repaint(); }
                @Override public void mouseExited(MouseEvent e) { repaint(); }
            });
        }

        FlatButton colors(Color base) {
            this.base = base;
            this.hover = brighten(base);
            repaint();
            return this;
        }

        FlatButton arc(int arc) { this.arc = arc; return this; }

        void setSelectedLook(boolean on) { selectedLook = on; repaint(); }

        FlatButton selectedColor(Color c) { selectedColor = c; return this; }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = !isEnabled() ? PANEL_HI
                    : selectedLook ? selectedColor
                    : getModel().isRollover() ? hover : base;
            g2.setColor(c);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            if (selectedLook) {
                g2.setColor(brighten(selectedColor));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            }
            g2.dispose();
            setForeground(isEnabled() ? BRIGHT : TEXT_DIM);
            super.paintComponent(g);
        }
    }

    static Color brighten(Color c) {
        return new Color(Math.min(255, c.getRed() + 18), Math.min(255, c.getGreen() + 18), Math.min(255, c.getBlue() + 18));
    }

    static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(style, size));
        l.setForeground(color);
        return l;
    }

    static JPanel darkPanel(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setBackground(PANEL);
        return p;
    }

    /* A pill-shaped on/off switch like the ones on the coordinates page. */
    static final class Toggle extends JComponent {
        private boolean on;
        private final Runnable onChange;

        Toggle(boolean initial, Runnable onChange) {
            this.on = initial;
            this.onChange = onChange;
            setPreferredSize(new Dimension(54, 28));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    Toggle.this.on = !Toggle.this.on;
                    repaint();
                    if (Toggle.this.onChange != null) Toggle.this.onChange.run();
                }
            });
        }

        boolean isOn() { return on; }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(on ? GREEN : new Color(0x4a, 0x48, 0x45));
            g2.fillRoundRect(0, 0, w, h, h, h);
            g2.setColor(new Color(0xee, 0xee, 0xee));
            int d = h - 6;
            g2.fillOval(on ? w - d - 3 : 3, 3, d, d);
            g2.setColor(on ? GREEN : new Color(0x4a, 0x48, 0x45));
            g2.setStroke(new BasicStroke(2f));
            int cx = (on ? w - d - 3 : 3) + d / 2, cy = h / 2;
            if (on) {
                g2.drawLine(cx - 4, cy, cx - 1, cy + 3);
                g2.drawLine(cx - 1, cy + 3, cx + 5, cy - 3);
            } else {
                g2.drawLine(cx - 3, cy - 3, cx + 3, cy + 3);
                g2.drawLine(cx - 3, cy + 3, cx + 3, cy - 3);
            }
            g2.dispose();
        }
    }

    /* Segmented row of choices (one selected). */
    static final class Segmented extends JPanel {
        private final List<FlatButton> buttons = new ArrayList<>();
        private int selected;
        private final IntConsumer onSelect;

        Segmented(String[] labels, int selected, IntConsumer onSelect) {
            this(labels, selected, onSelect, GREEN);
        }

        Segmented(String[] labels, int selected, IntConsumer onSelect, Color accent) {
            super(new GridLayout(1, labels.length, 2, 0));
            setOpaque(false);
            this.selected = selected;
            this.onSelect = onSelect;
            for (int i = 0; i < labels.length; i++) {
                FlatButton b = new FlatButton(labels[i], PANEL_HI);
                b.arc(6).selectedColor(accent);
                b.setBorder(new EmptyBorder(9, 12, 9, 12));
                final int idx = i;
                b.addActionListener(e -> select(idx, true));
                buttons.add(b);
                add(b);
            }
            refresh();
        }

        int getSelected() { return selected; }

        void select(int i, boolean fire) {
            selected = i;
            refresh();
            if (fire && onSelect != null) onSelect.accept(i);
        }

        private void refresh() {
            for (int i = 0; i < buttons.size(); i++) buttons.get(i).setSelectedLook(i == selected);
        }
    }
}