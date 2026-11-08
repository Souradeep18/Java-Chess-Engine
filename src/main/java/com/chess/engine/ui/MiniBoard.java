package com.chess.engine.ui;

import com.chess.engine.engine.Board;
import com.chess.engine.engine.Move;
import com.chess.engine.engine.MoveGenerator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/*
  A small interactive board for lessons. Three behaviours:
  EXPLORE: click a piece to see its legal moves and play them;
  LINE: step through a prepared sequence of moves with the arrow buttons;
  FIXED: a diagram with highlighted squares and arrows.
 */
@SuppressWarnings("serial")
public class MiniBoard extends JPanel {

    enum Mode { EXPLORE, LINE, FIXED }

    private final BoardCanvas canvas = new BoardCanvas();
    private final JLabel caption = new JLabel(" ");
    private final JLabel status = UiKit.label(" ", Font.PLAIN, 12, UiKit.TEXT_DIM);
    private final JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
    private final UiKit.FlatButton back = new UiKit.FlatButton("◀", UiKit.PANEL_HI);
    private final UiKit.FlatButton forward = new UiKit.FlatButton("▶", UiKit.PANEL_HI);
    private final UiKit.FlatButton reset = new UiKit.FlatButton("Reset", UiKit.PANEL_HI);

    private Mode mode = Mode.FIXED;
    private String startFen;
    private Board board;
    private final List<Board> history = new ArrayList<>();
    private String[] line = new String[0];
    private int step;
    private int selected = -1;
    private final List<Integer> targets = new ArrayList<>();
    private int lastFrom = -1, lastTo = -1;
    private int[] marks = new int[0];
    private int[][] arrows = new int[0][];

    MiniBoard(int size) {
        super(new BorderLayout(0, 6));
        setOpaque(false);
        canvas.setPreferredSize(new Dimension(size, size));
        add(canvas, BorderLayout.CENTER);

        JPanel south = new JPanel();
        south.setOpaque(false);
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        caption.setForeground(UiKit.TEXT);
        caption.setFont(UiKit.font(Font.PLAIN, 13));
        caption.setAlignmentX(Component.CENTER_ALIGNMENT);
        status.setAlignmentX(Component.CENTER_ALIGNMENT);
        controls.setOpaque(false);
        for (UiKit.FlatButton b : new UiKit.FlatButton[]{back, forward, reset}) {
            b.setBorder(new EmptyBorder(4, 12, 4, 12));
            b.setFocusable(false);
            controls.add(b);
        }
        back.addActionListener(e -> stepTo(step - 1));
        forward.addActionListener(e -> stepTo(step + 1));
        reset.addActionListener(e -> restart());
        south.add(controls);
        south.add(Box.createVerticalStrut(4));
        south.add(status);
        south.add(caption);
        add(south, BorderLayout.SOUTH);
    }

    //  configuration

    void showExplore(String text, String fen, String markedSquares) {
        configure(Mode.EXPLORE, text, fen, new String[0], markedSquares, null);
    }

    void showLine(String text, String fen, String[] moves, String markedSquares) {
        configure(Mode.LINE, text, fen, moves, markedSquares, null);
    }

    void showFixed(String text, String fen, String markedSquares, String arrowMoves) {
        configure(Mode.FIXED, text, fen, new String[0], markedSquares, arrowMoves);
    }

    private void configure(Mode m, String text, String fen, String[] moves, String markedSquares, String arrowMoves) {
        mode = m;
        startFen = fen;
        line = moves;
        caption.setText("<html><div style='text-align:center;width:" + (canvas.getPreferredSize().width - 40) + "px'>"
                + text + "</div></html>");
        marks = parseSquares(markedSquares);
        arrows = parseArrows(arrowMoves);
        controls.setVisible(m != Mode.FIXED);
        back.setVisible(m == Mode.LINE);
        forward.setVisible(m == Mode.LINE);
        restart();
    }

    private void restart() {
        board = Board.fromFEN(startFen);
        history.clear();
        step = 0;
        selected = -1;
        targets.clear();
        lastFrom = lastTo = -1;
        updateStatus();
        refresh();
    }

    private static int[] parseSquares(String s) {
        if (s == null || s.trim().isEmpty()) return new int[0];
        String[] p = s.trim().split("\\s+");
        int[] r = new int[p.length];
        for (int i = 0; i < p.length; i++) r[i] = ChessUtil.squareIndex(p[i]);
        return r;
    }

    private static int[][] parseArrows(String s) {
        if (s == null || s.trim().isEmpty()) return new int[0][];
        String[] p = s.trim().split("\\s+");
        int[][] r = new int[p.length][];
        for (int i = 0; i < p.length; i++) {
            r[i] = new int[]{ChessUtil.squareIndex(p[i].substring(0, 2)), ChessUtil.squareIndex(p[i].substring(2, 4))};
        }
        return r;
    }

    //  behaviour

    private void stepTo(int target) {
        if (mode != Mode.LINE) return;
        target = Math.max(0, Math.min(line.length, target));
        board = Board.fromFEN(startFen);
        lastFrom = lastTo = -1;
        for (int i = 0; i < target; i++) {
            Move m = ChessUtil.fromUci(board, line[i]);
            if (m == null) { target = i; break; }
            MoveGenerator.makeMove(board, m);
            lastFrom = m.from;
            lastTo = m.to;
        }
        step = target;
        updateStatus();
        refresh();
    }

