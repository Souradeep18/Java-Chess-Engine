package com.chess.engine.ui;

import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.Map;

/*
  Vector chess pieces in three different shape families. Every piece is built from a few simple
  shapes in a 100 x 100 box (y grows downwards, the base is at the bottom) and is scaled to the square
  when it is painted.
 
   - STAUNTON : the traditional tournament silhouettes (round heads, collars, flared bodies)
   - GEOMETRIC: angular, flat, "low-poly" pieces
   - CHUNKY   : the Staunton shapes puffed up into soft, heavy toy-like pieces
   - SLIM, STOUT, MINIMAL, PIXEL, ART_DECO, BUBBLE, LETTERS, GOTHIC, COIN, BLOCKS: ten more families,
     each with its own six silhouettes
 */
final class PieceShapes {

    enum Family {
        STAUNTON, GEOMETRIC, CHUNKY,
        SLIM, STOUT, MINIMAL, PIXEL, ART_DECO, BUBBLE, LETTERS, GOTHIC, COIN, BLOCKS
    }

    private PieceShapes() {
    }

    private static final Map<String, Shape> CACHE = new HashMap<>();

    /** The silhouette of a piece (FEN letter, either colour) in the 100 x 100 box. */
    static synchronized Shape shape(Family family, char piece) {
        char t = Character.toLowerCase(piece);
        String key = family + ":" + t;
        Shape s = CACHE.get(key);
        if (s == null) {
            switch (family) {
                case GEOMETRIC:
                    s = geometric(t);
                    break;
                case CHUNKY:
                    s = chunky(t);
                    break;
                case SLIM:
                    s = slim(t);
                    break;
                case STOUT:
                    s = stout(t);
                    break;
                case MINIMAL:
                    s = minimal(t);
                    break;
                case PIXEL:
                    s = pixel(t);
                    break;
                case ART_DECO:
                    s = artDeco(t);
                    break;
                case BUBBLE:
                    s = bubble(t);
                    break;
                case LETTERS:
                    s = letters(t);
                    break;
                case GOTHIC:
                    s = gothic(t);
                    break;
                case COIN:
                    s = coin(t);
                    break;
                case BLOCKS:
                    s = blocks(t);
                    break;
                default:
                    s = staunton(t);
                    break;
            }
            CACHE.put(key, s);
        }
        return s;
    }

    //  small helpers


    private static Area area(Shape s) {
        return new Area(s);
    }

    private static Area circle(double cx, double cy, double r) {
        return area(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    private static Area ellipse(double x, double y, double w, double h) {
        return area(new Ellipse2D.Double(x, y, w, h));
    }

    private static Area rect(double x, double y, double w, double h) {
        return area(new Rectangle2D.Double(x, y, w, h));
    }

    private static Area rounded(double x, double y, double w, double h, double r) {
        return area(new RoundRectangle2D.Double(x, y, w, h, r, r));
    }

    private static Area polygon(double... xy) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            p.lineTo(xy[i], xy[i + 1]);
        }
        p.closePath();
        return area(p);
    }

    private static Area union(Area first, Area... more) {
        Area a = new Area(first);
        for (Area m : more) {
            a.add(m);
        }
        return a;
    }

    private static Area minus(Area a, Area... holes) {
        Area r = new Area(a);
        for (Area h : holes) {
            r.subtract(h);
        }
        return r;
    }

    /* Two-step plinth every Staunton-style piece stands on. */
    private static Area plinth() {
        return union(rounded(27, 76, 46, 10, 5), rounded(22, 83, 56, 9, 5));
    }

    //  STAUNTON

