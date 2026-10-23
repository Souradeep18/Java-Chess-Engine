package com.chess.engine.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/*
  A gear (settings) symbol drawn with Java2D. It does not depend on any font, so it looks the same on every
  system (an emoji or symbol font may be missing, or too wide for the button, and then shows as "...").
 */
public class GearIcon implements Icon {

    private final int size;
    private final Color color;

    public GearIcon(int size, Color color) {
        this.size = size;
        this.color = color;
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.translate(x, y);

        double s = size;
        double cx = s / 2, cy = s / 2;
        double body = s * 0.33;        // radius of the round body
        double hole = s * 0.14;        // radius of the centre hole
        double toothLen = s * 0.17;    // teeth stick out this far beyond the body
        double toothW = s * 0.20;
        int teeth = 8;

        Area gear = new Area(new Ellipse2D.Double(cx - body, cy - body, 2 * body, 2 * body));
        for (int i = 0; i < teeth; i++) {
            Path2D.Double tooth = new Path2D.Double(new RoundRectangle2D.Double(
                    cx - toothW / 2, cy - body - toothLen, toothW, toothLen + body * 0.4, s * 0.04, s * 0.04));
            tooth.transform(java.awt.geom.AffineTransform.getRotateInstance(Math.PI * 2 * i / teeth, cx, cy));
            gear.add(new Area(tooth));
        }
        gear.subtract(new Area(new Ellipse2D.Double(cx - hole, cy - hole, 2 * hole, 2 * hole)));

        g2.setColor(c != null && !c.isEnabled() ? Color.GRAY : color);
        g2.fill(gear);
        g2.dispose();
    }
}