    private void updateStatus() {
        if (mode == Mode.LINE) {
            back.setEnabled(step > 0);
            forward.setEnabled(step < line.length);
            status.setText("Move " + step + " of " + line.length);
        } else if (mode == Mode.EXPLORE) {
            status.setText(ChessUtil.isCheckmate(board) ? "Checkmate!"
                    : ChessUtil.isStalemate(board) ? "Stalemate – a draw"
                    : "Click a piece, then a highlighted square");
        } else {
            status.setText(" ");
        }
    }

    private void squareClicked(int sq) {
        if (mode != Mode.EXPLORE) return;
        if (selected >= 0 && targets.contains(sq)) {
            Move m = null;
            for (Move c : MoveGenerator.generateLegalMoves(board)) {
                if (c.from == selected && c.to == sq && (c.promotion == '\0' || c.promotion == ' ' || Character.toLowerCase(c.promotion) == 'q')) { m = c; break; }
            }
            if (m != null) {
                MoveGenerator.makeMove(board, m);
                lastFrom = m.from;
                lastTo = m.to;
            }
            selected = -1;
            targets.clear();
            updateStatus();
            refresh();
            return;
        }
        char p = board.getPieceTypeAt(sq);
        selected = -1;
        targets.clear();
        if (p != '.' && Character.isUpperCase(p) == board.isWhiteToMove()) {
            for (Move c : MoveGenerator.generateLegalMoves(board)) {
                if (c.from == sq && !targets.contains(c.to)) targets.add(c.to);
            }
            if (!targets.isEmpty()) selected = sq;
        }
        refresh();
    }

    private void refresh() { canvas.repaint(); }

    //  painting

    private final class BoardCanvas extends JComponent {
        BoardCanvas() {
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    int cell = Math.min(getWidth(), getHeight()) / 8;
                    if (cell <= 0) return;
                    int col = e.getX() / cell, row = e.getY() / cell;
                    if (col > 7 || row > 7) return;
                    squareClicked((7 - row) * 8 + col);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int cell = Math.min(getWidth(), getHeight()) / 8;
            Color lightC = Themes.getBoard().light, darkC = Themes.getBoard().dark;
            Themes.PieceSet pieces = Themes.getPieces();
            g2.setFont(UiKit.font(Font.BOLD, Math.max(9, cell / 5)));
            FontMetrics fm = g2.getFontMetrics();
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    int sq = (7 - row) * 8 + col;
                    boolean dark = (col + (7 - row)) % 2 == 0;
                    int x = col * cell, y = row * cell;
                    g2.setColor(dark ? darkC : lightC);
                    g2.fillRect(x, y, cell, cell);
                    if (sq == lastFrom || sq == lastTo) {
                        g2.setColor(new Color(0xcd, 0xd2, 0x6a, 120));
                        g2.fillRect(x, y, cell, cell);
                    }
                    for (int m : marks) {
                        if (m == sq) {
                            g2.setColor(new Color(0x36, 0x92, 0xe7, 130));
                            g2.fillRect(x, y, cell, cell);
                        }
                    }
                    if (sq == selected) {
                        g2.setColor(new Color(0x62, 0x99, 0x24, 150));
                        g2.fillRect(x, y, cell, cell);
                    }
                    g2.setColor(dark ? lightC : darkC);
                    if (col == 0) g2.drawString(String.valueOf(sq / 8 + 1), x + 2, y + fm.getAscent());
                    if (row == 7) g2.drawString(String.valueOf((char) ('a' + col)), x + cell - fm.stringWidth("a") - 2, y + cell - 3);
                }
            }
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    int sq = (7 - row) * 8 + col;
                    char p = board.getPieceTypeAt(sq);
                    if (p != '.') pieces.paint(g2, p, col * cell, row * cell, cell);
                }
            }
            for (int t : targets) {
                int col = t & 7, row = 7 - (t >> 3);
                boolean capture = board.getPieceTypeAt(t) != '.';
                g2.setColor(new Color(0x14, 0x55, 0x1e, 130));
                if (capture) {
                    g2.setStroke(new BasicStroke(Math.max(3f, cell / 10f)));
                    g2.drawOval(col * cell + 3, row * cell + 3, cell - 6, cell - 6);
                } else {
                    int d = cell / 3;
                    g2.fillOval(col * cell + (cell - d) / 2, row * cell + (cell - d) / 2, d, d);
                }
            }
            g2.setStroke(new BasicStroke(Math.max(4f, cell / 8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int[] a : arrows) drawArrow(g2, a[0], a[1], cell);
            g2.dispose();
        }

        private void drawArrow(Graphics2D g2, int from, int to, int cell) {
            double x1 = (from & 7) * cell + cell / 2.0, y1 = (7 - (from >> 3)) * cell + cell / 2.0;
            double x2 = (to & 7) * cell + cell / 2.0, y2 = (7 - (to >> 3)) * cell + cell / 2.0;
            double ang = Math.atan2(y2 - y1, x2 - x1);
            double head = cell * 0.34;
            double ex = x2 - Math.cos(ang) * head * 0.8, ey = y2 - Math.sin(ang) * head * 0.8;
            g2.setColor(new Color(0xf5, 0x9b, 0x1e, 200));
            g2.draw(new java.awt.geom.Line2D.Double(x1, y1, ex, ey));
            Polygon tri = new Polygon();
            tri.addPoint((int) x2, (int) y2);
            tri.addPoint((int) (x2 - Math.cos(ang - 0.45) * head), (int) (y2 - Math.sin(ang - 0.45) * head));
            tri.addPoint((int) (x2 - Math.cos(ang + 0.45) * head), (int) (y2 - Math.sin(ang + 0.45) * head));
            g2.fillPolygon(tri);
        }
    }
}