    private static Area staunton(char t) {
        switch (t) {
            case 'p':
                return union(circle(50, 29, 11),
                        rounded(37, 38, 26, 6, 3),
                        polygon(43, 43, 57, 43, 66, 78, 34, 78),
                        plinth());
            case 'r':
                return union(minus(rect(30, 15, 40, 21), rect(39, 15, 6, 9), rect(55, 15, 6, 9)),
                        rect(34, 34, 32, 5),
                        polygon(37, 39, 63, 39, 67, 78, 33, 78),
                        plinth());
            case 'b': {
                Area mitre = minus(ellipse(38, 19, 24, 32),
                        polygon(45, 30, 48, 27, 61, 40, 58, 43));
                return union(circle(50, 13, 5), mitre,
                        rounded(35, 49, 30, 6, 3),
                        polygon(41, 54, 59, 54, 66, 78, 34, 78),
                        plinth());
            }
            case 'n': {
                Path2D.Double head = new Path2D.Double();
                head.moveTo(30, 80);
                head.curveTo(30, 70, 33, 62, 38, 56);
                head.curveTo(41, 53, 42, 51, 40, 50);
                head.lineTo(31, 54);
                head.curveTo(25, 55, 21, 50, 21, 45);
                head.curveTo(21, 40, 25, 35, 30, 31);
                head.lineTo(35, 13);
                head.lineTo(43, 21);
                head.curveTo(49, 17, 57, 18, 63, 25);
                head.curveTo(71, 35, 73, 55, 71, 80);
                head.closePath();
                return minus(union(area(head), plinth()), circle(36, 34, 2.6));
            }
            case 'q': {
                Area crown = polygon(32, 46, 27, 20, 37, 33, 39, 14, 45, 33, 50, 11, 55, 33, 61, 14, 63, 33, 73, 20, 68, 46);
                return union(crown,
                        circle(27, 18, 3.6), circle(39, 12, 3.6), circle(50, 9, 3.6), circle(61, 12, 3.6), circle(73, 18, 3.6),
                        rounded(33, 44, 34, 6, 3),
                        polygon(38, 49, 62, 49, 68, 78, 32, 78),
                        plinth());
            }
            case 'k': {
                Area cross = union(rect(46, 3, 8, 27), rect(40, 10, 20, 7));
                return union(cross,
                        rounded(36, 28, 28, 22, 14),
                        rounded(33, 48, 34, 6, 3),
                        polygon(38, 53, 62, 53, 68, 78, 32, 78),
                        plinth());
            }
            default:
                return new Area();
        }
    }


    //  GEOMETRIC

    private static Area flatBase() {
        return union(rect(24, 78, 52, 8), rect(18, 85, 64, 7));
    }

    private static Area geometric(char t) {
        switch (t) {
            case 'p':
                return union(polygon(50, 16, 62, 28, 50, 42, 38, 28),
                        polygon(42, 40, 58, 40, 68, 78, 32, 78),
                        flatBase());
            case 'r':
                return union(minus(rect(28, 14, 44, 18), rect(38, 14, 8, 9), rect(54, 14, 8, 9)),
                        polygon(34, 32, 66, 32, 70, 78, 30, 78),
                        flatBase());
            case 'b':
                return union(minus(polygon(50, 6, 65, 30, 50, 52, 35, 30),
                                polygon(44, 28, 48, 24, 62, 38, 58, 42)),
                        polygon(41, 50, 59, 50, 68, 78, 32, 78),
                        flatBase());
            case 'n':
                return minus(union(polygon(27, 78, 33, 56, 44, 45, 25, 50, 17, 43, 33, 25, 37, 9, 48, 20, 62, 24, 73, 48, 73, 78),
                        flatBase()), polygon(33, 33, 38, 31, 38, 36));
            case 'q':
                return union(polygon(30, 46, 22, 14, 38, 30, 50, 8, 62, 30, 78, 14, 70, 46),
                        polygon(36, 44, 64, 44, 70, 78, 30, 78),
                        flatBase());
            case 'k':
                return union(union(rect(46, 3, 8, 24), rect(38, 9, 24, 8)),
                        polygon(34, 28, 66, 28, 70, 48, 30, 48),
                        polygon(38, 47, 62, 47, 70, 78, 30, 78),
                        flatBase());
            default:
                return new Area();
        }
    }


    //  CHUNKY: the Staunton silhouettes puffed up

    private static Area chunky(char t) {
        Area base = staunton(t);
        Area puffed = new Area(base);
        puffed.add(new Area(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(base)));
        // the eye and the bishop's slit were filled by the puffing: cut them out again
        if (t == 'n') {
            puffed.subtract(circle(37, 35, 3.2));
        } else if (t == 'b') {
            puffed.subtract(polygon(45, 29, 48, 26, 61, 39, 58, 42));
        }
        return puffed;
    }

    //  SLIM: tall, narrow, elegant

    private static Area slimBase() {
        return union(rounded(36, 81, 28, 5, 3), rounded(32, 86, 36, 6, 3));
    }

