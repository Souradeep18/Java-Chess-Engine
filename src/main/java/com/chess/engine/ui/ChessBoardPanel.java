package com.chess.engine.ui;

import com.chess.engine.engine.AtomicChess;
import com.chess.engine.engine.Board;
import com.chess.engine.engine.Move;
import com.chess.engine.engine.MoveGenerator;
import com.chess.engine.engine.RacingKings;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("serial")
public class ChessBoardPanel extends JPanel {

    private static final Color LIGHT_SQUARE =
            new Color(240, 217, 181);

    private static final Color DARK_SQUARE =
            new Color(181, 136, 99);

    private static final Color SELECTED_COLOR =
            new Color(130, 151, 105);

    private static final Color LEGAL_MOVE_DOT_COLOR =
            new Color(100, 110, 64, 160);

    private static final Color CAPTURE_RING_COLOR =
            new Color(100, 110, 64, 160);

    private static final Color CHECK_COLOR =
            new Color(235, 70, 70);

    private static final Color CHECKMATE_COLOR =
            new Color(190, 25, 25);

    private static final Color CHECKER_COLOR =
            new Color(235, 130, 130);

    private final SquareButton[] squares = new SquareButton[64];

    private Board board;

    private int selectedSquare = -1;

    private boolean movesEnabled = true;

    private boolean isFlipped = false;

    // a-h along the bottom edge and 1-8 along the right edge (follows the board orientation)
    private boolean showCoordinates = true;

    // Guard flag to prevent double-processing during modal dialogs
    private boolean isProcessingClick = false;

    private final Map<Integer, Move> highlightedMoves =
            new HashMap<>();

    // ----- Board editor support -----

    /**
     * Callback used by the board editor. The panel reports raw square
     * clicks and piece drops; the owner decides what they mean.
     */
    public interface PieceEditListener {
        void onSquareClicked(int square);

        void onPieceDropped(int square, char piece);
    }

    private boolean editorMode = false;

    private PieceEditListener pieceEditListener;

    // ----- Overlay support (used by the knight's tour to draw the red mark and path) -----

    public interface OverlayPainter {
        /** Paints in this panel's coordinate system, on top of the squares and pieces. */
        void paint(Graphics2D g, ChessBoardPanel panel);
    }

    private OverlayPainter overlayPainter;

    public void setOverlayPainter(OverlayPainter painter) {
        this.overlayPainter = painter;
        repaint();
    }

    /** Bounds of a square (0 = a1 ... 63 = h8) in this panel's coordinates. */
    public Rectangle getSquareBounds(int square) {
        return squares[square].getBounds();
    }

    /** Called by every square so the overlay stays visible whichever square repaints. */
    private void paintOverlay(Graphics2D g) {
        if (overlayPainter != null) {
            overlayPainter.paint(g, this);
        }
    }

    public ChessBoardPanel(Board board) {

        this.board = board;

        setLayout(new GridLayout(8, 8));

        setPreferredSize(
                new Dimension(800, 720)
        );

        setMinimumSize(
                new Dimension(500, 500)
        );

        setBorder(
                BorderFactory.createEmptyBorder(
                        0, 0, 0, 0
                )
        );

        createBoard();

        refreshBoard();
    }

    /**
     * Toggles or sets board flip orientation.
     */
    public void setFlipped(boolean flipped) {
        if (this.isFlipped != flipped) {
            this.isFlipped = flipped;
            rebuildGridLayout();
            refreshBoard();
        }
    }

    public boolean isFlipped() {
        return isFlipped;
    }

    public void setShowCoordinates(boolean show) {
        this.showCoordinates = show;
        updateCoordinateLabels();
        repaint();
    }

    public boolean isShowCoordinates() {
        return showCoordinates;
    }

