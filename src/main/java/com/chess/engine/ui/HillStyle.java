package com.chess.engine.ui;

import java.awt.*;
import java.awt.geom.Point2D;

/*
  Look of the four central squares (d4, e4, d5, e5) in King of the Hill: lighter colours and a soft
  drop shadow around the 2x2 block, so the centre seems lifted off the board.
  Shared by the main board and the miniature board. Columns / rows are the VISUAL ones (0..7 from
  the top-left corner of what is drawn), so flipping the board needs no special handling.
 */
final class HillStyle {

    private static final Color LIGHT_HILL = new Color(250, 236, 208);
    private static final Color DARK_HILL = new Color(212, 172, 132);

    // Racing Kings: the 8th rank is lighter and raised
    private static final Color LIGHT_RACE = new Color(250, 236, 210);
    private static final Color DARK_RACE = new Color(200, 164, 132);

    private HillStyle() {
    }

    static boolean isHillSquare(int square) {
        return square == 27 || square == 28 || square == 35 || square == 36;
    }

    static Color raisedRankColor(boolean darkSquare) {
        return darkSquare ? DARK_RACE : LIGHT_RACE;
    }

    /*
      Racing Kings: the 8th rank (visual row liftedRow, 0 or 7) seems to float above the board; the
      rank next to it receives the shadow. g is translated so the cell starts at (0,0).
     */
    static void paintRaisedRank(Graphics2D g0, int w, int h, int row, int liftedRow) {
        Graphics2D g = (Graphics2D) g0.create();
        g.clipRect(0, 0, w, h);
        boolean liftedAtTop = liftedRow == 0;
        int d = Math.max(5, Math.min(14, h / 3));

        if (row == liftedRow) {
            g.setColor(new Color(255, 255, 255, 140));
            g.fillRect(0, liftedAtTop ? 0 : h - 1, w, 1);
        } else if (row == (liftedAtTop ? 1 : 6)) {
            int edgeY = liftedAtTop ? 0 : h;
            int farY = liftedAtTop ? d : h - d;
            g.setPaint(new GradientPaint(0, edgeY, new Color(40, 25, 10, 120),
                    0, farY, new Color(40, 25, 10, 0)));
            g.fillRect(0, liftedAtTop ? 0 : h - d, w, d);
        }
        g.dispose();
    }

    // Small blue padlock badge in the top-right corner of a cell (stalemate in Racing Kings).
    static void paintLock(Graphics2D g0, int w, int h) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int dia = Math.max(12, Math.min(w, h) * 36 / 100);
        int x = w - dia - 2, y = 2;

        g.setColor(new Color(122, 190, 240));
        g.fillOval(x, y, dia, dia);
        g.setColor(new Color(255, 255, 255, 200));
        g.setStroke(new BasicStroke(1f));
        g.drawOval(x, y, dia, dia);

        // padlock: body + shackle
        float cx = x + dia / 2f, cy = y + dia / 2f;
        float bw = dia * 0.42f, bh = dia * 0.30f;
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(Math.max(1.4f, dia / 10f)));
        float sw = bw * 0.62f;
        g.draw(new java.awt.geom.Arc2D.Float(cx - sw / 2, cy - bh / 2 - sw * 0.85f, sw, sw * 1.7f, 0, 180, java.awt.geom.Arc2D.OPEN));
        g.fill(new java.awt.geom.RoundRectangle2D.Float(cx - bw / 2, cy - bh * 0.35f, bw, bh, 3, 3));
        g.dispose();
    }

    static boolean isHillCell(int col, int row) {
        return col >= 3 && col <= 4 && row >= 3 && row <= 4;
    }

    static Color color(boolean darkSquare) {
        return darkSquare ? DARK_HILL : LIGHT_HILL;
    }

    /* Paints the lifted-centre effect for one cell; g is translated so the cell starts at (0,0). */
    static void paintLift(Graphics2D g0, int w, int h, int col, int row) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.clipRect(0, 0, w, h);

        int d = Math.max(4, Math.min(10, Math.min(w, h) / 3));
        int top = 70, soft = 0;

        if (isHillCell(col, row)) {
            // thin highlight on the outer edges of the block
            g.setColor(new Color(255, 255, 255, 150));
            if (row == 3) g.fillRect(0, 0, w, 1);
            if (col == 3) g.fillRect(0, 0, 1, h);
            g.setColor(new Color(120, 80, 40, 70));
            if (row == 4) g.fillRect(0, h - 1, w, 1);
            if (col == 4) g.fillRect(w - 1, 0, 1, h);
        } else {
            boolean rowsInBlock = row >= 3 && row <= 4;
            boolean colsInBlock = col >= 3 && col <= 4;

            if (rowsInBlock && col == 2) {          // left of the block: shadow on its right edge
                gradient(g, w - d, 0, w, 0, w - d, 0, d, h, top, soft);
            } else if (rowsInBlock && col == 5) {   // right of the block
                gradient(g, d, 0, 0, 0, 0, 0, d, h, top + 15, soft);
            } else if (colsInBlock && row == 2) {   // above the block: shadow on the bottom edge
                gradient(g, 0, h - d, 0, h, 0, h - d, w, d, top, soft);
            } else if (colsInBlock && row == 5) {   // below the block
                gradient(g, 0, d, 0, 0, 0, 0, w, d, top + 15, soft);
            } else if (col == 2 && row == 2) {
                corner(g, w, h, d, top);
            } else if (col == 5 && row == 2) {
                corner(g, 0, h, d, top);
            } else if (col == 2 && row == 5) {
                corner(g, w, 0, d, top);
            } else if (col == 5 && row == 5) {
                corner(g, 0, 0, d, top + 15);
            }
        }
        g.dispose();
    }

    // dark at (x1,y1) fading to nothing at (x2,y2); painted into the rectangle rx,ry,rw,rh
    private static void gradient(Graphics2D g, int x1, int y1, int x2, int y2,
                                 int rx, int ry, int rw, int rh, int alphaDark, int alphaLight) {
        g.setPaint(new GradientPaint(x2, y2, new Color(40, 25, 10, alphaDark),
                x1, y1, new Color(40, 25, 10, alphaLight)));
        g.fillRect(rx, ry, rw, rh);
    }

    private static void corner(Graphics2D g, int cx, int cy, int d, int alpha) {
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), d,
                new float[]{0f, 1f},
                new Color[]{new Color(40, 25, 10, alpha), new Color(40, 25, 10, 0)}));
        g.fillRect(cx - d, cy - d, 2 * d, 2 * d);
    }
}