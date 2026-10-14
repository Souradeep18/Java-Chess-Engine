package com.chess.engine.ui;

import com.chess.engine.engine.Board;
import com.chess.engine.engine.Move;

import javax.swing.*;
import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;

/*
  A small borderless window with a miniature chess board. It is shown next to a move the
  mouse is hovering over and never takes focus, so it does not disturb the main window.
 */
@SuppressWarnings("serial")
public class MiniBoardPopup extends JWindow {

    private static final int SQUARE = 26;

    private static final Color LIGHT_SQUARE = new Color(240, 217, 181);
    private static final Color DARK_SQUARE = new Color(181, 136, 99);
    private static final Color LIGHT_LAST_MOVE = new Color(205, 210, 106);
    private static final Color DARK_LAST_MOVE = new Color(170, 162, 58);

    private static final Font PIECE_FONT = new Font("Segoe UI Symbol", Font.PLAIN, SQUARE - 2);

    private final BoardView view = new BoardView();

    public MiniBoardPopup(Window owner) {
        super(owner);
        setFocusableWindowState(false);
        getContentPane().add(view);
        pack();
    }

    /*
      Shows the position. The popup is placed under anchorOnScreen (above it when
     there is no room below), so the mouse pointer is never on top of the popup.
     */
    public void showPosition(Board board, Move lastMove, boolean flipped, Rectangle anchorOnScreen) {
        view.set(board, lastMove, flipped);
        pack();

        int w = getWidth();
        int h = getHeight();

        GraphicsConfiguration gc = getOwner() != null ? getOwner().getGraphicsConfiguration() : null;
        Rectangle screen = gc != null ? gc.getBounds()
                : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

        int x = anchorOnScreen.x + anchorOnScreen.width / 2 - w / 2;
        int y = anchorOnScreen.y + anchorOnScreen.height + 6;

        if (y + h > screen.y + screen.height) {
            y = anchorOnScreen.y - h - 6;
        }

        x = Math.max(screen.x, Math.min(x, screen.x + screen.width - w));
        y = Math.max(screen.y, y);

        setLocation(x, y);

        if (!isVisible()) {
            setVisible(true);
        }
        view.repaint();
    }

    public void hidePopup() {
        if (isVisible()) {
            setVisible(false);
        }
    }

    private static final class BoardView extends JPanel {

        private Board board;
        private Move lastMove;
        private boolean flipped;

        BoardView() {
            setPreferredSize(new Dimension(SQUARE * 8 + 4, SQUARE * 8 + 4));
            setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60), 2));
            setOpaque(true);
        }

        void set(Board board, Move lastMove, boolean flipped) {
            this.board = board;
            this.lastMove = lastMove;
            this.flipped = flipped;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (board == null) {
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            Insets in = getInsets();

            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    int file = flipped ? 7 - col : col;
                    int rank = flipped ? row : 7 - row;
                    int square = rank * 8 + file;

                    boolean dark = (rank + file) % 2 == 0;
                    boolean highlighted = lastMove != null && (square == lastMove.from || square == lastMove.to);

                    if (highlighted) {
                        g2.setColor(dark ? DARK_LAST_MOVE : LIGHT_LAST_MOVE);
                    } else if (board.isRacingKings() && rank == 7) {
                        g2.setColor(HillStyle.raisedRankColor(dark));
                    } else if (board.isKingOfTheHill() && HillStyle.isHillSquare(square)) {
                        g2.setColor(HillStyle.color(dark));
                    } else {
                        g2.setColor(dark ? DARK_SQUARE : LIGHT_SQUARE);
                    }

                    int x = in.left + col * SQUARE;
                    int y = in.top + row * SQUARE;
                    g2.fillRect(x, y, SQUARE, SQUARE);
                }
            }

            // King of the Hill: the lifted centre is drawn under the pieces
            if (board.isKingOfTheHill()) {
                for (int row = 0; row < 8; row++) {
                    for (int col = 0; col < 8; col++) {
                        Graphics2D cell = (Graphics2D) g2.create();
                        cell.translate(in.left + col * SQUARE, in.top + row * SQUARE);
                        HillStyle.paintLift(cell, SQUARE, SQUARE, col, row);
                        cell.dispose();
                    }
                }
            }

            // Racing Kings: the raised 8th rank
            if (board.isRacingKings()) {
                for (int row = 0; row < 8; row++) {
                    for (int col = 0; col < 8; col++) {
                        Graphics2D cell = (Graphics2D) g2.create();
                        cell.translate(in.left + col * SQUARE, in.top + row * SQUARE);
                        HillStyle.paintRaisedRank(cell, SQUARE, SQUARE, row, flipped ? 7 : 0);
                        cell.dispose();
                    }
                }
            }

            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    int file = flipped ? 7 - col : col;
                    int rank = flipped ? row : 7 - row;
                    char piece = board.getPiece(rank * 8 + file);
                    if (piece != '.') {
                        drawPiece(g2, piece, in.left + col * SQUARE, in.top + row * SQUARE);
                    }
                }
            }

            g2.dispose();
        }

        private void drawPiece(Graphics2D g2, char piece, int x, int y) {
            String symbol = switch (Character.toLowerCase(piece)) {
                case 'k' -> "\u265A";
                case 'q' -> "\u265B";
                case 'r' -> "\u265C";
                case 'b' -> "\u265D";
                case 'n' -> "\u265E";
                case 'p' -> "\u265F";
                default -> "";
            };

            if (symbol.isEmpty()) {
                return;
            }

            // Drawn as an outline so the same (filled) glyph serves both colours and is never replaced by a colour emoji.
            GlyphVector glyphs = PIECE_FONT.createGlyphVector(g2.getFontRenderContext(), symbol);
            Rectangle2D bounds = glyphs.getVisualBounds();

            float tx = (float) (x + (SQUARE - bounds.getWidth()) / 2.0 - bounds.getX());
            float ty = (float) (y + (SQUARE - bounds.getHeight()) / 2.0 - bounds.getY());
            Shape outline = glyphs.getOutline(tx, ty);

            boolean white = Character.isUpperCase(piece);

            g2.setColor(white ? new Color(252, 252, 252) : new Color(105, 105, 95));
            g2.fill(outline);

            g2.setColor(white ? new Color(47, 47, 47) : Color.BLACK);
            g2.setStroke(new BasicStroke(0.6f));
            g2.draw(outline);
        }
    }
}