    /*
      Only the squares on the bottom row get a file letter and only the squares on the
      right-hand column get a rank number, whichever way the board is turned.
     */
    private void updateCoordinateLabels() {
        for (int square = 0; square < 64; square++) {
            int file = square % 8;
            int rank = square / 8;

            int bottomRank = isFlipped ? 7 : 0;
            int rightFile = isFlipped ? 0 : 7;

            SquareButton button = squares[square];
            button.fileLabel = (showCoordinates && rank == bottomRank) ? String.valueOf((char) ('a' + file)) : null;
            button.rankLabel = (showCoordinates && file == rightFile) ? String.valueOf(rank + 1) : null;
            button.darkSquare = (rank + file) % 2 == 0;
            button.visualCol = isFlipped ? 7 - file : file;
            button.visualRow = isFlipped ? rank : 7 - rank;
            button.liftedRow = isFlipped ? 7 : 0;
        }
    }

    private void createBoard() {

        for (int square = 0; square < 64; square++) {

            SquareButton button = new SquareButton();
            button.owner = this;

            button.setFont(
                    new Font(
                            "Segoe UI Symbol",
                            Font.PLAIN,
                            36
                    )
            );

            button.setMargin(new Insets(0, 0, 0, 0));
            button.setFocusPainted(false);
            button.setBorderPainted(false);
            button.setOpaque(true);

            final int currentSquare = square;

            // Using ActionListener avoids duplicate mousePressed / mouseReleased triggers
            button.addActionListener(e -> handleSquareClick(currentSquare));

            // Lets palette pieces be dragged onto this square (editor mode only)
            button.setTransferHandler(new SquareDropHandler(currentSquare));

            squares[square] = button;
        }

        rebuildGridLayout();
    }

    /**
     * Re-populates the GridLayout according to flip orientation.
     */
    private void rebuildGridLayout() {
        removeAll();

        if (!isFlipped) {
            // Standard perspective: Rank 8 (display rank index 7 down to 0)
            for (int displayRank = 7; displayRank >= 0; displayRank--) {
                for (int file = 0; file < 8; file++) {
                    int square = displayRank * 8 + file;
                    add(squares[square]);
                }
            }
        } else {
            // Flipped perspective: Rank 1 (display rank index 0 up to 7)
            for (int displayRank = 0; displayRank < 8; displayRank++) {
                for (int file = 7; file >= 0; file--) {
                    int square = displayRank * 8 + file;
                    add(squares[square]);
                }
            }
        }

        updateCoordinateLabels();

        revalidate();
        repaint();
    }

    private void handleSquareClick(int square) {

        // In editor mode a click never plays a move; the editor handles it
        if (editorMode) {
            if (pieceEditListener != null) {
                pieceEditListener.onSquareClicked(square);
            }
            return;
        }

        if (!movesEnabled || isProcessingClick) {
            return;
        }

        isProcessingClick = true;

        try {
            char clickedPiece = board.getPiece(square);

            if (selectedSquare == -1) {

                if (clickedPiece == '.') {
                    return;
                }

                if (!isPieceOwnedBySideToMove(clickedPiece)) {
                    return;
                }

                selectPiece(square);
                return;
            }

            if (clickedPiece != '.' && isPieceOwnedBySideToMove(clickedPiece)) {
                selectPiece(square);
                return;
            }

            // Check if destination square clicked is valid
            if (highlightedMoves.containsKey(square)) {

                int fromSquare = selectedSquare;

                selectedSquare = -1;
                highlightedMoves.clear();

                Move selectedMove = resolveMoveForSquare(fromSquare, square);

                if (selectedMove != null) {

                    Board before = board.copy();

                    MoveGenerator.makeMove(
                            board,
                            selectedMove
                    );

                    refreshBoard();

                    firePropertyChange(
                            "boardMove",
                            before,
                            board.copy()
                    );

                    return;
                }
            }

            selectedSquare = -1;
            highlightedMoves.clear();
            refreshBoard();
        } finally {
            // Reset guard synchronously so buffered modal events are ignored
            isProcessingClick = false;
        }
    }

