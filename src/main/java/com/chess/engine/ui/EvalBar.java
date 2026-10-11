package com.chess.engine.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Vertical evaluation bar: the white part grows towards White's side of the board when
 * White is better and shrinks when Black is better. The number of the side that is
 * ahead is written at its end of the bar.
 */
@SuppressWarnings("serial")
public class EvalBar extends JPanel {

    private static final Color BLACK_SIDE = new Color(64, 61, 57);
    private static final Color WHITE_SIDE = new Color(250, 250, 250);
    private static final Color MIDDLE_LINE = new Color(128, 128, 128, 170);

    private double shown = 0.5;   // share of the bar that is currently drawn white
    private double target = 0.5;  // share it is moving towards
    private String label = "0.0";
    private boolean flipped = false;

    private final Timer animator;

    public EvalBar() {
        setPreferredSize(new Dimension(36, 100));
        setMinimumSize(new Dimension(36, 50));
        setToolTipText("Evaluation: positive means White is better, negative means Black is better");

        animator = new Timer(16, e -> step());
    }

    /**
     * @param whiteShare 0.0 (Black winning) .. 1.0 (White winning), 0.5 = equal
     * @param text       evaluation shown on the bar, such as "+0.3" or "-M4"
     */
    public void setEvaluation(double whiteShare, String text) {
        target = Math.max(0.0, Math.min(1.0, whiteShare));
        label = text == null ? "" : text;
        animator.start();
        repaint();
    }

    /** When the board is turned round, White is at the top of the bar. */
    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
        repaint();
    }

    private void step() {
        double diff = target - shown;

        if (Math.abs(diff) < 0.002) {
            shown = target;
            animator.stop();
        } else {
            shown += diff * 0.2;
        }

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        g2.setColor(BLACK_SIDE);
        g2.fillRect(0, 0, w, h);

        int whiteHeight = (int) Math.round(shown * h);
        g2.setColor(WHITE_SIDE);
        if (flipped) {
            g2.fillRect(0, 0, w, whiteHeight);
        } else {
            g2.fillRect(0, h - whiteHeight, w, whiteHeight);
        }

        g2.setColor(MIDDLE_LINE);
        g2.drawLine(0, h / 2, w, h / 2);

        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();

        boolean whiteLeads = target >= 0.5;
        boolean atBottom = whiteLeads != flipped; // White's end is the bottom unless the board is flipped

        int x = (w - fm.stringWidth(label)) / 2;
        int y = atBottom ? h - 6 : fm.getAscent() + 4;

        g2.setColor(whiteLeads ? new Color(60, 60, 60) : new Color(235, 235, 235));
        g2.drawString(label, x, y);

        g2.setColor(new Color(150, 150, 150));
        g2.drawRect(0, 0, w - 1, h - 1);

        g2.dispose();
    }
}