    private static Area slim(char t) {
        switch (t) {
            case 'p':
                return union(circle(50, 26, 8.5), rounded(43, 34, 14, 4, 2),
                        polygon(46.5, 37, 53.5, 37, 57, 81, 43, 81), slimBase());
            case 'r':
                return union(minus(rect(36, 12, 28, 15), rect(43, 12, 5, 8), rect(52, 12, 5, 8)),
                        rect(40, 27, 20, 4), polygon(42.5, 31, 57.5, 31, 60, 81, 40, 81), slimBase());
            case 'b': {
                Area mitre = minus(ellipse(40, 14, 20, 34), polygon(46, 28, 49, 25, 58, 35, 55, 38));
                return union(circle(50, 10, 4), mitre, rounded(38, 46, 24, 4, 2),
                        polygon(44, 49, 56, 49, 60, 81, 40, 81), slimBase());
            }
            case 'n': {
                Path2D.Double head = new Path2D.Double();
                head.moveTo(36, 82);
                head.curveTo(36, 70, 38, 62, 42, 56);
                head.curveTo(44, 53, 44, 51, 42, 50);
                head.lineTo(34, 53);
                head.curveTo(29, 54, 26, 50, 26, 46);
                head.curveTo(26, 41, 29, 37, 33, 33);
                head.lineTo(37, 12);
                head.lineTo(44, 21);
                head.curveTo(50, 18, 57, 19, 62, 26);
                head.curveTo(68, 36, 69, 56, 66, 82);
                head.closePath();
                return minus(union(area(head), slimBase()), circle(38, 33, 2));
            }
            case 'q':
                return union(polygon(38, 48, 33, 18, 41, 32, 43, 10, 47, 31, 50, 6, 53, 31, 57, 10, 59, 32, 67, 18, 62, 48),
                        circle(33, 16, 3), circle(43, 9, 3), circle(50, 5, 3), circle(57, 9, 3), circle(67, 16, 3),
                        rounded(37, 46, 26, 4, 2), polygon(42, 50, 58, 50, 62, 81, 38, 81), slimBase());
            case 'k':
                return union(rect(47, 2, 6, 24), rect(41, 8, 18, 6), rounded(39, 25, 22, 20, 11),
                        rounded(37, 44, 26, 4, 2), polygon(42, 47, 58, 47, 62, 81, 38, 81), slimBase());
            default:
                return new Area();
        }
    }

    //  STOUT: short, wide, heavy

    private static Area stoutBase() {
        return union(rounded(18, 78, 64, 9, 5), rounded(12, 85, 76, 8, 5));
    }

    private static Area stout(char t) {
        switch (t) {
            case 'p':
                return union(circle(50, 40, 15), rounded(32, 53, 36, 8, 4),
                        polygon(36, 58, 64, 58, 74, 80, 26, 80), stoutBase());
            case 'r':
                return union(minus(rect(22, 26, 56, 22), rect(34, 26, 10, 11), rect(56, 26, 10, 11)),
                        rect(26, 46, 48, 7), polygon(31, 52, 69, 52, 76, 80, 24, 80), stoutBase());
            case 'b': {
                Area mitre = minus(ellipse(30, 24, 40, 40), polygon(44, 40, 49, 35, 66, 52, 61, 57));
                return union(circle(50, 19, 7), mitre, rounded(26, 60, 48, 8, 4),
                        polygon(34, 66, 66, 66, 76, 80, 24, 80), stoutBase());
            }
            case 'n': {
                Path2D.Double head = new Path2D.Double();
                head.moveTo(20, 82);
                head.curveTo(20, 70, 24, 62, 30, 56);
                head.curveTo(34, 52, 35, 50, 32, 48);
                head.lineTo(22, 52);
                head.curveTo(14, 54, 9, 48, 9, 42);
                head.curveTo(9, 36, 14, 31, 21, 27);
                head.lineTo(26, 14);
                head.lineTo(36, 22);
                head.curveTo(46, 17, 62, 20, 72, 32);
                head.curveTo(82, 46, 84, 64, 82, 82);
                head.closePath();
                return minus(union(area(head), stoutBase()), circle(30, 36, 3.2));
            }
            case 'q':
                return union(polygon(24, 58, 14, 30, 30, 42, 33, 22, 42, 42, 50, 18, 58, 42, 67, 22, 70, 42, 86, 30, 76, 58),
                        circle(14, 28, 5), circle(33, 19, 5), circle(50, 15, 5), circle(67, 19, 5), circle(86, 28, 5),
                        rounded(26, 56, 48, 8, 4), polygon(32, 62, 68, 62, 76, 80, 24, 80), stoutBase());
            case 'k':
                return union(rect(44, 4, 12, 26), rect(35, 11, 30, 11), rounded(24, 30, 52, 30, 18),
                        rounded(25, 56, 50, 8, 4), polygon(32, 62, 68, 62, 76, 80, 24, 80), stoutBase());
            default:
                return new Area();
        }
    }