    /**
     * Finds matching legal move. Prompts the user once if move requires pawn promotion.
     */
    private Move resolveMoveForSquare(int fromSquare, int toSquare) {
        List<Move> legalMoves = MoveGenerator.generateLegalMoves(board);

        char movingPiece = board.getPiece(fromSquare);
        boolean isPawn = Character.toLowerCase(movingPiece) == 'p';
        int toRank = toSquare / 8;
        boolean reachesPromotionRank = (board.isWhiteToMove() && toRank == 7) || (!board.isWhiteToMove() && toRank == 0);

        char chosenPromotion = '\0';

        if (isPawn && reachesPromotionRank) {
            chosenPromotion = promptPromotionPiece(board.isWhiteToMove());
            if (chosenPromotion == '\0') {
                return null; // Canceled selection
            }
        }

        for (Move move : legalMoves) {
            if (move.from == fromSquare && move.to == toSquare) {
                if (isPawn && reachesPromotionRank) {
                    if (Character.toUpperCase(move.promotion) == Character.toUpperCase(chosenPromotion)) {
                        return move;
                    }
                } else {
                    return move;
                }
            }
        }

        return null;
    }

    /**
     * Prompts the player to select promotion piece option.
     */
    private char promptPromotionPiece(boolean isWhite) {
        String[] options = {"Queen", "Rook", "Bishop", "Knight"};

        int choice = JOptionPane.showOptionDialog(
                this,
                "Select piece for pawn promotion:",
                "Pawn Promotion",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        if (choice == JOptionPane.CLOSED_OPTION) {
            return '\0';
        }

        char piece = switch (choice) {
            case 1 -> 'R';
            case 2 -> 'B';
            case 3 -> 'N';
            case 0 -> 'Q';
            default -> 'Q';
        };

        return isWhite ? Character.toUpperCase(piece) : Character.toLowerCase(piece);
    }

    private void selectPiece(int square) {

        selectedSquare = square;
        highlightedMoves.clear();

        List<Move> legalMoves = MoveGenerator.generateLegalMoves(board);

        for (Move move : legalMoves) {
            if (move.from == square) {
                highlightedMoves.put(move.to, move);
            }
        }

        refreshBoard();
    }

    private boolean isPieceOwnedBySideToMove(char piece) {

        if (piece == '.') {
            return false;
        }

        boolean whitePiece = Character.isUpperCase(piece);
        return whitePiece == board.isWhiteToMove();
    }

    public void setEditorMode(boolean enabled) {
        this.editorMode = enabled;
        selectedSquare = -1;
        highlightedMoves.clear();
        refreshBoard();
    }

    public boolean isEditorMode() {
        return editorMode;
    }

    public void setPieceEditListener(PieceEditListener listener) {
        this.pieceEditListener = listener;
    }

    public PieceEditListener getPieceEditListener() {
        return pieceEditListener;
    }

    /**
     * Accepts a single piece character dragged from the piece palette.
     */
    private class SquareDropHandler extends TransferHandler {

        private final int square;

        SquareDropHandler(int square) {
            this.square = square;
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return editorMode
                    && support.isDrop()
                    && support.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }

            try {
                String data = (String) support.getTransferable()
                        .getTransferData(DataFlavor.stringFlavor);

                if (data == null || data.length() != 1) {
                    return false;
                }

                if (pieceEditListener != null) {
                    pieceEditListener.onPieceDropped(square, data.charAt(0));
                }
                return true;

            } catch (UnsupportedFlavorException | IOException ex) {
                return false;
            }
        }
    }

    public void setBoard(Board board) {

        this.board = board.copy();
        selectedSquare = -1;
        highlightedMoves.clear();
        refreshBoard();
    }

    public Board getBoard() {
        return board;
    }

    public void setMovesEnabled(boolean enabled) {

        movesEnabled = enabled;

        if (!enabled) {
            selectedSquare = -1;
            highlightedMoves.clear();
        }

        refreshBoard();
    }

