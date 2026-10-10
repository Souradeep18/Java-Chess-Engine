package com.chess.engine.ui;

import com.chess.engine.engine.Board;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/*
  Knight's tour mode.
 
    The side panel is replaced by two knights (white / black) and the message "pick a knight".
    The chosen knight is dragged (or clicked) onto any square of an empty board.
    The user is asked whether to change the knight's position.
    The knight then tours the board, jumping once per second. The start square is marked red
        and the path is drawn as a line from the start square through every square visited.
   When the knight is back on its start square the red mark disappears and
        "The knights tour is over" is shown.
  The tour is a closed (re-entrant) knight's tour: it visits all 64 squares exactly once and
  ends on the square it started from, whichever square that is.
 */
public class KnightsTour {

    private static final int JUMP_DELAY_MS = 1000;

    private static final String PICK_MESSAGE = "pick a knight";
    private static final String TOUR_MESSAGE = "Knight's tour in progress...";

    /* A verified closed knight's tour (square = rank * 8 + file, a1 = 0). The tour that is
     actually used is a random reflection/rotation of it, in a random direction, rotated so
     that it begins on the square the user chose.*/
    private static final int[] BASE_TOUR = {
            0, 10, 4, 14, 31, 46, 63, 53, 47, 62, 52, 58, 48, 33, 16, 1,
            11, 5, 15, 21, 6, 23, 38, 55, 61, 51, 57, 40, 50, 56, 41, 24,
            9, 3, 20, 26, 32, 49, 59, 42, 36, 30, 13, 7, 22, 37, 43, 60,
            54, 39, 45, 28, 18, 8, 25, 35, 29, 19, 34, 44, 27, 12, 2, 17};

    private enum State { IDLE, PICKING, CONFIRMING, RUNNING }

    private final JFrame owner;
    private final ChessBoardPanel boardPanel;
    private final JPanel sidePanel;
    private final Runnable onClose;
    private final Random random = new Random();

    // side-panel UI
    private final JPanel tourSidePanel = new JPanel();
    private final JLabel messageLabel = new JLabel(PICK_MESSAGE);
    private final Map<JButton, Character> knightButtons = new LinkedHashMap<>();
    private final Map<Component, Boolean> hiddenComponents = new LinkedHashMap<>();

    private ChessBoardPanel.PieceEditListener savedEditListener;

    private State state = State.IDLE;
    private char selectedKnight = '\0';

    private int startSquare = -1;
    private char knightPiece = 'N';
    private boolean markStartSquare = false;

    private int[] path;                                
    private int pathIndex;
    private final List<Integer> visited = new ArrayList<>();
    private Timer timer;

    public KnightsTour(JFrame owner, ChessBoardPanel boardPanel, JPanel sidePanel, Runnable onClose) {
        this.owner = owner;
        this.boardPanel = boardPanel;
        this.sidePanel = sidePanel;
        this.onClose = onClose;

        buildTourSidePanel();
    }

    public boolean isActive() {
        return state != State.IDLE;
    }

    // start / stop

    public void start() {
        if (isActive()) {
            return;
        }

        // Hide everything on the side panel; only the knights and the message stay
        hiddenComponents.clear();
        for (Component c : sidePanel.getComponents()) {
            hiddenComponents.put(c, c.isVisible());
            c.setVisible(false);
        }
        sidePanel.add(tourSidePanel, 0);
        tourSidePanel.setVisible(true);
        sidePanel.revalidate();
        sidePanel.repaint();

        // Reuse the board's editor mode: it routes clicks and palette drops to a listener
        savedEditListener = boardPanel.getPieceEditListener();
        boardPanel.setPieceEditListener(new ChessBoardPanel.PieceEditListener() {
            @Override
            public void onSquareClicked(int square) {
                if (state == State.PICKING && selectedKnight != '\0') {
                    placeKnight(square, selectedKnight);
                }
            }

            @Override
            public void onPieceDropped(int square, char piece) {
                if (state == State.PICKING && (piece == 'N' || piece == 'n')) {
                    placeKnight(square, piece);
                }
            }
        });
        boardPanel.setOverlayPainter(this::paintOverlay);
        boardPanel.setEditorMode(true);

        startSquare = -1;
        markStartSquare = false;
        visited.clear();
        showEmptyBoard();

        beginPicking();
    }

    /*Ends the tour (also used when the user presses the tour button during a tour). */
    public void cancel() {
        finish();
    }