    //  MINIMAL: plain circles, rounded blocks and one flat base

    private static Area minimal(char t) {
        Area base = rounded(24, 84, 52, 6, 3);
        switch (t) {
            case 'p':
                return union(circle(50, 36, 15), rounded(32, 54, 36, 28, 10), base);
            case 'r':
                return union(minus(rounded(28, 16, 44, 66, 4), rect(40, 16, 6, 11), rect(54, 16, 6, 11)), base);
            case 'b':
                return union(minus(ellipse(36, 12, 28, 42), polygon(46, 26, 50, 22, 60, 33, 56, 37)),
                        polygon(40, 52, 60, 52, 66, 82, 34, 82), base);
            case 'n':
                return minus(union(polygon(30, 82, 34, 58, 22, 54, 20, 44, 40, 24, 44, 10, 54, 20, 70, 36, 74, 82), base),
                        circle(40, 36, 3));
            case 'q':
                return union(polygon(30, 82, 24, 24, 40, 46, 50, 16, 60, 46, 76, 24, 70, 82), circle(24, 22, 4),
                        circle(50, 13, 4), circle(76, 22, 4), base);
            case 'k':
                return union(rect(46, 4, 8, 28), rect(38, 12, 24, 8), rounded(30, 32, 40, 50, 10), base);
            default:
                return new Area();
        }
    }

    //  PIXEL: every piece is a 10 x 10 bitmap

    private static final String[][] PIXELS = {
            // pawn
            {"..........", "....##....", "...####...", "...####...", "....##....",
                    "...####...", "...####...", "..######..", ".########.", ".########."},
            // rook
            {"..........", ".##.##.##.", ".########.", ".########.", "..######..",
                    "..######..", "..######..", "..######..", ".########.", ".########."},
            // bishop
            {"....##....", "...####...", "..####.#..", "..###.##..", "..######..",
                    "...####...", "....##....", "...####...", "..######..", ".########."},
            // knight
            {"..#.#.....", "..#####...", ".##.####..", "#######...", "######....",
                    "...######.", "...######.", "..#######.", ".########.", ".########."},
            // queen
            {"#..#..#..#", "#..#..#..#", "##.####.##", ".########.", "..######..",
                    "..######..", "...####...", "..######..", ".########.", ".########."},
            // king
            {"....##....", "..######..", "....##....", "..######..", ".########.",
                    "..######..", "...####...", "..######..", ".########.", ".########."}
    };