    public boolean isMovesEnabled() {
        return movesEnabled;
    }

    public void refreshBoard() {

        if (board == null) {
            return;
        }

        highlightedMoves.clear();

        if (selectedSquare != -1 && movesEnabled) {

            List<Move> legalMoves = MoveGenerator.generateLegalMoves(board);

            for (Move move : legalMoves) {
                if (move.from == selectedSquare) {
                    highlightedMoves.put(move.to, move);
                }
            }
        }

        // Positions being edited may be incomplete/illegal, so skip check highlighting
        boolean whiteKingInCheck = !editorMode && MoveGenerator.isKingInCheck(board, true);
        boolean blackKingInCheck = !editorMode && MoveGenerator.isKingInCheck(board, false);

        boolean whiteCheckmate = !editorMode && isCheckmate(board, true);
        boolean blackCheckmate = !editorMode && isCheckmate(board, false);

        int whiteKingSquare = editorMode ? -1 : findKing(board, true);
        int blackKingSquare = editorMode ? -1 : findKing(board, false);

        boolean racingStalemate = !editorMode && board.isRacingKings()
                && RacingKings.getResult(board) == RacingKings.Result.STALEMATE;

        for (int rank = 0; rank < 8; rank++) {

            for (int file = 0; file < 8; file++) {

                int square = rank * 8 + file;

                SquareButton button = squares[square];

                // Reset indicators
                button.setLegalMoveDot(false);
                button.setLegalCaptureRing(false);

                boolean hillOn = board.isKingOfTheHill();
                button.hillActive = hillOn;

                boolean racingOn = board.isRacingKings();
                button.racingActive = racingOn;
                button.lockBadge = racingStalemate
                        && (board.getPiece(square) == 'K' || board.getPiece(square) == 'k');

                if (racingOn && rank == 7) {
                    button.setBackground(HillStyle.raisedRankColor((rank + file) % 2 == 0));
                } else if (hillOn && HillStyle.isHillSquare(square)) {
                    button.setBackground(HillStyle.color((rank + file) % 2 == 0));
                } else if ((rank + file) % 2 == 0) {
                    button.setBackground(DARK_SQUARE);
                } else {
                    button.setBackground(LIGHT_SQUARE);
                }

                button.setText(
                        pieceSymbol(board.getPiece(square))
                );

                if (square == selectedSquare) {
                    button.setBackground(SELECTED_COLOR);
                }

                /*
                 * Dot / Capture ring overlays
                 */
                if (highlightedMoves.containsKey(square)) {

                    char destinationPiece = board.getPiece(square);

                    if (destinationPiece != '.' && isOpponentPiece(destinationPiece)) {
                        button.setLegalCaptureRing(true);
                    } else {
                        button.setLegalMoveDot(true);
                    }
                }

                if (square == whiteKingSquare && whiteKingInCheck) {

                    button.setBackground(
                            whiteCheckmate ? CHECKMATE_COLOR : CHECK_COLOR
                    );
                }

                if (square == blackKingSquare && blackKingInCheck) {

                    button.setBackground(
                            blackCheckmate ? CHECKMATE_COLOR : CHECK_COLOR
                    );
                }

                if (whiteCheckmate && isCheckingPiece(square, true)) {
                    button.setBackground(CHECKER_COLOR);
                }

                if (blackCheckmate && isCheckingPiece(square, false)) {
                    button.setBackground(CHECKER_COLOR);
                }

                if ((square == whiteKingSquare && whiteCheckmate)
                        || (square == blackKingSquare && blackCheckmate)) {

                    button.setText(
                            "<html>"
                                    + "<div style='position:relative;'>"
                                    + pieceSymbolRaw(board.getPiece(square))
                                    + "<span style='font-size:16px;'>#</span>"
                                    + "</div>"
                                    + "</html>"
                    );
                }
            }
        }

        repaint();
    }

