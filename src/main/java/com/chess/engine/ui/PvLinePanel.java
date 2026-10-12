package com.chess.engine.ui;

import com.chess.engine.engine.Board;
import com.chess.engine.engine.Move;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
  One line of the engine's expected continuation ("6. Bd3 e6 7. O-O ...").
  Every move is its own hot spot: hovering underlines it (and the listener can show a
  miniature board), clicking it reports its index. Moves that do not fit the width are
  replaced by "...".
 */
@SuppressWarnings("serial")
public class PvLinePanel extends JComponent {

    /** One move of the line, together with the position that arises after it. */
    public static final class Step {
        public final String number;   // "6." / "6..." / "" (nothing in front of this move)
        public final String san;      // "Bd3"
        public final Board after;     // position after the move
        public final Move move;

        public Step(String number, String san, Board after, Move move) {
            this.number = number == null ? "" : number;
            this.san = san;
            this.after = after;
            this.move = move;
        }
    }

    public interface Listener {
        // The mouse is over move {@code index}; tokenBounds is the move's rectangle inside this panel.
        void onHover(PvLinePanel source, int index, Rectangle tokenBounds);

        void onHoverEnd(PvLinePanel source);

        void onClick(PvLinePanel source, int index);
    }

    private static final Color NUMBER_COLOR = new Color(120, 120, 120);
    private static final Color HOVER_COLOR = new Color(30, 90, 200);

    private List<Step> steps = Collections.emptyList();
    private Listener listener;

    // layout of the moves that are really drawn (rebuilt by computeLayout)
    private final List<Integer> numberX = new ArrayList<>();
    private final List<Rectangle> sanBounds = new ArrayList<>();
    private int ellipsisX = -1;

    private int hoverIndex = -1;
    private Point lastMouse;

    public PvLinePanel() {
        setFont(new Font("SansSerif", Font.PLAIN, 13));
        setOpaque(false);
        setPreferredSize(new Dimension(10, 22));

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                lastMouse = e.getPoint();
                updateHover(lastMouse);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                lastMouse = e.getPoint();
                updateHover(lastMouse);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                lastMouse = null;
                setCursor(Cursor.getDefaultCursor());

                if (hoverIndex >= 0) {
                    hoverIndex = -1;
                    repaint();
                    if (listener != null) {
                        listener.onHoverEnd(PvLinePanel.this);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }

                computeLayout();
                int index = indexAt(e.getPoint());
                if (index >= 0 && listener != null) {
                    listener.onClick(PvLinePanel.this, index);
                }
            }
        };

        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public List<Step> getSteps() {
        return steps;
    }

    /** Replaces the moves shown (null or empty clears the line). */
    public void setSteps(List<Step> newSteps) {
        int previousHover = hoverIndex;

        steps = newSteps == null ? Collections.<Step>emptyList() : new ArrayList<>(newSteps);
        hoverIndex = -1;

        // the mouse may still be resting on the line: find what is under it now
        if (lastMouse != null) {
            updateHover(lastMouse);
        }

        if (previousHover >= 0 && hoverIndex < 0 && listener != null) {
            listener.onHoverEnd(this);
        }

        repaint();
    }

    private void updateHover(Point p) {
        computeLayout();
        int index = indexAt(p);

        setCursor(index >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());

        if (index == hoverIndex) {
            return;
        }

        hoverIndex = index;
        repaint();

        if (listener != null) {
            if (index >= 0) {
                listener.onHover(this, index, new Rectangle(sanBounds.get(index)));
            } else {
                listener.onHoverEnd(this);
            }
        }
    }

    private int indexAt(Point p) {
        for (int i = 0; i < sanBounds.size(); i++) {
            Rectangle r = sanBounds.get(i);
            if (p.x >= r.x - 3 && p.x <= r.x + r.width + 3 && p.y >= 0 && p.y < getHeight()) {
                return i;
            }
        }
        return -1;
    }

    private void computeLayout() {
        numberX.clear();
        sanBounds.clear();
        ellipsisX = -1;

        FontMetrics fm = getFontMetrics(getFont());
        int gap = fm.stringWidth(" ");
        int ellipsisWidth = fm.stringWidth("...");
        int top = (getHeight() - fm.getHeight()) / 2;
        int limit = getWidth();
        int x = 0;

        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);

            int numberWidth = step.number.isEmpty() ? 0 : fm.stringWidth(step.number) + gap;
            int sanWidth = fm.stringWidth(step.san);
            boolean last = (i == steps.size() - 1);

            // keep room for the "..." unless this is the last move of the line
            if (x + numberWidth + sanWidth > limit - (last ? 0 : ellipsisWidth)) {
                ellipsisX = x;
                break;
            }

            numberX.add(x);
            sanBounds.add(new Rectangle(x + numberWidth, top, sanWidth, fm.getHeight()));
            x += numberWidth + sanWidth + gap;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(getFont());

        computeLayout();

        FontMetrics fm = g2.getFontMetrics();
        Color normal = UIManager.getColor("Label.foreground");
        if (normal == null) {
            normal = Color.BLACK;
        }

        for (int i = 0; i < sanBounds.size(); i++) {
            Step step = steps.get(i);
            Rectangle r = sanBounds.get(i);
            int baseline = r.y + fm.getAscent();

            if (!step.number.isEmpty()) {
                g2.setColor(normal);
                g2.drawString(step.number, numberX.get(i), baseline);
            }

            boolean hovered = (i == hoverIndex);
            g2.setColor(hovered ? HOVER_COLOR : normal);
            g2.drawString(step.san, r.x, baseline);

            if (hovered) {
                g2.fillRect(r.x, baseline + 2, r.width, 2);
            }
        }

        if (ellipsisX >= 0) {
            g2.setColor(NUMBER_COLOR);
            g2.drawString("...", ellipsisX, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        }

        g2.dispose();
    }
}