    private void finish() {
        if (!isActive()) {
            return;
        }

        if (timer != null) {
            timer.stop();
            timer = null;
        }
        state = State.IDLE;
        selectedKnight = '\0';
        markStartSquare = false;
        visited.clear();

        boardPanel.setOverlayPainter(null);
        boardPanel.setEditorMode(false);
        boardPanel.setPieceEditListener(savedEditListener);

        sidePanel.remove(tourSidePanel);
        for (Map.Entry<Component, Boolean> e : hiddenComponents.entrySet()) {
            e.getKey().setVisible(e.getValue());
        }
        hiddenComponents.clear();
        sidePanel.revalidate();
        sidePanel.repaint();

        if (onClose != null) {
            onClose.run();
        }
    }

    // ------------------------------------------------------------------ picking a knight

    private void buildTourSidePanel() {
        tourSidePanel.setLayout(new BoxLayout(tourSidePanel, BoxLayout.Y_AXIS));
        tourSidePanel.setOpaque(false);
        tourSidePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        messageLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel knights = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        knights.setOpaque(false);
        knights.setAlignmentX(Component.LEFT_ALIGNMENT);
        knights.setMaximumSize(new Dimension(340, 100));

        knights.add(createKnightButton('N', "♘"));
        knights.add(createKnightButton('n', "♞"));

        tourSidePanel.add(messageLabel);
        tourSidePanel.add(Box.createVerticalStrut(14));
        tourSidePanel.add(knights);
    }