    private boolean isOpponentPiece(char piece) {

        if (piece == '.') {
            return false;
        }

        return Character.isUpperCase(piece) != board.isWhiteToMove();
    }

    private int findKing(Board position, boolean white) {

        char king = white ? 'K' : 'k';

        for (int square = 0; square < 64; square++) {

            if (position.getPiece(square) == king) {
                return square;
            }
        }

        return -1;
    }

    private boolean isCheckmate(Board position, boolean white) {

        // Atomic chess: once a king has been blown off the game is over by the explosion.
        // That is not a checkmate, so no "#" mark (a king that is still attacked just shows as in check).
        if (position.isAtomic()
                && (AtomicChess.hasLostKing(position, true) || AtomicChess.hasLostKing(position, false))) {
            return false;
        }

        if (!MoveGenerator.isKingInCheck(position, white)) {
            return false;
        }

        Board test = position.copy();
        test.setWhiteToMove(white);

        return MoveGenerator.generateLegalMoves(test).isEmpty();
    }

    private boolean isCheckingPiece(int square, boolean checkedKingIsWhite) {

        char piece = board.getPiece(square);

        if (piece == '.') {
            return false;
        }

        boolean pieceIsWhite = Character.isUpperCase(piece);

        if (pieceIsWhite == checkedKingIsWhite) {
            return false;
        }

        int kingSquare = findKing(board, checkedKingIsWhite);

        if (kingSquare == -1) {
            return false;
        }

        return pieceAttacksSquare(
                board,
                square,
                kingSquare,
                piece
        );
    }

    private boolean pieceAttacksSquare(
            Board position,
            int from,
            int target,
            char piece) {

        int fromFile = from % 8;
        int fromRank = from / 8;

        int targetFile = target % 8;
        int targetRank = target / 8;

        int df = targetFile - fromFile;
        int dr = targetRank - fromRank;

        char lower = Character.toLowerCase(piece);

        if (lower == 'p') {

            int direction = Character.isUpperCase(piece) ? 1 : -1;

            return dr == direction && Math.abs(df) == 1;
        }

        if (lower == 'n') {

            return (Math.abs(df) == 1 && Math.abs(dr) == 2)
                    || (Math.abs(df) == 2 && Math.abs(dr) == 1);
        }

        if (lower == 'k') {

            return Math.max(
                    Math.abs(df),
                    Math.abs(dr)
            ) == 1;
        }

        if (lower == 'r') {

            if (df != 0 && dr != 0) {
                return false;
            }

            return clearLine(
                    position,
                    fromFile,
                    fromRank,
                    targetFile,
                    targetRank
            );
        }

        if (lower == 'b') {

            if (Math.abs(df) != Math.abs(dr)) {
                return false;
            }

            return clearLine(
                    position,
                    fromFile,
                    fromRank,
                    targetFile,
                    targetRank
            );
        }

        if (lower == 'q') {

            boolean straight = df == 0 || dr == 0;
            boolean diagonal = Math.abs(df) == Math.abs(dr);

            if (!straight && !diagonal) {
                return false;
            }

            return clearLine(
                    position,
                    fromFile,
                    fromRank,
                    targetFile,
                    targetRank
            );
        }

        return false;
    }

    private boolean clearLine(
            Board position,
            int fromFile,
            int fromRank,
            int targetFile,
            int targetRank) {

        int stepFile = Integer.compare(targetFile, fromFile);
        int stepRank = Integer.compare(targetRank, fromRank);

        int file = fromFile + stepFile;
        int rank = fromRank + stepRank;

        while (file != targetFile || rank != targetRank) {

            int square = rank * 8 + file;

            if (position.getPiece(square) != '.') {
                return false;
            }

            file += stepFile;
            rank += stepRank;
        }

        return true;
    }