    private static Area pixel(char t) {
        int idx = "prbnqk".indexOf(t);
        if (idx < 0) {
            return new Area();
        }
        Area a = new Area();
        String[] rows = PIXELS[idx];
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                if (rows[y].charAt(x) == '#') {
                    a.add(rect(x * 10, y * 10, 10, 10));
                }
            }
        }
        return a;
    }

    //  ART DECO: stepped ziggurat bases, diamonds, fans and chevrons

    private static Area decoBase() {
        return union(rect(20, 84, 60, 8), rect(27, 77, 46, 7), rect(34, 70, 32, 7));
    }

    private static Area artDeco(char t) {
        switch (t) {
            case 'p':
                return union(polygon(50, 8, 63, 24, 50, 40, 37, 24), rect(41, 40, 18, 5),
                        polygon(44, 45, 56, 45, 60, 70, 40, 70), decoBase());
            case 'r':
                return union(rect(28, 12, 12, 16), rect(44, 12, 12, 16), rect(60, 12, 12, 16),
                        rect(28, 28, 44, 7), polygon(35, 35, 65, 35, 68, 70, 32, 70), decoBase());
            case 'b':
                return union(minus(polygon(50, 4, 63, 22, 65, 34, 58, 42, 42, 42, 35, 34, 37, 22),
                                polygon(50, 16, 55, 24, 50, 32, 45, 24)),
                        rect(35, 42, 30, 5), polygon(42, 47, 58, 47, 62, 70, 38, 70), decoBase());
            case 'n':
                return minus(union(polygon(32, 70, 34, 52, 44, 42, 28, 44, 20, 36, 34, 20, 38, 8, 48, 16, 58, 18, 70, 32, 72, 70),
                        decoBase()), polygon(34, 29, 40, 27, 38, 33));
            case 'q':
                return union(polygon(34, 48, 20, 14, 36, 30, 42, 6, 50, 28, 58, 6, 64, 30, 80, 14, 66, 48),
                        rect(33, 48, 34, 5), polygon(39, 53, 61, 53, 65, 70, 35, 70), decoBase());
            case 'k':
                return union(rect(46, 3, 8, 22), rect(38, 9, 24, 8), polygon(34, 26, 66, 26, 70, 44, 30, 44),
                        rect(32, 44, 36, 5), polygon(38, 49, 62, 49, 66, 70, 34, 70), decoBase());
            default:
                return new Area();
        }
    }

    //  BUBBLE: nothing but circles and ovals

    private static Area bubbleBase() {
        return ellipse(18, 76, 64, 16);
    }

    private static Area bubble(char t) {
        switch (t) {
            case 'p':
                return union(circle(50, 32, 17), circle(50, 62, 20), bubbleBase());
            case 'r':
                return union(circle(33, 20, 8), circle(50, 17, 8), circle(67, 20, 8), rounded(26, 22, 48, 20, 12),
                        ellipse(28, 36, 44, 44), bubbleBase());
            case 'b':
                return union(circle(50, 12, 6), minus(ellipse(32, 16, 36, 44), circle(58, 30, 4.5)),
                        ellipse(36, 54, 28, 26), bubbleBase());
            case 'n':
                return minus(union(circle(54, 34, 16), ellipse(20, 34, 38, 20), circle(55, 17, 6), circle(66, 21, 6),
                        ellipse(40, 44, 40, 40), ellipse(30, 58, 50, 24), bubbleBase()), circle(49, 30, 3.2));
            case 'q':
                return union(circle(50, 42, 20), circle(26, 26, 7), circle(38, 15, 7), circle(50, 11, 7),
                        circle(62, 15, 7), circle(74, 26, 7), ellipse(30, 56, 40, 26), bubbleBase());
            case 'k':
                return union(rounded(46, 2, 8, 24, 8), rounded(40, 8, 20, 8, 8), circle(50, 42, 20),
                        ellipse(30, 56, 40, 26), bubbleBase());
            default:
                return new Area();
        }
    }

    //  LETTERS: the pieces are written as capital letters (K Q R B N P)

    private static Area strokeOf(Shape s, float width) {
        return area(new BasicStroke(width, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER).createStrokedShape(s));
    }

    private static Area line(double x1, double y1, double x2, double y2) {
        return strokeOf(new java.awt.geom.Line2D.Double(x1, y1, x2, y2), 13f);
    }

    private static Area letters(char t) {
        Area serif = rect(22, 84, 56, 8);
        Path2D.Double bowl = new Path2D.Double();
        switch (t) {
            case 'p':
                bowl.moveTo(34, 20);
                bowl.lineTo(52, 20);
                bowl.curveTo(72, 20, 72, 52, 52, 52);
                bowl.lineTo(34, 52);
                return union(line(34, 14, 34, 86), strokeOf(bowl, 13f), serif);
            case 'r':
                bowl.moveTo(34, 20);
                bowl.lineTo(54, 20);
                bowl.curveTo(72, 20, 72, 50, 54, 50);
                bowl.lineTo(34, 50);
                return union(line(34, 14, 34, 86), strokeOf(bowl, 13f), line(50, 52, 68, 84), serif);
            case 'b': {
                Path2D.Double lower = new Path2D.Double();
                bowl.moveTo(34, 20);
                bowl.lineTo(50, 20);
                bowl.curveTo(66, 20, 66, 48, 50, 48);
                bowl.lineTo(34, 48);
                lower.moveTo(34, 48);
                lower.lineTo(54, 48);
                lower.curveTo(74, 48, 74, 80, 54, 80);
                lower.lineTo(34, 80);
                return union(line(34, 14, 34, 86), strokeOf(bowl, 13f), strokeOf(lower, 13f), serif);
            }
            case 'n':
                return union(polygon(24, 86, 24, 14, 40, 14, 60, 58, 60, 14, 76, 14, 76, 86, 60, 86, 40, 42, 40, 86), serif);
            case 'q': {
                Area ring = strokeOf(new java.awt.geom.Ellipse2D.Double(28, 18, 44, 52), 13f);
                return union(ring, line(54, 62, 74, 86), serif);
            }
            case 'k':
                return union(rect(26, 14, 15, 72), polygon(41, 50, 62, 14, 80, 14, 54, 56), polygon(48, 48, 82, 86, 64, 86, 41, 62), serif);
            default:
                return new Area();
        }
    }

    //  GOTHIC: spires, pointed arches and flared feet

    private static Area gothicBase() {
        return union(polygon(18, 92, 82, 92, 74, 82, 26, 82), rect(30, 78, 40, 5));
    }

    private static Area gothic(char t) {
        switch (t) {
            case 'p':
                return union(polygon(50, 6, 60, 26, 57, 38, 43, 38, 40, 26), rect(39, 38, 22, 4),
                        polygon(44, 42, 56, 42, 64, 80, 36, 80), gothicBase());
            case 'r':
                return union(polygon(28, 32, 28, 10, 37, 22, 42, 6, 50, 22, 58, 6, 63, 22, 72, 10, 72, 32),
                        rect(32, 32, 36, 6), polygon(36, 38, 64, 38, 68, 80, 32, 80), gothicBase());
            case 'b':
                return union(minus(polygon(50, 2, 66, 30, 60, 46, 40, 46, 34, 30),
                                rect(48, 17, 4, 15), rect(43, 22, 14, 4)),
                        rect(34, 46, 32, 5), polygon(42, 51, 58, 51, 64, 80, 36, 80), gothicBase());
            case 'n':
                return minus(union(polygon(30, 82, 32, 56, 40, 46, 24, 48, 14, 38, 34, 20, 34, 4, 46, 16, 60, 20, 74, 38, 72, 82),
                        gothicBase()), polygon(34, 32, 41, 30, 39, 37));
            case 'q':
                return union(polygon(32, 50, 20, 12, 36, 34, 42, 4, 50, 32, 58, 4, 64, 34, 80, 12, 68, 50),
                        rect(32, 50, 36, 5), polygon(38, 55, 62, 55, 68, 80, 32, 80), gothicBase());
            case 'k':
                return union(rect(47, 2, 6, 28), rect(39, 9, 22, 6), polygon(50, 28, 66, 38, 64, 50, 36, 50, 34, 38),
                        rect(32, 50, 36, 5), polygon(38, 55, 62, 55, 68, 80, 32, 80), gothicBase());
            default:
                return new Area();
        }
    }

    //  COIN: a round token with the piece cut out of it and a groove round the rim

    private static Area coin(char t) {
        Area disc = circle(50, 52, 40);
        Area groove = minus(circle(50, 52, 36), circle(50, 52, 33));
        java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform();
        at.translate(50, 52);
        at.scale(0.6, 0.6);
        at.translate(-50, -50);
        Area icon = new Area(at.createTransformedShape(minimal(t)));
        return minus(disc, groove, icon);
    }

    //  BLOCKS: pieces stacked from rectangular building blocks

    private static Area blocks(char t) {
        Area base = rect(16, 80, 68, 12);
        switch (t) {
            case 'p':
                return union(rect(40, 12, 20, 18), rect(34, 30, 32, 14), rect(28, 44, 44, 16), rect(22, 60, 56, 20), base);
            case 'r':
                return union(rect(24, 10, 14, 16), rect(43, 10, 14, 16), rect(62, 10, 14, 16), rect(24, 26, 52, 10),
                        rect(30, 36, 40, 30), rect(24, 66, 52, 14), base);
            case 'b':
                return minus(union(rect(44, 4, 12, 10), rect(38, 14, 24, 14), rect(34, 28, 32, 16), rect(28, 44, 44, 8),
                        rect(36, 52, 28, 28), base), rect(50, 24, 12, 5));
            case 'n':
                return minus(union(rect(30, 54, 44, 26), rect(40, 40, 34, 14), rect(26, 24, 36, 18), rect(10, 32, 18, 12),
                        rect(46, 10, 10, 14), rect(60, 30, 14, 12), base), rect(34, 28, 5, 5));
            case 'q':
                return union(rect(17, 16, 10, 12), rect(31, 10, 10, 18), rect(45, 6, 10, 22), rect(59, 10, 10, 18),
                        rect(73, 16, 10, 12), rect(17, 28, 66, 8), rect(26, 36, 48, 12), rect(32, 48, 36, 32), base);
            case 'k':
                return union(rect(44, 2, 12, 30), rect(33, 10, 34, 10), rect(30, 32, 40, 16), rect(36, 48, 28, 32),
                        rect(22, 62, 56, 18), base);
            default:
                return new Area();
        }
    }
}