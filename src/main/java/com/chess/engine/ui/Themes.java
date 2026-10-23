package com.chess.engine.ui;

import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/*
 Board colour themes, piece sets and presets, plus the saved choice.
 The first entry of {@link #BOARDS} and of {@link #PIECES} is the default look of the application
  (the brown board with the classic glyph pieces). The choice is stored with
  {@link java.util.prefs.Preferences}, so it survives closing the application and only changes when the
  user saves a different theme.
 
  Adding a board: add one {@code new BoardTheme(...)} line to {@link #BOARDS}.
  Adding a piece set: add one {@code new GlyphSet(...)} line to {@link #PIECES} (or write another
  {@link PieceSet}, for example one that draws images).
 */
public final class Themes {

    private Themes() {
    }


    //  Board themes


    public static final class BoardTheme {
        public final String id;
        public final String name;
        public final Color light;
        public final Color dark;

        public BoardTheme(String id, String name, Color light, Color dark) {
            this.id = id;
            this.name = name;
            this.light = light;
            this.dark = dark;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static Color c(int r, int g, int b) {
        return new Color(r, g, b);
    }

    /** The first one is the default. */
    public static final List<BoardTheme> BOARDS = Collections.unmodifiableList(java.util.Arrays.asList(
            new BoardTheme("brown", "Brown", c(240, 217, 181), c(181, 136, 99)),
            new BoardTheme("green", "Green", c(238, 238, 210), c(118, 150, 86)),
            new BoardTheme("blue", "Blue", c(222, 227, 230), c(140, 162, 173)),
            new BoardTheme("slate", "Slate", c(178, 190, 205), c(76, 90, 112)),
            new BoardTheme("midnight", "Midnight", c(98, 106, 124), c(40, 46, 60)),
            new BoardTheme("ice", "Ice", c(238, 243, 248), c(151, 184, 212)),
            new BoardTheme("gray", "Gray", c(220, 220, 220), c(140, 140, 140)),
            new BoardTheme("walnut", "Walnut", c(214, 177, 134), c(120, 78, 48)),
            new BoardTheme("sand", "Sand", c(236, 226, 196), c(176, 152, 104)),
            new BoardTheme("orange", "Orange", c(248, 220, 185), c(215, 128, 64)),
            new BoardTheme("red", "Crimson", c(240, 225, 225), c(170, 60, 60)),
            new BoardTheme("pink", "Rose", c(252, 236, 240), c(232, 160, 180)),
            new BoardTheme("purple", "Purple", c(235, 228, 245), c(138, 110, 176)),
            new BoardTheme("forest", "Forest", c(213, 226, 196), c(76, 110, 60)),
            new BoardTheme("teal", "Teal", c(214, 236, 232), c(70, 140, 134)),
            new BoardTheme("olive", "Olive", c(232, 232, 200), c(128, 128, 70)),
            new BoardTheme("coffee", "Coffee", c(222, 200, 176), c(98, 66, 46)),
            new BoardTheme("lavender", "Lavender", c(240, 238, 250), c(150, 150, 205)),
            new BoardTheme("charcoal", "Charcoal", c(160, 160, 160), c(60, 60, 60)),
            new BoardTheme("mint", "Mint", c(236, 250, 240), c(120, 190, 150))
    ));


    //  Piece sets


    /* Something that can draw a chess piece into a square. */
    public abstract static class PieceSet {
        public final String id;
        public final String name;

        protected PieceSet(String id, String name) {
            this.id = id;
            this.name = name;
        }

        /*
          The classic set is the one the squares draw themselves (as text); every other set is painted
          by {@link #paint}.
         */
        public boolean isClassic() {
            return false;
        }

        /** Paints {@code piece} (FEN letter, upper case = White) in the square x, y, size x size. */
        public abstract void paint(Graphics2D g, char piece, int x, int y, int size);

        @Override
        public String toString() {
            return name;
        }
    }

    private static final String SYMBOL_FONT = pickSymbolFont();

    private static String pickSymbolFont() {
        String[] wanted = {"Segoe UI Symbol", "DejaVu Sans", "Noto Sans Symbols2", "Arial Unicode MS", "Apple Symbols"};
        java.util.Set<String> have = new java.util.HashSet<>(java.util.Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String f : wanted) {
            if (have.contains(f)) {
                return f;
            }
        }
        return Font.DIALOG;
    }

    private static String filledGlyph(char piece) {
        switch (Character.toLowerCase(piece)) {
            case 'k': return "\u265A";
            case 'q': return "\u265B";
            case 'r': return "\u265C";
            case 'b': return "\u265D";
            case 'n': return "\u265E";
            case 'p': return "\u265F";
            default: return "";
        }
    }

    private static String hollowGlyph(char piece) {
        switch (Character.toLowerCase(piece)) {
            case 'k': return "\u2654";
            case 'q': return "\u2655";
            case 'r': return "\u2656";
            case 'b': return "\u2657";
            case 'n': return "\u2658";
            case 'p': return "\u2659";
            default: return "";
        }
    }

    /* The look the application has always had: white pieces hollow, black pieces solid (font glyphs as text). */
    public static final class ClassicSet extends PieceSet {
        public ClassicSet() {
            super("classic", "Classic");
        }

        @Override
        public boolean isClassic() {
            return true;
        }

        @Override
        public void paint(Graphics2D g, char piece, int x, int y, int size) {
            boolean white = Character.isUpperCase(piece);
            String s = white ? hollowGlyph(piece) : filledGlyph(piece);
            if (s.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.BLACK);
            drawGlyph(g2, s, x, y, size, false);
            g2.dispose();
        }
    }

    /* Pieces drawn as filled outlines of the solid glyphs, with their own fill, outline and shadow colours. */
    public static final class GlyphSet extends PieceSet {
        private final Color whiteFill;
        private final Color whiteLine;
        private final Color blackFill;
        private final Color blackLine;
        private final float lineWidth;
        private final boolean shadow;

        public GlyphSet(String id, String name, Color whiteFill, Color whiteLine, Color blackFill, Color blackLine,
                        float lineWidth, boolean shadow) {
            super(id, name);
            this.whiteFill = whiteFill;
            this.whiteLine = whiteLine;
            this.blackFill = blackFill;
            this.blackLine = blackLine;
            this.lineWidth = lineWidth;
            this.shadow = shadow;
        }

        @Override
        public void paint(Graphics2D g, char piece, int x, int y, int size) {
            String s = filledGlyph(piece);
            if (s.isEmpty()) {
                return;
            }

            boolean white = Character.isUpperCase(piece);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            Shape outline = glyphOutline(s, x, y, size);

            if (shadow) {
                Graphics2D sh = (Graphics2D) g2.create();
                sh.translate(Math.max(1, size / 40.0), Math.max(1, size / 40.0));
                sh.setColor(new Color(0, 0, 0, 70));
                sh.fill(outline);
                sh.dispose();
            }

            g2.setColor(white ? whiteFill : blackFill);
            g2.fill(outline);
            g2.setColor(white ? whiteLine : blackLine);
            g2.setStroke(new BasicStroke(Math.max(0.6f, lineWidth * size / 60f)));
            g2.draw(outline);
            g2.dispose();
        }
    }

    /*
      Pieces with their own shapes (see PieceShapes): a shape family drawn with fill, outline and shadow colours.
     */
    public static final class ShapeSet extends PieceSet {
        private final PieceShapes.Family family;
        private final Color whiteFill;
        private final Color whiteLine;
        private final Color blackFill;
        private final Color blackLine;
        private final float lineWidth;
        private final boolean shadow;

        public ShapeSet(String id, String name, PieceShapes.Family family, Color whiteFill, Color whiteLine,
                        Color blackFill, Color blackLine, float lineWidth, boolean shadow) {
            super(id, name);
            this.family = family;
            this.whiteFill = whiteFill;
            this.whiteLine = whiteLine;
            this.blackFill = blackFill;
            this.blackLine = blackLine;
            this.lineWidth = lineWidth;
            this.shadow = shadow;
        }

        @Override
        public void paint(Graphics2D g, char piece, int x, int y, int size) {
            Shape unit = PieceShapes.shape(family, piece);
            boolean white = Character.isUpperCase(piece);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            double scale = size * 0.90 / 100.0;
            AffineTransform at = new AffineTransform();
            at.translate(x + size * 0.05, y + size * 0.05);
            at.scale(scale, scale);
            Shape shape = at.createTransformedShape(unit);

            if (shadow) {
                Graphics2D sh = (Graphics2D) g2.create();
                sh.translate(Math.max(1, size / 36.0), Math.max(1, size / 36.0));
                sh.setColor(new Color(0, 0, 0, 70));
                sh.fill(shape);
                sh.dispose();
            }

            g2.setColor(white ? whiteFill : blackFill);
            g2.fill(shape);
            g2.setColor(white ? whiteLine : blackLine);
            g2.setStroke(new BasicStroke(Math.max(0.8f, lineWidth * size / 50f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(shape);
            g2.dispose();
        }
    }

    /* The first one is the default. */
    public static final List<PieceSet> PIECES = Collections.unmodifiableList(java.util.Arrays.asList(
            new ClassicSet(),
            // own shapes: Staunton
            new ShapeSet("staunton", "Staunton", PieceShapes.Family.STAUNTON, c(250, 247, 238), c(60, 48, 38), c(78, 70, 64), c(20, 16, 12), 1.5f, true),
            new ShapeSet("staunton-wood", "Staunton Wood", PieceShapes.Family.STAUNTON, c(238, 208, 160), c(92, 56, 28), c(104, 62, 34), c(38, 20, 8), 1.5f, true),
            new ShapeSet("staunton-gold", "Staunton Gold", PieceShapes.Family.STAUNTON, c(238, 196, 70), c(112, 80, 12), c(36, 36, 40), c(0, 0, 0), 1.5f, true),
            // own shapes: Geometric
            new ShapeSet("geometric", "Geometric", PieceShapes.Family.GEOMETRIC, c(240, 242, 245), c(50, 56, 66), c(52, 58, 70), c(14, 18, 26), 1.5f, false),
            new ShapeSet("geometric-blue", "Geometric Blue", PieceShapes.Family.GEOMETRIC, c(232, 242, 252), c(26, 60, 110), c(36, 96, 176), c(8, 28, 70), 1.5f, false),
            new ShapeSet("geometric-red", "Geometric Red", PieceShapes.Family.GEOMETRIC, c(252, 236, 226), c(110, 40, 30), c(190, 52, 44), c(80, 12, 10), 1.5f, false),
            //  own shapes: Chunky
            new ShapeSet("chunky", "Chunky", PieceShapes.Family.CHUNKY, c(252, 252, 250), c(70, 70, 70), c(62, 62, 66), c(10, 10, 12), 1.4f, true),
            new ShapeSet("chunky-candy", "Chunky Candy", PieceShapes.Family.CHUNKY, c(255, 226, 236), c(150, 60, 100), c(120, 90, 200), c(50, 24, 110), 1.4f, true),
            new ShapeSet("chunky-mint", "Chunky Mint", PieceShapes.Family.CHUNKY, c(226, 250, 238), c(40, 110, 76), c(30, 130, 96), c(8, 56, 40), 1.4f, true),
            // own shapes: ten more families, each with its own colourway
            new ShapeSet("slim", "Slim", PieceShapes.Family.SLIM, c(246, 244, 240), c(70, 70, 82), c(54, 58, 68), c(14, 16, 22), 1.3f, true),
            new ShapeSet("stout", "Stout", PieceShapes.Family.STOUT, c(244, 226, 196), c(124, 72, 40), c(150, 84, 52), c(70, 30, 14), 1.6f, true),
            new ShapeSet("minimal", "Minimal", PieceShapes.Family.MINIMAL, c(255, 255, 255), c(30, 30, 30), c(38, 38, 38), c(0, 0, 0), 2.0f, false),
            new ShapeSet("pixel", "Pixel", PieceShapes.Family.PIXEL, c(110, 220, 255), c(14, 30, 80), c(255, 100, 160), c(80, 8, 50), 1.2f, false),
            new ShapeSet("art-deco", "Art Deco", PieceShapes.Family.ART_DECO, c(236, 210, 132), c(122, 90, 30), c(30, 32, 42), c(214, 174, 74), 1.5f, true),
            new ShapeSet("bubble", "Bubble", PieceShapes.Family.BUBBLE, c(255, 218, 232), c(196, 96, 150), c(160, 206, 255), c(56, 98, 182), 1.6f, true),
            new ShapeSet("letters", "Letters", PieceShapes.Family.LETTERS, c(244, 240, 228), c(62, 52, 42), c(64, 56, 52), c(18, 14, 10), 1.4f, false),
            new ShapeSet("gothic", "Gothic", PieceShapes.Family.GOTHIC, c(216, 220, 232), c(50, 58, 84), c(66, 34, 62), c(22, 6, 22), 1.5f, true),
            new ShapeSet("coin", "Coin", PieceShapes.Family.COIN, c(236, 188, 66), c(120, 84, 16), c(150, 94, 54), c(58, 28, 8), 1.4f, true),
            new ShapeSet("blocks", "Blocks", PieceShapes.Family.BLOCKS, c(255, 214, 64), c(132, 98, 0), c(52, 112, 204), c(12, 40, 104), 1.5f, false),
            // the font-glyph sets (same shapes, different colours)
            new GlyphSet("ivory", "Ivory & Ebony", c(252, 252, 252), c(47, 47, 47), c(70, 70, 66), c(0, 0, 0), 1.6f, true),
            new GlyphSet("cream", "Cream & Brown", c(248, 234, 205), c(90, 62, 40), c(96, 62, 38), c(40, 24, 12), 1.6f, true),
            new GlyphSet("gold", "Gold & Black", c(236, 190, 52), c(110, 78, 10), c(34, 34, 34), c(0, 0, 0), 1.6f, true),
            new GlyphSet("silver", "Silver & Navy", c(222, 226, 234), c(60, 70, 90), c(34, 58, 98), c(8, 18, 40), 1.6f, true),
            new GlyphSet("red", "White & Red", c(255, 255, 255), c(80, 30, 30), c(184, 38, 38), c(70, 8, 8), 1.6f, true),
            new GlyphSet("blue", "White & Blue", c(255, 255, 255), c(30, 50, 90), c(30, 100, 190), c(8, 30, 80), 1.6f, true),
            new GlyphSet("green", "Mint & Forest", c(232, 247, 235), c(30, 70, 40), c(40, 120, 60), c(10, 48, 20), 1.6f, true),
            new GlyphSet("pearl", "Pearl & Graphite", c(242, 242, 242), c(110, 110, 110), c(86, 86, 90), c(30, 30, 34), 1.2f, false),
            new GlyphSet("sand", "Sand & Charcoal", c(240, 226, 190), c(120, 96, 50), c(66, 62, 58), c(20, 20, 20), 1.4f, false),
            new GlyphSet("rose", "Rose & Plum", c(255, 236, 242), c(130, 60, 90), c(112, 48, 98), c(50, 14, 44), 1.6f, true),
            new GlyphSet("outline", "Bold Outline", c(255, 255, 255), c(0, 0, 0), c(40, 40, 40), c(0, 0, 0), 3.2f, false),
            new GlyphSet("neon", "Neon", c(120, 255, 240), c(0, 90, 90), c(255, 90, 200), c(100, 0, 70), 1.8f, true)
    ));

    //  glyph drawing helpers (shared by the sets) 

    private static final Map<String, Shape> OUTLINE_CACHE = new HashMap<>();

    /* The outline of a glyph centred in the square (x, y, size). Outlines are cached per glyph and size. */
    static synchronized Shape glyphOutline(String symbol, int x, int y, int size) {
        String key = symbol + "@" + size;
        Shape base = OUTLINE_CACHE.get(key);
        if (base == null) {
            Font font = new Font(SYMBOL_FONT, Font.PLAIN, Math.max(8, Math.round(size * 0.80f)));
            GlyphVector gv = font.createGlyphVector(new java.awt.font.FontRenderContext(null, true, true), symbol);
            Rectangle2D b = gv.getVisualBounds();
            double tx = (size - b.getWidth()) / 2.0 - b.getX();
            double ty = (size - b.getHeight()) / 2.0 - b.getY();
            base = gv.getOutline((float) tx, (float) ty);
            OUTLINE_CACHE.put(key, base);
        }
        return AffineTransform.getTranslateInstance(x, y).createTransformedShape(base);
    }

    /* Draws a glyph as text (used by the classic set so it looks like the text the squares draw). */
    private static void drawGlyph(Graphics2D g2, String symbol, int x, int y, int size, boolean unused) {
        Font font = new Font(SYMBOL_FONT, Font.PLAIN, Math.max(8, Math.round(size * 0.78f)));
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int tx = x + (size - fm.stringWidth(symbol)) / 2;
        int ty = y + (size - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(symbol, tx, ty);
    }

    
    //  Presets (a board and a piece set that go well together)

    public static final class Preset {
        public final String name;
        public final String boardId;
        public final String pieceId;

        public Preset(String name, String boardId, String pieceId) {
            this.name = name;
            this.boardId = boardId;
            this.pieceId = pieceId;
        }
    }

    public static final List<Preset> PRESETS = Collections.unmodifiableList(java.util.Arrays.asList(
            new Preset("Classic", "brown", "classic"),
            new Preset("Green", "green", "ivory"),
            new Preset("Wood", "walnut", "cream"),
            new Preset("Blue", "blue", "silver"),
            new Preset("Midnight Gold", "midnight", "gold"),
            new Preset("Ice", "ice", "blue"),
            new Preset("Marble", "gray", "pearl"),
            new Preset("Sunset", "orange", "red"),
            new Preset("Purple", "purple", "rose"),
            new Preset("Forest", "forest", "green"),
            new Preset("Coffee", "coffee", "sand"),
            new Preset("Neon Night", "charcoal", "neon")
    ));


    //  The saved choice

    private static final String KEY_BOARD = "theme.board";
    private static final String KEY_PIECES = "theme.pieces";

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(Themes.class);
    }

    private static BoardTheme currentBoard;
    private static PieceSet currentPieces;
    private static final List<Runnable> LISTENERS = new ArrayList<>();

    public static BoardTheme findBoard(String id) {
        for (BoardTheme b : BOARDS) {
            if (b.id.equals(id)) {
                return b;
            }
        }
        return null;
    }

    public static PieceSet findPieces(String id) {
        for (PieceSet p : PIECES) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    private static synchronized void ensureLoaded() {
        if (currentBoard != null) {
            return;
        }
        BoardTheme b = null;
        PieceSet p = null;
        try {
            Preferences prefs = prefs();
            b = findBoard(prefs.get(KEY_BOARD, ""));
            p = findPieces(prefs.get(KEY_PIECES, ""));
        } catch (RuntimeException ignored) {
            // preferences not available: use the defaults
        }
        currentBoard = b != null ? b : BOARDS.get(0);
        currentPieces = p != null ? p : PIECES.get(0);
    }

    public static BoardTheme getBoard() {
        ensureLoaded();
        return currentBoard;
    }

    public static PieceSet getPieces() {
        ensureLoaded();
        return currentPieces;
    }

    /* Makes the choice current, stores it for the next run and tells the listeners. */
    public static void save(BoardTheme board, PieceSet pieces) {
        ensureLoaded();
        currentBoard = board != null ? board : BOARDS.get(0);
        currentPieces = pieces != null ? pieces : PIECES.get(0);
        try {
            Preferences prefs = prefs();
            prefs.put(KEY_BOARD, currentBoard.id);
            prefs.put(KEY_PIECES, currentPieces.id);
            prefs.flush();
        } catch (Exception ignored) {
            // the theme still applies for this run
        }
        for (Runnable r : new ArrayList<>(LISTENERS)) {
            r.run();
        }
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }
}