    private String pieceSymbolRaw(char piece) {
        return switch (piece) {
            case 'K' -> "♔";
            case 'Q' -> "♕";
            case 'R' -> "♖";
            case 'B' -> "♗";
            case 'N' -> "♘";
            case 'P' -> "♙";

            case 'k' -> "♚";
            case 'q' -> "♛";
            case 'r' -> "♜";
            case 'b' -> "♝";
            case 'n' -> "♞";
            case 'p' -> "♟";

            default -> "";
        };
    }

    private String pieceSymbol(char piece) {
        String rawSymbol = pieceSymbolRaw(piece);

        if (rawSymbol.isEmpty()) {
            return "";
        }

        return "<html><span style='font-size:32px;'>" + rawSymbol + "</span></html>";
    }
    
    

    /**
     * Custom JButton that renders translucent dot/ring indicators for moves.
     */
    private static class SquareButton extends JButton {

        private boolean legalMoveDot = false;
        private boolean legalCaptureRing = false;

        ChessBoardPanel owner;

        String fileLabel;
        String rankLabel;
        boolean darkSquare;
        boolean racingActive; // Racing Kings: raised 8th rank
        boolean lockBadge;    // Racing Kings stalemate: padlock on the king
        int liftedRow;
        boolean hillActive;   // King of the Hill: draw the lifted centre
        int visualCol, visualRow;

        public void setLegalMoveDot(boolean legalMoveDot) {
            this.legalMoveDot = legalMoveDot;
        }

        public void setLegalCaptureRing(boolean legalCaptureRing) {
            this.legalCaptureRing = legalCaptureRing;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // overlay (knight's tour path) drawn in panel coordinates, clipped to this square
            if (owner != null) {
                Graphics2D overlay = (Graphics2D) g.create();
                overlay.translate(-getX(), -getY());
                owner.paintOverlay(overlay);
                overlay.dispose();
            }

            if (hillActive) {
                Graphics2D lift = (Graphics2D) g.create();
                HillStyle.paintLift(lift, getWidth(), getHeight(), visualCol, visualRow);
                lift.dispose();
            }

            if (racingActive) {
                Graphics2D lift = (Graphics2D) g.create();
                HillStyle.paintRaisedRank(lift, getWidth(), getHeight(), visualRow, liftedRow);
                lift.dispose();
            }

            if (fileLabel != null || rankLabel != null) {
                Graphics2D text = (Graphics2D) g.create();
                text.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                text.setFont(new Font("SansSerif", Font.BOLD, 13));
                text.setColor(darkSquare ? LIGHT_SQUARE : DARK_SQUARE);

                FontMetrics fm = text.getFontMetrics();

                if (fileLabel != null) {
                    text.drawString(fileLabel, 4, getHeight() - 4);
                }
                if (rankLabel != null) {
                    text.drawString(rankLabel, getWidth() - fm.stringWidth(rankLabel) - 4, fm.getAscent() + 2);
                }
                text.dispose();
            }

            if (lockBadge) {
                Graphics2D lock = (Graphics2D) g.create();
                HillStyle.paintLock(lock, getWidth(), getHeight());
                lock.dispose();
            }

            if (legalMoveDot || legalCaptureRing) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );

                int w = getWidth();
                int h = getHeight();

                if (legalMoveDot) {
                    g2d.setColor(LEGAL_MOVE_DOT_COLOR);
                    int dotDiameter = Math.min(w, h) / 3;
                    int x = (w - dotDiameter) / 2;
                    int y = (h - dotDiameter) / 2;
                    g2d.fillOval(x, y, dotDiameter, dotDiameter);
                } else if (legalCaptureRing) {
                    g2d.setColor(CAPTURE_RING_COLOR);
                    int outerSize = Math.min(w, h);
                    int strokeWidth = Math.max(3, outerSize / 12);
                    g2d.setStroke(new BasicStroke(strokeWidth));
                    int pad = strokeWidth / 2 + 2;
                    g2d.drawOval(pad, pad, outerSize - 2 * pad, outerSize - 2 * pad);
                }

                g2d.dispose();
            }
        }
    }
}