    private JButton createKnightButton(char piece, String symbol) {
        JButton button = new JButton(symbol);
        button.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 52));
        button.setPreferredSize(new Dimension(100, 90));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFocusable(false);
        button.setBackground(Color.WHITE);

        // Click: pick the knight up. Clicking the other knight cancels this selection.
        button.addActionListener(e -> toggleSelection(piece));

        makeDraggable(button, piece, symbol);

        knightButtons.put(button, piece);
        return button;
    }

    private void toggleSelection(char piece) {
        if (state != State.PICKING) {
            return;
        }
        selectedKnight = (selectedKnight == piece) ? '\0' : piece;
        updateKnightButtons();
    }

    private void updateKnightButtons() {
        boolean enabled = state == State.PICKING;

        for (Map.Entry<JButton, Character> e : knightButtons.entrySet()) {
            JButton button = e.getKey();
            boolean selected = enabled && selectedKnight == e.getValue();

            button.setEnabled(enabled);
            button.setBackground(selected ? new Color(175, 215, 245) : Color.WHITE);
            button.setBorder(selected
                    ? BorderFactory.createLineBorder(new Color(30, 110, 200), 3)
                    : UIManager.getBorder("Button.border"));
        }
    }

    /**
     * The knight can be dragged from the side panel and dropped on any square of the board.
     * The dragged data is the single piece character ('N' or 'n').
     */
    private void makeDraggable(JButton button, char piece, String symbol) {
        @SuppressWarnings("serial")
		TransferHandler handler = new TransferHandler() {
            @Override
            protected Transferable createTransferable(JComponent c) {
                return new StringSelection(String.valueOf(piece));
            }

            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }
        };

        handler.setDragImage(createDragImage(symbol));
        handler.setDragImageOffset(new Point(32, 32));
        button.setTransferHandler(handler);

        MouseAdapter dragStarter = new MouseAdapter() {
            private Point pressPoint;

            @Override
            public void mousePressed(MouseEvent e) {
                pressPoint = e.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pressPoint = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (state != State.PICKING || pressPoint == null || !button.isEnabled()) {
                    return;
                }
                if (pressPoint.distance(e.getPoint()) < 5) {
                    return;
                }

                pressPoint = null;

                // picking the knight up by dragging it selects it
                selectedKnight = piece;
                updateKnightButtons();

                handler.exportAsDrag(button, e, TransferHandler.COPY);
            }
        };

        button.addMouseListener(dragStarter);
        button.addMouseMotionListener(dragStarter);
    }

    private BufferedImage createDragImage(String symbol) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 56));
        g2.setColor(new Color(40, 40, 40));

        FontMetrics fm = g2.getFontMetrics();
        int x = (64 - fm.stringWidth(symbol)) / 2;
        int y = (64 - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(symbol, x, y);

        g2.dispose();
        return img;
    }

    private void beginPicking() {
        state = State.PICKING;
        selectedKnight = '\0';
        messageLabel.setText(PICK_MESSAGE);
        updateKnightButtons();
    }

    // placing and confirming

    private void placeKnight(int square, char piece) {
        startSquare = square;
        knightPiece = piece;

        // selection is disabled as soon as the knight is on the board
        state = State.CONFIRMING;
        selectedKnight = '\0';
        updateKnightButtons();

        showKnightAt(square);

        // Ask after the current click / drop event has finished
        SwingUtilities.invokeLater(this::askToChangePosition);
    }

    private void askToChangePosition() {
        if (state != State.CONFIRMING) {
            return;
        }

        int answer = JOptionPane.showConfirmDialog(
                owner,
                "Do you want to change the knight's position?",
                "Knight's Tour",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (state != State.CONFIRMING) {
            return; // tour was cancelled while the dialog was open
        }

        if (answer == JOptionPane.NO_OPTION) {
            startTour();
        } else {
            // Yes (or dialog closed): take the knight off and let the user pick again
            startSquare = -1;
            showEmptyBoard();
            beginPicking();
        }
    }

    // the tour itself

    private void startTour() {
        state = State.RUNNING;
        messageLabel.setText(TOUR_MESSAGE);

        path = buildTour(startSquare);
        pathIndex = 0;

        visited.clear();
        visited.add(startSquare);
        markStartSquare = true;
        boardPanel.repaint();

        timer = new Timer(JUMP_DELAY_MS, e -> step());
        timer.setInitialDelay(JUMP_DELAY_MS);
        timer.start();
    }

    private void step() {
        if (state != State.RUNNING) {
            return;
        }

        pathIndex++;
        int square = path[pathIndex];

        visited.add(square);
        showKnightAt(square);

        if (pathIndex == path.length - 1) {
            // back on the initial square
            timer.stop();
            markStartSquare = false;
            boardPanel.repaint();

            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(
                        owner,
                        "The knights tour is over",
                        "Knight's Tour",
                        JOptionPane.INFORMATION_MESSAGE
                );
                finish();
            });
        }
    }

    /**
     * Builds a closed tour that starts (and, after the 64th jump, ends) on {@code start}:
     * 65 squares, the first and last being the start square.
     */
    private int[] buildTour(int start) {
        boolean swap = random.nextBoolean();
        boolean flipFile = random.nextBoolean();
        boolean flipRank = random.nextBoolean();
        boolean reverse = random.nextBoolean();

        int[] seq = new int[64];
        for (int i = 0; i < 64; i++) {
            int s = BASE_TOUR[reverse ? 63 - i : i];
            int file = s % 8;
            int rank = s / 8;

            if (swap) {
                int t = file;
                file = rank;
                rank = t;
            }
            if (flipFile) file = 7 - file;
            if (flipRank) rank = 7 - rank;

            seq[i] = rank * 8 + file;
        }

        int offset = 0;
        for (int i = 0; i < 64; i++) {
            if (seq[i] == start) {
                offset = i;
                break;
            }
        }

        int[] result = new int[65];
        for (int i = 0; i < 64; i++) {
            result[i] = seq[(offset + i) % 64];
        }
        result[64] = start;
        return result;
    }

    //board helpers

    private void showEmptyBoard() {
        boardPanel.setBoard(boardWithKnight(-1));
        boardPanel.repaint();
    }

    private void showKnightAt(int square) {
        boardPanel.setBoard(boardWithKnight(square));
        boardPanel.repaint();
    }

    private Board boardWithKnight(int square) {
        Board board = new Board();

        for (int sq = 0; sq < 64; sq++) {
            board.setPiece(sq, '.');
        }

        board.setEnPassantSquare(-1);
        board.setWhiteKingSideCastle(false);
        board.setWhiteQueenSideCastle(false);
        board.setBlackKingSideCastle(false);
        board.setBlackQueenSideCastle(false);

        if (square >= 0) {
            board.setPiece(square, knightPiece);
        }
        return board;
    }

    // drawing the red mark and the path

    /*Paints in board-panel coordinates. */
    private void paintOverlay(Graphics2D g, ChessBoardPanel panel) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (markStartSquare && startSquare >= 0) {
            Rectangle r = panel.getSquareBounds(startSquare);
            g.setColor(new Color(225, 25, 25, 160));
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        if (visited.size() >= 2) {
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(30, 90, 210, 215));

            for (int i = 1; i < visited.size(); i++) {
                Rectangle a = panel.getSquareBounds(visited.get(i - 1));
                Rectangle b = panel.getSquareBounds(visited.get(i));

                g.drawLine(a.x + a.width / 2, a.y + a.height / 2,
                        b.x + b.width / 2, b.y + b.height / 2);
            }
        }
    }
}