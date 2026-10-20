package com.chess.engine.ui;

import com.chess.engine.engine.Board;
import com.chess.engine.engine.AtomicChess;
import com.chess.engine.engine.ChessEngine;
import com.chess.engine.engine.HordeChess;
import com.chess.engine.engine.Move;
import com.chess.engine.engine.MoveGenerator;
import com.chess.engine.engine.AntiChess;
import com.chess.engine.engine.Chess960;
import com.chess.engine.engine.KingOfTheHill;
import com.chess.engine.engine.RacingKings;
import com.chess.engine.engine.ThreeCheck;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Main application frame for the Java Chess Engine.
 * Features recursive variation trees, PGN HTML notation display,
 * material score tracking, engine search, and a full Board Editor mode.
 */
@SuppressWarnings("serial")
public class ChessFrame extends JFrame {

    // UI Components
    private final ChessBoardPanel boardPanel;

    private final JButton backButton;
    private Chess960PositionBox chess960Box;
    private JLabel navigationTitleLabel;
    private JPanel navigationPanelRef;
    private final JButton forwardButton;

    private final JButton newGameButton;
    private final JButton loadFenButton;
    private final JButton engineButton;

    // Evaluation: toggle next to "Engine Move", bar beside the board, and the best lines
    private final JToggleButton analysisToggle;
    private final JPanel analysisPanel;
    private final JLabel analysisEvalLabel;
    private final JLabel analysisDepthLabel;
    private final JLabel[] analysisScoreLabels = new JLabel[3];
    private final PvLinePanel[] analysisMovePanels = new PvLinePanel[3];
    private MoveNode analysisAnchor;          // the position the shown lines belong to
    private MiniBoardPopup miniBoard;         // preview shown while hovering a move of a line
    private final EvalBar evalBar;
    private ChessEngine analysisEngine;
    private SwingWorker<Void, AnalysisSnapshot> analysisWorker;
    private int analysisGeneration = 0;
    private final JButton flipButton;
    private final JButton coordinatesButton;
    private final JButton tourButton;

    // Board editor: variant chooser (replaces the GAME section while editing)
    private final JComboBox<String> variantCombo;
    private final JPanel gameSection;
    private final KnightsTour knightsTour;

    // Board Editor Toggle & Special Controls
    private final JButton boardEditorButton;
    private final JButton startingPositionButton;
    private final JButton clearBoardButton;
    private final JButton analyseBoardButton;

    // Palette Containers
    private final JPanel topPalettePanel;
    private final JPanel bottomPalettePanel;

    // Selected piece for placement ('\0' = none, 'X' = delete mode)
    private char selectedEditorPiece = '\0';

    // Every palette button (both palettes) -> the piece code it represents ('X' = delete)
    private final Map<JButton, Character> paletteButtons = new LinkedHashMap<>();

    // True while the UI is being refreshed from the board (prevents feedback loops)
    private boolean syncingUI = false;

    // True when the current position is checkmate or a draw: no further moves allowed
    private boolean gameIsOver = false;

    // JTextPane for HTML variation styling
    private final JTextPane notationPane;

    private final JLabel whiteMaterialLabel;
    private final JLabel blackMaterialLabel;

    private final JLabel statusLabel;

    // Three-Check: a box of three marks on each side of the board (top / bottom as shown)
    private final JLabel[] topCheckMarks = new JLabel[3];
    private final JLabel[] bottomCheckMarks = new JLabel[3];
    private final JPanel topCheckPanel;
    private final JPanel bottomCheckPanel;

    // --- Castling Rights & Active Player Editor Components ---
    private final JPanel castlingAndPlayerPanel;
    private final JComboBox<String> activePlayerCombo;
    private final JCheckBox whiteKingSideCb;
    private final JCheckBox whiteQueenSideCb;
    private final JCheckBox blackKingSideCb;
    private final JCheckBox blackQueenSideCb;

    // --- Engine & Game State ---
    private final ChessEngine engine;
    private boolean isFlipped = false;
    private boolean isEditorMode = false;

    // Root node of the move tree (ply 0)
    private MoveNode rootNode;
    private MoveNode currentNode;

    // Target node right-clicked for context menu actions
    private MoveNode targetContextMenuNode;

    // Map to link HTML hyperlink IDs to MoveNodes
    private final Map<String, MoveNode> nodeMap = new HashMap<>();
    private int nodeCounter = 0;

    // Constructs the main chess frame and initializes all UI components.
    public ChessFrame() {
        super("Java Chess Engine");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Initialize chess engine 
        engine = new ChessEngine();
        Board initialBoard = new Board();

        rootNode = new MoveNode(initialBoard.copy(), null, "Start", 0);
        currentNode = rootNode;

        boardPanel = new ChessBoardPanel(initialBoard);

        // Header Title
        JLabel titleLabel = new JLabel("JAVA CHESS ENGINE", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        titleLabel.setBorder(new EmptyBorder(10, 10, 10, 10));

        add(titleLabel, BorderLayout.NORTH);

        // --- Side Control Panel ---
        JPanel sidePanel = new JPanel();
        sidePanel.setPreferredSize(new Dimension(360, 780));
        sidePanel.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(200, 200, 200)),
                        new EmptyBorder(15, 15, 15, 15)
                )
        );
        sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));

        // Editor Actions Panel
        JPanel editorControlsPanel = new JPanel(new GridLayout(3, 1, 0, 6));
        editorControlsPanel.setOpaque(false);
        editorControlsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        editorControlsPanel.setMaximumSize(new Dimension(320, 120));

        startingPositionButton = new JButton("🔄 Starting position");
        clearBoardButton = new JButton("🗑️ Clear board");
        analyseBoardButton = new JButton("🔬 Analyse board");

        Font editorFont = new Font("SansSerif", Font.BOLD, 13);
        startingPositionButton.setFont(editorFont);
        clearBoardButton.setFont(editorFont);
        analyseBoardButton.setFont(editorFont);

        editorControlsPanel.add(startingPositionButton);
        editorControlsPanel.add(clearBoardButton);
        editorControlsPanel.add(analyseBoardButton);

        editorControlsPanel.setVisible(false); // Initially hidden

        startingPositionButton.addActionListener(e -> resetToStartingPosition());
        clearBoardButton.addActionListener(e -> clearBoardPieces());
        analyseBoardButton.addActionListener(e -> exitEditorAndAnalyse());

        sidePanel.add(editorControlsPanel);
        sidePanel.add(Box.createVerticalStrut(10));

        // Variant drop-down: shown instead of the GAME section while the board editor is open
        variantCombo = new JComboBox<>(new String[]{
                HordeChess.VARIANT_STANDARD,
                HordeChess.VARIANT_HORDE,
                AtomicChess.VARIANT_ATOMIC,
                ThreeCheck.VARIANT_THREE_CHECK,
                AntiChess.VARIANT_ANTI_CHESS,
                KingOfTheHill.VARIANT_KING_OF_THE_HILL,
                RacingKings.VARIANT_RACING_KINGS,
                Chess960.VARIANT_CHESS960});
        variantCombo.setMaximumSize(new Dimension(320, 32));
        variantCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        variantCombo.setVisible(false);
        variantCombo.addActionListener(e -> onVariantSelected());
        sidePanel.add(variantCombo);

        // Game Control Actions
        gameSection = new JPanel();
        gameSection.setLayout(new BoxLayout(gameSection, BoxLayout.Y_AXIS));
        gameSection.setOpaque(false);
        gameSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel gameLabel = createSectionTitle("GAME");
        gameSection.add(gameLabel);
        gameSection.add(Box.createVerticalStrut(8));

        newGameButton = new JButton("New Game");
        loadFenButton = new JButton("Load FEN");
        engineButton = new JButton("Engine Move");

        Dimension controlSize = new Dimension(320, 38);
        newGameButton.setMaximumSize(controlSize);
        loadFenButton.setMaximumSize(controlSize);
        engineButton.setMaximumSize(controlSize);

        newGameButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        loadFenButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        engineButton.setAlignmentX(Component.LEFT_ALIGNMENT);

        gameSection.add(newGameButton);
        gameSection.add(Box.createVerticalStrut(6));
        gameSection.add(loadFenButton);
        gameSection.add(Box.createVerticalStrut(6));

        // "Engine Move" is a little narrower to make room for the evaluation toggle
        analysisToggle = new JToggleButton("📊");
        analysisToggle.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        analysisToggle.setFocusable(false);
        analysisToggle.setPreferredSize(new Dimension(48, 38));
        analysisToggle.setToolTipText("Evaluation bar and the engine's 3 best lines");

        JPanel engineRow = new JPanel(new BorderLayout(4, 0));
        engineRow.setOpaque(false);
        engineRow.setMaximumSize(controlSize);
        engineRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        engineRow.add(engineButton, BorderLayout.CENTER);
        engineRow.add(analysisToggle, BorderLayout.EAST);
        gameSection.add(engineRow);
        sidePanel.add(gameSection);

        // Evaluation + the three best lines (only visible while the toggle is on)
        analysisPanel = new JPanel();
        analysisPanel.setLayout(new BoxLayout(analysisPanel, BoxLayout.Y_AXIS));
        analysisPanel.setOpaque(false);
        analysisPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        analysisPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        analysisEvalLabel = new JLabel("0.0");
        analysisEvalLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        analysisDepthLabel = new JLabel(" ");
        analysisDepthLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        analysisDepthLabel.setForeground(new Color(110, 110, 110));

        JPanel evalHeader = new JPanel(new BorderLayout(10, 0));
        evalHeader.setOpaque(false);
        evalHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        evalHeader.setPreferredSize(new Dimension(10, 34));
        evalHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        evalHeader.add(analysisEvalLabel, BorderLayout.WEST);
        evalHeader.add(analysisDepthLabel, BorderLayout.CENTER);
        analysisPanel.add(evalHeader);
        analysisPanel.add(Box.createVerticalStrut(4));

        for (int i = 0; i < 3; i++) {
            analysisScoreLabels[i] = new JLabel(" ", SwingConstants.CENTER);
            analysisScoreLabels[i].setFont(new Font("SansSerif", Font.BOLD, 13));
            analysisScoreLabels[i].setPreferredSize(new Dimension(56, 22));

            analysisMovePanels[i] = new PvLinePanel();
            analysisMovePanels[i].setListener(createPvListener());

            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            // a small preferred width keeps long lines from widening the side panel (they end in "...")
            row.setPreferredSize(new Dimension(10, 28));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
            row.add(analysisScoreLabels[i], BorderLayout.WEST);
            row.add(analysisMovePanels[i], BorderLayout.CENTER);
            analysisPanel.add(row);
        }

        analysisPanel.setVisible(false);
        sidePanel.add(analysisPanel);

        newGameButton.addActionListener(e -> startNewGame());
        loadFenButton.addActionListener(e -> loadFEN());
        engineButton.addActionListener(e -> makeEngineMove());
        analysisToggle.addActionListener(e -> refreshAnalysis());

        sidePanel.add(Box.createVerticalStrut(14));

        //Castling Rights & Active Player Panel (Hidden by default)
        castlingAndPlayerPanel = new JPanel();
        castlingAndPlayerPanel.setLayout(new BoxLayout(castlingAndPlayerPanel, BoxLayout.Y_AXIS));
        castlingAndPlayerPanel.setOpaque(false);
        castlingAndPlayerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        String[] options = {"White to play", "Black to play"};
        activePlayerCombo = new JComboBox<>(options);
        activePlayerCombo.setMaximumSize(new Dimension(320, 32));
        activePlayerCombo.setAlignmentX(Component.LEFT_ALIGNMENT);

        castlingAndPlayerPanel.add(activePlayerCombo);
        castlingAndPlayerPanel.add(Box.createVerticalStrut(10));

        JLabel castlingTitle = createSectionTitle("Castling rights");
        castlingAndPlayerPanel.add(castlingTitle);
        castlingAndPlayerPanel.add(Box.createVerticalStrut(6));

        // White Castling Options
        JPanel whiteRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        whiteRow.setOpaque(false);
        whiteRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel whiteLabel = new JLabel("White");
        whiteKingSideCb = new JCheckBox("O-O");
        whiteQueenSideCb = new JCheckBox("O-O-O");

        whiteRow.add(whiteLabel);
        whiteRow.add(whiteKingSideCb);
        whiteRow.add(whiteQueenSideCb);

        // Black Castling Options
        JPanel blackRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        blackRow.setOpaque(false);
        blackRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel blackLabel = new JLabel("Black");
        blackKingSideCb = new JCheckBox("O-O");
        blackQueenSideCb = new JCheckBox("O-O-O");

        blackRow.add(blackLabel);
        blackRow.add(blackKingSideCb);
        blackRow.add(blackQueenSideCb);

        castlingAndPlayerPanel.add(whiteRow);
        castlingAndPlayerPanel.add(Box.createVerticalStrut(4));
        castlingAndPlayerPanel.add(blackRow);

        activePlayerCombo.addActionListener(e -> applyEditorOptionsToBoard());
        whiteKingSideCb.addActionListener(e -> applyEditorOptionsToBoard());
        whiteQueenSideCb.addActionListener(e -> applyEditorOptionsToBoard());
        blackKingSideCb.addActionListener(e -> applyEditorOptionsToBoard());
        blackQueenSideCb.addActionListener(e -> applyEditorOptionsToBoard());

        castlingAndPlayerPanel.setVisible(false); // Initially hidden
        sidePanel.add(castlingAndPlayerPanel);

        sidePanel.add(Box.createVerticalStrut(14));

        // Chess960: position number box (board editor only; it takes the place of the move navigation)
        chess960Box = new Chess960PositionBox();
        chess960Box.setListener(this::onChess960NumberChanged);
        chess960Box.setVisible(false);
        sidePanel.add(chess960Box);

        // Navigation Controls
        JLabel navigationTitle = createSectionTitle("MOVE NAVIGATION");
        navigationTitleLabel = navigationTitle;
        sidePanel.add(navigationTitle);
        sidePanel.add(Box.createVerticalStrut(8));

        JPanel navigationPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        navigationPanelRef = navigationPanel;
        navigationPanel.setOpaque(false);

        backButton = new JButton("<");
        forwardButton = new JButton(">");

        backButton.setFont(new Font("SansSerif", Font.BOLD, 20));
        forwardButton.setFont(new Font("SansSerif", Font.BOLD, 20));

        navigationPanel.setMaximumSize(new Dimension(320, 45));
        navigationPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        navigationPanel.add(backButton);
        navigationPanel.add(forwardButton);

        sidePanel.add(navigationPanel);

        backButton.addActionListener(e -> goBack());
        forwardButton.addActionListener(e -> goForward());

        sidePanel.add(Box.createVerticalStrut(14));

        // Status Indicator
        JLabel statusTitle = createSectionTitle("STATUS");
        sidePanel.add(statusTitle);
        sidePanel.add(Box.createVerticalStrut(5));

        statusLabel = new JLabel("White to move");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        sidePanel.add(statusLabel);

        sidePanel.add(Box.createVerticalStrut(14));

        // Material Score Section
        JLabel materialTitle = createSectionTitle("MATERIAL SCORE");
        sidePanel.add(materialTitle);
        sidePanel.add(Box.createVerticalStrut(8));

        whiteMaterialLabel = new JLabel("White: +0");
        blackMaterialLabel = new JLabel("Black: +0");

        whiteMaterialLabel.setFont(new Font("Serif", Font.PLAIN, 18));
        blackMaterialLabel.setFont(new Font("Serif", Font.PLAIN, 18));

        whiteMaterialLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        blackMaterialLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        sidePanel.add(whiteMaterialLabel);
        sidePanel.add(Box.createVerticalStrut(4));
        sidePanel.add(blackMaterialLabel);

        sidePanel.add(Box.createVerticalStrut(14));

        // Move Notation Panel
        JLabel movesTitle = createSectionTitle("MOVES");
        sidePanel.add(movesTitle);
        sidePanel.add(Box.createVerticalStrut(8));

        notationPane = new JTextPane();
        notationPane.setContentType("text/html");
        notationPane.setEditable(false);
        notationPane.setMargin(new Insets(8, 8, 8, 8));

        notationPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && !isEditorMode) {
                String id = e.getDescription();
                MoveNode target = nodeMap.get(id);
                if (target != null) {
                    selectNode(target);
                }
            }
        });

        notationPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) && !isEditorMode) {
                    showContextMenuAtPoint(e.getPoint());
                }
            }
        });

        JScrollPane notationScroll = new JScrollPane(notationPane);
        notationScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        sidePanel.add(notationScroll);

        // --- Main Board Container & Piece Palettes ---
        JPanel boardContainer = new JPanel(new BorderLayout(0, 5));
        boardContainer.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top Palette (Black Pieces)
        topPalettePanel = createPiecePalette(false);
        topPalettePanel.setVisible(false);

        // Bottom Palette (White Pieces)
        bottomPalettePanel = createPiecePalette(true);
        bottomPalettePanel.setVisible(false);

        JPanel boardCenterPanel = new JPanel(new GridBagLayout());

        // Evaluation bar on the left of the board (hidden until the toggle is switched on)
        evalBar = new EvalBar();
        evalBar.setVisible(false);

        GridBagConstraints barConstraints = new GridBagConstraints();
        barConstraints.gridx = 0;
        barConstraints.gridy = 0;
        barConstraints.fill = GridBagConstraints.VERTICAL;
        barConstraints.insets = new Insets(0, 0, 0, 8);
        boardCenterPanel.add(evalBar, barConstraints);

        GridBagConstraints boardConstraints = new GridBagConstraints();
        boardConstraints.gridx = 1;
        boardConstraints.gridy = 0;
        boardCenterPanel.add(boardPanel, boardConstraints);

        boardContainer.add(topPalettePanel, BorderLayout.NORTH);
        boardContainer.add(boardCenterPanel, BorderLayout.CENTER);

        // Bottom Row Container
        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.add(bottomPalettePanel, BorderLayout.CENTER);

        JPanel actionButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        boardEditorButton = new JButton("✏️");
        boardEditorButton.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        boardEditorButton.setFocusable(false);
        boardEditorButton.setToolTipText("Board Editor Mode");

        flipButton = new JButton("⟳");
        flipButton.setFont(new Font("SansSerif", Font.PLAIN, 18));
        flipButton.setFocusable(false);
        flipButton.setToolTipText("Reverse Board Orientation");

        // Toggles the a-h / 1-8 coordinates around the board
        coordinatesButton = new JButton("a1");
        coordinatesButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        coordinatesButton.setFocusable(false);
        coordinatesButton.setToolTipText("Show / hide board coordinates");

        actionButtonsPanel.add(coordinatesButton);
        actionButtonsPanel.add(boardEditorButton);
        actionButtonsPanel.add(flipButton);

        bottomContainer.add(actionButtonsPanel, BorderLayout.EAST);
        boardContainer.add(bottomContainer, BorderLayout.SOUTH);

        boardEditorButton.addActionListener(e -> enterEditorMode());
        flipButton.addActionListener(e -> toggleBoardOrientation());
        coordinatesButton.addActionListener(e -> boardPanel.setShowCoordinates(!boardPanel.isShowCoordinates()));

        // Knight's tour button: top-right of the board area
        tourButton = new JButton("🐎");
        tourButton.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 26));
        tourButton.setFocusable(false);
        tourButton.setPreferredSize(new Dimension(64, 52));
        tourButton.setToolTipText("Knight's tour (click again to leave)");

        JPanel tourButtonPanel = new JPanel(new BorderLayout());
        tourButtonPanel.setOpaque(false);
        tourButtonPanel.setBorder(new EmptyBorder(0, 8, 0, 0));
        topCheckPanel = createCheckPanel(topCheckMarks);
        bottomCheckPanel = createCheckPanel(bottomCheckMarks);

        JPanel eastTop = new JPanel();
        eastTop.setOpaque(false);
        eastTop.setLayout(new BoxLayout(eastTop, BoxLayout.Y_AXIS));
        tourButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        eastTop.add(tourButton);
        eastTop.add(Box.createVerticalStrut(10));
        eastTop.add(topCheckPanel);

        tourButtonPanel.add(eastTop, BorderLayout.NORTH);
        tourButtonPanel.add(bottomCheckPanel, BorderLayout.SOUTH);
        boardContainer.add(tourButtonPanel, BorderLayout.EAST);

        add(boardContainer, BorderLayout.CENTER);
        add(sidePanel, BorderLayout.EAST);

        knightsTour = new KnightsTour(this, boardPanel, sidePanel, this::onKnightsTourClosed);
        tourButton.addActionListener(e -> toggleKnightsTour());

        // Board editor: clicks and palette drops on squares are reported by the board panel
        boardPanel.setPieceEditListener(new ChessBoardPanel.PieceEditListener() {
            @Override
            public void onSquareClicked(int square) {
                handleEditorSquareClick(square);
            }

            @Override
            public void onPieceDropped(int square, char piece) {
                placeEditorPiece(square, piece);
            }
        });

        boardPanel.addPropertyChangeListener(
                "boardMove",
                evt -> {
                    if (!isEditorMode) {
                        handleManualMove((Board) evt.getOldValue(),(Board) evt.getNewValue());
                    }
                }
        );

        // Miniature board preview for the engine lines; hidden whenever the window loses focus
        miniBoard = new MiniBoardPopup(this);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowDeactivated(java.awt.event.WindowEvent e) {
                hideMiniBoard();
            }
        });

        // Initial UI state setup
        syncCastlingRightsUI();
        recalculateMaterialScore();
        updateStatus();
        updateNotationDisplay();
        updateNavigationButtons();

        setMinimumSize(new Dimension(1180, 880));
        pack();
        setSize(new Dimension(1200, 900));
        setLocationRelativeTo(null);
    }

    // --- Piece Palette Generator ---
    private JPanel createPiecePalette(boolean isWhite) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        panel.setBackground(new Color(235, 238, 240));
        panel.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180), 1));

        char[] pieces = isWhite ? new char[]{'♔', '♕', '♖', '♗', '♘', '♙'}
                : new char[]{'♚', '♛', '♜', '♝', '♞', '♟'};

        char[] pieceCodes = isWhite ? new char[]{'K', 'Q', 'R', 'B', 'N', 'P'}
                : new char[]{'k', 'q', 'r', 'b', 'n', 'p'};

        Font symbolFont = new Font("Segoe UI Symbol", Font.PLAIN, 20);

        for (int i = 0; i < pieces.length; i++) {
            char symbol = pieces[i];
            char code = pieceCodes[i];

            JButton btn = new JButton(String.valueOf(symbol));
            btn.setFont(symbolFont);
            btn.setPreferredSize(new Dimension(48, 42));
            btn.setMargin(new Insets(0, 0, 0, 0));
            btn.setFocusable(false);
            btn.setBackground(Color.WHITE);

            // Click = select piece for click-to-place; drag = drop it on any square
            btn.addActionListener(e -> selectPalettePiece(code));
            makeDraggable(btn, code, String.valueOf(symbol));

            paletteButtons.put(btn, code);
            panel.add(btn);
        }

        // Add Delete Piece button ("❌")
        JButton deleteBtn = new JButton("❌");
        deleteBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        deleteBtn.setPreferredSize(new Dimension(48, 42));
        deleteBtn.setMargin(new Insets(0, 0, 0, 0));
        deleteBtn.setFocusable(false);
        deleteBtn.setBackground(Color.RED);
        deleteBtn.setToolTipText("Delete piece mode (click again to turn off)");

        deleteBtn.addActionListener(e -> selectPalettePiece('X'));

        paletteButtons.put(deleteBtn, 'X');
        panel.add(deleteBtn);

        return panel;
    }

    /* Lets a palette button be dragged onto the board. The dragged data is the
     single piece character (e.g. 'Q' or 'p'), received by ChessBoardPanel.
     */
    private void makeDraggable(JButton btn, char pieceCode, String symbol) {
        TransferHandler handler = new TransferHandler() {
            @Override
            protected Transferable createTransferable(JComponent c) {
                return new StringSelection(String.valueOf(pieceCode));
            }

            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }
        };

        handler.setDragImage(createPieceDragImage(symbol));
        handler.setDragImageOffset(new Point(24, 24));
        btn.setTransferHandler(handler);

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
                if (!isEditorMode || pressPoint == null || !btn.isEnabled()) {
                    return;
                }

                // Small threshold so a normal click is not mistaken for a drag
                if (pressPoint.distance(e.getPoint()) < 5) {
                    return;
                }

                pressPoint = null;
                handler.exportAsDrag(btn, e, TransferHandler.COPY);
            }
        };

        btn.addMouseListener(dragStarter);
        btn.addMouseMotionListener(dragStarter);
    }

    private BufferedImage createPieceDragImage(String symbol) {
        BufferedImage img = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 40));
        g2.setColor(new Color(40, 40, 40));

        FontMetrics fm = g2.getFontMetrics();
        int x = (48 - fm.stringWidth(symbol)) / 2;
        int y = (48 - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(symbol, x, y);

        g2.dispose();
        return img;
    }

    /*
     Selects a palette piece (or delete mode 'X'). Selecting the same one again
     switches it off. Both delete buttons share the code 'X', so clicking either
     delete button while delete mode is active turns it off.
     */
    private void selectPalettePiece(char pieceCode) {
        if (selectedEditorPiece == pieceCode) {
            selectedEditorPiece = '\0';
        } else {
            selectedEditorPiece = pieceCode;
        }

        updatePaletteHighlight();
    }

    private void updatePaletteHighlight() {
        for (Map.Entry<JButton, Character> entry : paletteButtons.entrySet()) {
            boolean selected = selectedEditorPiece != '\0'
                    && entry.getValue() == selectedEditorPiece;

            JButton btn = entry.getKey();
            btn.setBackground(selected ? new Color(175, 215, 245) : Color.WHITE);
            btn.setBorder(selected ? BorderFactory.createLineBorder(new Color(30, 110, 200), 3)
                    : UIManager.getBorder("Button.border"));
        }
    }

    // Board Editor Operations
    private void enterEditorMode() {
        isEditorMode = true;
        refreshAnalysis();   // hides the evaluation while the board is being edited

        topPalettePanel.setVisible(true);
        bottomPalettePanel.setVisible(true);
        castlingAndPlayerPanel.setVisible(true);
        startingPositionButton.getParent().setVisible(true);

        // the variant drop-down takes the place of the GAME section
        gameSection.setVisible(false);
        variantCombo.setVisible(true);
        syncVariantComboWithBoard();

        setMainUIEnabled(false);
        boardEditorButton.setEnabled(false);
        tourButton.setEnabled(false);

        boardPanel.setEditorMode(true);
        updateCheckBoxes();
        syncCastlingRightsUI();
        recalculateMaterialScore();
        updateChess960PanelVisibility();

        statusLabel.setText("Board Editor Active");
    }

    /*Click on a board square while in editor mode: place the selected palette
     piece, or delete the piece if delete mode is active.
     */
    private void handleEditorSquareClick(int square) {
        if (!isEditorMode || selectedEditorPiece == '\0') {
            return;
        }

        if (selectedEditorPiece == 'X') {
            deleteEditorPiece(square);
        } else {
            placeEditorPiece(square, selectedEditorPiece);
        }
    }

    private void deleteEditorPiece(int square) {
        Board board = boardPanel.getBoard();
        if (board == null || board.getPiece(square) == '.') {
            return;
        }

        if (racingEditRejected(board, square, '.')) {
            return;
        }

        board.setPiece(square, '.');
        afterEditorChange(board);
    }

    /*
     Places a piece (from a click or a drag and drop) enforcing the editor rules:
     pawns not on the 1st/8th rank, and only one king of each colour.
     */
    private void placeEditorPiece(int square, char piece) {
        if (!isEditorMode || "KQRBNPkqrbnp".indexOf(piece) < 0) {
            return;
        }

        Board board = boardPanel.getBoard();
        if (board == null) {
            return;
        }

        String placementError = HordeChess.placementError(board, square, piece);

        if (placementError != null) {
            // Show the dialog after the current click/drop event has finished
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    this,
                    placementError,
                    "Invalid Placement",
                    JOptionPane.WARNING_MESSAGE
            ));
            return;
        }

        // Racing Kings: no pawns, and neither king may ever stand in check
        if (board.isRacingKings() && (piece == 'P' || piece == 'p')) {
            return;
        }
        if (racingEditRejected(board, square, piece)) {
            return;
        }

        // Kings are never duplicated: placing a king moves the existing one
        if (piece == 'K' || piece == 'k') {
            for (int sq = 0; sq < 64; sq++) {
                if (sq != square && board.getPiece(sq) == piece) {
                    board.setPiece(sq, '.');
                }
            }
        }

        board.setPiece(square, piece);
        afterEditorChange(board);
    }

    private void afterEditorChange(Board board) {
        board.setEnPassantSquare(-1); // any en passant target is meaningless once edited

        syncCastlingRightsUI();
        boardPanel.refreshBoard();
        recalculateMaterialScore();
    }

    /*
     Copies side-to-move and castling checkboxes into the board being edited.
     */
    private void applyEditorOptionsToBoard() {
        if (syncingUI || !isEditorMode) {
            return;
        }

        Board board = boardPanel.getBoard();
        if (board == null) {
            return;
        }

        board.setWhiteToMove(activePlayerCombo.getSelectedIndex() == 0);
        board.setWhiteKingSideCastle(whiteKingSideCb.isSelected());
        board.setWhiteQueenSideCastle(whiteQueenSideCb.isSelected());
        board.setBlackKingSideCastle(blackKingSideCb.isSelected());
        board.setBlackQueenSideCastle(blackQueenSideCb.isSelected());
    }

    private void resetToStartingPosition() {
        Board board = new Board();

        Board current = boardPanel.getBoard();
        if (current != null && current.isHorde()) {
            HordeChess.setupStartingPosition(board);
        } else if (current != null && current.isAtomic()) {
            board.setAtomic(true);
        } else if (current != null && current.isThreeCheck()) {
            board.setThreeCheck(true);
        } else if (current != null && current.isAntiChess()) {
            board.setAntiChess(true);
        } else if (current != null && current.isKingOfTheHill()) {
            board.setKingOfTheHill(true);
        } else if (current != null && current.isRacingKings()) {
            RacingKings.setupStartingPosition(board);
        } else if (current != null && current.isChess960()) {
            Chess960.setupStartingPosition(board, chess960Box.getValue());
        }

        boardPanel.setBoard(board);
        syncCastlingRightsUI();
        recalculateMaterialScore();
    }

    //Variants (board editor drop-down)

    //Shows the drop-down entry that matches the board being edited.
    private void syncVariantComboWithBoard() {
        Board board = boardPanel.getBoard();
        boolean horde = board != null && board.isHorde();
        boolean atomic = board != null && board.isAtomic();
        boolean threeCheck = board != null && board.isThreeCheck();
        boolean antiChess = board != null && board.isAntiChess();
        boolean kingOfTheHill = board != null && board.isKingOfTheHill();
        boolean racingKings = board != null && board.isRacingKings();
        boolean chess960 = board != null && board.isChess960();

        String name = HordeChess.VARIANT_STANDARD;
        if (horde) {
            name = HordeChess.VARIANT_HORDE;
        } else if (atomic) {
            name = AtomicChess.VARIANT_ATOMIC;
        } else if (threeCheck) {
            name = ThreeCheck.VARIANT_THREE_CHECK;
        } else if (antiChess) {
            name = AntiChess.VARIANT_ANTI_CHESS;
        } else if (kingOfTheHill) {
            name = KingOfTheHill.VARIANT_KING_OF_THE_HILL;
        } else if (racingKings) {
            name = RacingKings.VARIANT_RACING_KINGS;
        } else if (chess960) {
            name = Chess960.VARIANT_CHESS960;
        }

        syncingUI = true;
        try {
            variantCombo.setSelectedItem(name);
        } finally {
            syncingUI = false;
        }

        updateVariantUI(horde);
        updateChess960PanelVisibility();
    }

    //The user picked a variant: put that variant's starting position on the board.
    private void onVariantSelected() {
        if (syncingUI || !isEditorMode) {
            return;
        }

        Object chosen = variantCombo.getSelectedItem();
        boolean horde = HordeChess.VARIANT_HORDE.equals(chosen);
        boolean atomic = AtomicChess.VARIANT_ATOMIC.equals(chosen);
        boolean threeCheck = ThreeCheck.VARIANT_THREE_CHECK.equals(chosen);
        boolean antiChess = AntiChess.VARIANT_ANTI_CHESS.equals(chosen);
        boolean kingOfTheHill = KingOfTheHill.VARIANT_KING_OF_THE_HILL.equals(chosen);
        boolean racingKings = RacingKings.VARIANT_RACING_KINGS.equals(chosen);
        boolean chess960 = Chess960.VARIANT_CHESS960.equals(chosen);

        // Standard and Atomic start from the normal position; Horde has its own
        Board board = new Board();
        if (horde) {
            HordeChess.setupStartingPosition(board);
        } else if (atomic) {
            board.setAtomic(true);
        } else if (threeCheck) {
            board.setThreeCheck(true);
        } else if (antiChess) {
            board.setAntiChess(true); // also switches castling off
        } else if (kingOfTheHill) {
            board.setKingOfTheHill(true);
        } else if (racingKings) {
            RacingKings.setupStartingPosition(board);
        } else if (chess960) {
            Chess960.setupStartingPosition(board, chess960Box.getValue());
        }

        boardPanel.setBoard(board);
        updateChess960PanelVisibility();
        updateVariantUI(horde);
        syncCastlingRightsUI();   // white castling boxes are disabled when White has no king
        recalculateMaterialScore();
    }

    // Chess960: the number box is shown (instead of the move navigation) while a Chess960 board is edited.
    private void updateChess960PanelVisibility() {
        Board edited = boardPanel.getBoard();
        boolean show = isEditorMode && edited != null && edited.isChess960();
        chess960Box.setVisible(show);
        navigationTitleLabel.setVisible(!show);
        navigationPanelRef.setVisible(!show);
        sidePanelRevalidate();
    }

    private void sidePanelRevalidate() {
        Container parent = chess960Box.getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    // The number in the Chess960 box changed: set up that starting position.
    private void onChess960NumberChanged(int number) {
        if (!isEditorMode) {
            return;
        }
        Board board = new Board();
        Chess960.setupStartingPosition(board, number);
        boardPanel.setBoard(board);
        syncCastlingRightsUI();
        recalculateMaterialScore();
    }

    //Horde chess: White has no king, so the white king cannot be picked from the palette.
    private void updateVariantUI(boolean horde) {
        for (Map.Entry<JButton, Character> entry : paletteButtons.entrySet()) {
            if (entry.getValue() == 'K') {
                entry.getKey().setEnabled(!horde);
            }
        }

        if (horde && selectedEditorPiece == 'K') {
            selectedEditorPiece = '\0';
            updatePaletteHighlight();
        }

        // Racing Kings has no pawns: they cannot be picked from the palette
        Board edited = boardPanel.getBoard();
        boolean racing = edited != null && edited.isRacingKings();
        for (Map.Entry<JButton, Character> entry : paletteButtons.entrySet()) {
            if (entry.getValue() == 'P' || entry.getValue() == 'p') {
                entry.getKey().setEnabled(!racing);
            }
        }
        if (racing && (selectedEditorPiece == 'P' || selectedEditorPiece == 'p')) {
            selectedEditorPiece = '\0';
            updatePaletteHighlight();
        }
    }

    /*
     Racing Kings editor rule: a change that would leave either king in check is refused with a dialog.
     piece '.' means the square is being emptied. Returns true when the change must not be made.
     */
    private boolean racingEditRejected(Board board, int square, char piece) {
        if (!board.isRacingKings()) {
            return false;
        }

        Board test = board.copy();
        if (piece == 'K' || piece == 'k') {
            for (int sq = 0; sq < 64; sq++) {
                if (sq != square && test.getPiece(sq) == piece) {
                    test.setPiece(sq, '.');
                }
            }
        }
        test.setPiece(square, piece);

        String error = RacingKings.checkError(test);
        if (error == null) {
            return false;
        }

        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                this, error, "Invalid Placement", JOptionPane.WARNING_MESSAGE));
        return true;
    }

    private void clearBoardPieces() {
        Board board = boardPanel.getBoard();
        if (board == null) {
            return;
        }

        for (int sq = 0; sq < 64; sq++) {
            board.setPiece(sq, '.');
        }

        board.setEnPassantSquare(-1);
        board.setWhiteKingSideCastle(false);
        board.setWhiteQueenSideCastle(false);
        board.setBlackKingSideCastle(false);
        board.setBlackQueenSideCastle(false);

        syncCastlingRightsUI();
        boardPanel.refreshBoard(); // squares are buttons, so they must be refreshed explicitly
        recalculateMaterialScore();
    }

    private void exitEditorAndAnalyse() {
        Board board = boardPanel.getBoard();
        if (board == null) return;

        int whiteKingCount = 0;
        int blackKingCount = 0;

        for (int sq = 0; sq < 64; sq++) {
            char p = board.getPiece(sq);
            if (p == 'K') whiteKingCount++;
            if (p == 'k') blackKingCount++;
        }

        boolean hordeBoard = board.isHorde();
        int expectedWhiteKings = hordeBoard ? 0 : 1;

        if (!board.isAntiChess() && (whiteKingCount != expectedWhiteKings || blackKingCount != 1)) {
            JOptionPane.showMessageDialog(
                    this,
                    hordeBoard
                            ? "In Horde chess White has no king and Black must have exactly 1 King to analyse."
                            : "The board must have exactly 1 White King and 1 Black King to analyse.",
                    "Invalid Board Position",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        boolean whiteToMoveChosen = activePlayerCombo.getSelectedIndex() == 0;
        String racingError = board.isRacingKings() ? RacingKings.checkError(board) : null;
        if (racingError != null) {
            JOptionPane.showMessageDialog(this, racingError, "Invalid Board Position", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (isKingInCheck(board, !whiteToMoveChosen)) {
            JOptionPane.showMessageDialog(
                    this,
                    "Illegal position: the side that is not to move cannot be in check.",
                    "Invalid Board Position",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        board.setWhiteToMove(whiteToMoveChosen);
        board.setWhiteKingSideCastle(whiteKingSideCb.isSelected());
        board.setWhiteQueenSideCastle(whiteQueenSideCb.isSelected());
        board.setBlackKingSideCastle(blackKingSideCb.isSelected());
        board.setBlackQueenSideCastle(blackQueenSideCb.isSelected());

        if (board.isChess960()) {
            // remember which rooks castle, and which numbered start this is (if it still is one)
            Chess960.configureCastling(board);
            board.setChess960Id(Chess960.matchesStart(board, chess960Box.getValue()) ? chess960Box.getValue() : -1);
        }

        isEditorMode = false;
        selectedEditorPiece = '\0';
        updatePaletteHighlight();
        boardPanel.setEditorMode(false);

        topPalettePanel.setVisible(false);
        bottomPalettePanel.setVisible(false);
        castlingAndPlayerPanel.setVisible(false);
        startingPositionButton.getParent().setVisible(false);

        variantCombo.setVisible(false);
        gameSection.setVisible(true);
        updateChess960PanelVisibility();

        setMainUIEnabled(true);
        boardEditorButton.setEnabled(true);
        tourButton.setEnabled(true);

        rootNode = new MoveNode(board.copy(), null, "Custom", 0);
        currentNode = rootNode;

        recalculateMaterialScore();
        updateStatus();
        updateNotationDisplay();
        updateNavigationButtons();
    }

    private void setMainUIEnabled(boolean enabled) {
        newGameButton.setEnabled(enabled);
        loadFenButton.setEnabled(enabled);
        engineButton.setEnabled(enabled);
        backButton.setEnabled(enabled && currentNode.parent != null);
        forwardButton.setEnabled(enabled && !currentNode.children.isEmpty());
        flipButton.setEnabled(enabled);
    }

    // --- Knight's Tour ---
    private void toggleKnightsTour() {
        if (knightsTour.isActive()) {
            knightsTour.cancel();
            return;
        }
        if (isEditorMode) {
            return;
        }

        boardEditorButton.setEnabled(false);
        knightsTour.start();
    }

    /** Called when the knight's tour ends or is cancelled: bring the game back. */
    private void onKnightsTourClosed() {
        boardEditorButton.setEnabled(true);
        tourButton.setEnabled(true);
        selectNode(currentNode);
    }

    private void toggleBoardOrientation() {
        isFlipped = !isFlipped;
        boardPanel.setFlipped(isFlipped);
        evalBar.setFlipped(isFlipped);
        updateCheckBoxes();
        boardPanel.repaint();
    }

    //Evaluation bar & best lines

    private static final class AnalysisSnapshot {
        final int depth;
        final List<ChessEngine.AnalysisLine> lines;

        AnalysisSnapshot(int depth, List<ChessEngine.AnalysisLine> lines) {
            this.depth = depth;
            this.lines = lines;
        }
    }

    private void stopAnalysis() {
        analysisGeneration++;
        hideMiniBoard();

        if (analysisEngine != null) {
            analysisEngine.cancel();
            analysisEngine = null;
        }
        if (analysisWorker != null) {
            analysisWorker.cancel(false);
            analysisWorker = null;
        }
    }

    /*
     Called whenever the shown position changes (and when the toggle is clicked):
     hides everything when the toggle is off, otherwise starts a fresh analysis.
     */
    private void refreshAnalysis() {
        stopAnalysis();
        analysisAnchor = null;

        boolean show = analysisToggle.isSelected() && !isEditorMode && currentNode != null;
        analysisPanel.setVisible(show);
        evalBar.setVisible(show);

        if (!show) {
            return;
        }

        Board position = currentNode.board.copy();
        int result = gameResult(position);

        if (result != 0) {
            showFinishedPosition(result);
            return;
        }

        // the old lines belong to another position: they must not stay clickable
        for (int i = 0; i < analysisMovePanels.length; i++) {
            clearAnalysisRow(i);
        }

        analysisAnchor = currentNode;
        analysisDepthLabel.setText("analysing...");
        startAnalysis(position);
    }

    private void startAnalysis(Board position) {
        final Board uiBoard = position.copy();     // used on the EDT to write the moves
        final ChessEngine analysing = new ChessEngine();
        final int generation = analysisGeneration;

        analysisEngine = analysing;

        SwingWorker<Void, AnalysisSnapshot> worker = new SwingWorker<Void, AnalysisSnapshot>() {
            @Override
            protected Void doInBackground() {
                analysing.analyse(position, 3, 24, 6000,
                        (depth, lines) -> publish(new AnalysisSnapshot(depth, lines)));
                return null;
            }

            @Override
            protected void process(List<AnalysisSnapshot> chunks) {
                if (generation != analysisGeneration || chunks.isEmpty()) {
                    return;
                }
                applyAnalysis(uiBoard, chunks.get(chunks.size() - 1));
            }
        };

        analysisWorker = worker;
        worker.execute();
    }

    private void applyAnalysis(Board position, AnalysisSnapshot snapshot) {
        List<ChessEngine.AnalysisLine> lines = snapshot.lines;

        for (int i = 0; i < analysisScoreLabels.length; i++) {
            JLabel score = analysisScoreLabels[i];
            PvLinePanel moves = analysisMovePanels[i];

            if (i < lines.size()) {
                ChessEngine.AnalysisLine line = lines.get(i);

                score.setText(formatEval(line));
                styleScoreLabel(score, line.mateIn > 0 || (line.mateIn == 0 && line.scoreCp >= 0));
                moves.setSteps(pvToSteps(position, line.pv));
            } else {
                clearAnalysisRow(i);
            }
        }

        if (!lines.isEmpty()) {
            ChessEngine.AnalysisLine best = lines.get(0);
            String text = formatEval(best);

            analysisEvalLabel.setText(text);
            evalBar.setEvaluation(evalShare(best), text);
        }

        analysisDepthLabel.setText("Depth " + snapshot.depth);
    }

    private void showFinishedPosition(int result) {
        for (int i = 0; i < analysisScoreLabels.length; i++) {
            clearAnalysisRow(i);
        }

        if (result == 2) {
            analysisEvalLabel.setText("0.0");
            analysisDepthLabel.setText("Draw");
            evalBar.setEvaluation(0.5, "0.0");
        } else {
            analysisEvalLabel.setText("-");
            analysisDepthLabel.setText(result > 0 ? "Game over - White wins" : "Game over - Black wins");
            evalBar.setEvaluation(result > 0 ? 1.0 : 0.0, "-");
        }
    }

    private void clearAnalysisRow(int row) {
        analysisScoreLabels[row].setText(" ");
        analysisScoreLabels[row].setOpaque(false);
        analysisScoreLabels[row].setBorder(null);
        analysisMovePanels[row].setSteps(null);
    }

    private void styleScoreLabel(JLabel label, boolean whiteBetter) {
        label.setOpaque(true);
        label.setBackground(whiteBetter ? new Color(250, 250, 250) : new Color(70, 70, 70));
        label.setForeground(whiteBetter ? new Color(30, 30, 30) : new Color(245, 245, 245));
        label.setBorder(BorderFactory.createLineBorder(new Color(150, 150, 150)));
    }

    /** +1.3 / -0.4 / 0.0 in pawns, or +M5 / -M5 when a forced mate has been found. */
    private String formatEval(ChessEngine.AnalysisLine line) {
        if (line.mateIn != 0) {
            return (line.mateIn > 0 ? "+M" : "-M") + Math.abs(line.mateIn);
        }

        double pawns = Math.max(-99.9, Math.min(99.9, line.scoreCp / 100.0));

        if (Math.round(pawns * 10) == 0) {
            return "0.0";
        }

        return String.format(java.util.Locale.ROOT, "%+.1f", pawns);
    }

    /** Share of the bar that is white: 0.5 = equal, 1.0 = White is winning. */
    private double evalShare(ChessEngine.AnalysisLine line) {
        if (line.mateIn > 0) {
            return 1.0;
        }
        if (line.mateIn < 0) {
            return 0.0;
        }
        return 1.0 / (1.0 + Math.exp(-0.00368208 * line.scoreCp));
    }

    /*
     The expected continuation as separate moves ("6." "Bd3", "e6", "7." "O-O" ...), each with
     the position after it, so every move can be previewed and clicked.
     */
    private List<PvLinePanel.Step> pvToSteps(Board start, List<Move> pv) {
        List<PvLinePanel.Step> steps = new ArrayList<>();
        Board current = start.copy();

        int fullMove = currentNode.ply / 2 + 1;
        boolean whiteMoves = current.isWhiteToMove();
        boolean first = true;

        try {
            for (Move move : pv) {
                Board before = current.copy();
                MoveGenerator.makeMove(current, move);

                String san = createNotation(before, current, move,
                        before.getWhiteChecks(), before.getBlackChecks());

                String number = "";
                if (whiteMoves) {
                    number = fullMove + ".";
                } else if (first) {
                    number = fullMove + "...";
                }

                steps.add(new PvLinePanel.Step(number, san, current.copy(), move));

                if (!whiteMoves) {
                    fullMove++;
                }
                whiteMoves = !whiteMoves;
                first = false;
            }
        } catch (RuntimeException ex) {
            // keep whatever could be built
        }

        return steps;
    }

    //Engine lines: hover = miniature board, click = play the line

    private PvLinePanel.Listener createPvListener() {
        return new PvLinePanel.Listener() {
            @Override
            public void onHover(PvLinePanel source, int index, Rectangle tokenBounds) {
                List<PvLinePanel.Step> steps = source.getSteps();
                if (miniBoard == null || index < 0 || index >= steps.size() || !source.isShowing()) {
                    return;
                }

                PvLinePanel.Step step = steps.get(index);
                Point origin = source.getLocationOnScreen();

                // anchor = the whole row height, so the preview never lands under the pointer
                Rectangle anchor = new Rectangle(origin.x + tokenBounds.x, origin.y,
                        tokenBounds.width, source.getHeight());

                miniBoard.showPosition(step.after, step.move, isFlipped, anchor);
            }

            @Override
            public void onHoverEnd(PvLinePanel source) {
                hideMiniBoard();
            }

            @Override
            public void onClick(PvLinePanel source, int index) {
                playEngineLine(source.getSteps(), index);
            }
        };
    }

    private void hideMiniBoard() {
        if (miniBoard != null) {
            miniBoard.hidePopup();
        }
    }

    /*
     Writes the whole engine line into the move tree (as a variation, or as the continuation of
     the game when there is none yet) and shows the position after the clicked move. Selecting
     the node updates the board, the MOVES list and restarts the evaluation for that position.
     */
    private void playEngineLine(List<PvLinePanel.Step> steps, int clickedIndex) {
        hideMiniBoard();

        MoveNode anchor = analysisAnchor;
        if (isEditorMode || knightsTour.isActive() || anchor == null || anchor != currentNode
                || !isSubtreeOf(anchor, rootNode) || clickedIndex < 0 || clickedIndex >= steps.size()) {
            return;
        }

        MoveNode node = anchor;
        MoveNode target = null;

        try {
            for (int i = 0; i < steps.size(); i++) {
                PvLinePanel.Step step = steps.get(i);
                node = findOrCreateChild(node, node.board, step.after, step.move);

                if (i == clickedIndex) {
                    target = node;
                }
            }
        } catch (RuntimeException ex) {
            // use as much of the line as could be added
        }

        if (target != null) {
            selectNode(target);
        }
    }

    //0 = still being played, 1 = White has won, -1 = Black has won, 2 = drawn.
    private int gameResult(Board board) {
        boolean whiteToMove = board.isWhiteToMove();

        if (board.isAtomic()) {
            if (AtomicChess.hasLostKing(board, true)) return -1;
            if (AtomicChess.hasLostKing(board, false)) return 1;
        }

        if (board.isThreeCheck()) {
            if (board.getWhiteChecks() >= 3) return 1;
            if (board.getBlackChecks() >= 3) return -1;
        }

        if (board.isRacingKings()) {
            switch (RacingKings.getResult(board)) {
                case WHITE_WINS: return 1;
                case BLACK_WINS: return -1;
                case DRAW_BOTH_REACHED:
                case STALEMATE: return 2;
                default: return 0;
            }
        }

        if (board.isKingOfTheHill()) {
            if (KingOfTheHill.hasKingOnHill(board, true)) return 1;
            if (KingOfTheHill.hasKingOnHill(board, false)) return -1;
        }

        if (board.isAntiChess()) {
            if (AntiChess.hasNoPieces(board, true)) return 1;
            if (AntiChess.hasNoPieces(board, false)) return -1;
        }

        if (!MoveGenerator.generateLegalMoves(board).isEmpty()) {
            return 0;
        }

        if (board.isAntiChess()) {
            return whiteToMove ? 1 : -1;      // no moves left means the side to move wins
        }
        if (board.isHorde() && HordeChess.whiteHasNoPieces(board)) {
            return -1;
        }
        if (isKingInCheck(board, whiteToMove)) {
            return whiteToMove ? -1 : 1;      // checkmate
        }

        return 2;                              // stalemate
    }

    // Three-Check boxes

    private JPanel createCheckPanel(JLabel[] marks) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (int i = 0; i < marks.length; i++) {
            marks[i] = new JLabel("➕");
            marks[i].setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
            panel.add(marks[i]);
        }

        panel.setVisible(false);
        return panel;
    }

    private void fillCheckMarks(JLabel[] marks, int checks) {
        for (int i = 0; i < marks.length; i++) {
            marks[i].setText(i < checks ? "❌" : "➕");
        }
    }

    /*
      Shows the two boxes only in Three-Check (outside the editor). Each check a colour
     has given turns the next "➕" (left to right) into a "❌". The box at the bottom
     belongs to the colour at the bottom of the board, so they swap when the board is flipped.
     */
    private void updateCheckBoxes() {
        boolean show = !isEditorMode && currentNode != null && currentNode.board.isThreeCheck();

        topCheckPanel.setVisible(show);
        bottomCheckPanel.setVisible(show);

        if (!show) {
            return;
        }

        Board board = currentNode.board;
        boolean topIsBlack = !isFlipped;

        fillCheckMarks(topCheckMarks, topIsBlack ? board.getBlackChecks() : board.getWhiteChecks());
        fillCheckMarks(bottomCheckMarks, topIsBlack ? board.getWhiteChecks() : board.getBlackChecks());

        topCheckPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180), 1), topIsBlack ? "Black" : "White"));
        bottomCheckPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180), 1), topIsBlack ? "White" : "Black"));

        topCheckPanel.revalidate();
        bottomCheckPanel.revalidate();
    }

    private JLabel createSectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 14));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void syncCastlingRightsUI() {
        syncingUI = true;
        try {
            syncCastlingRightsUIInternal();
        } finally {
            syncingUI = false;
        }
    }

    private void syncCastlingRightsUIInternal() {
        Board currentBoard = boardPanel.getBoard();
        if (currentBoard == null) return;

        activePlayerCombo.setSelectedIndex(currentBoard.isWhiteToMove() ? 0 : 1);

        if (currentBoard.isChess960()) {
            Chess960.configureCastling(currentBoard);   // the rooks may have been moved in the editor
        }
        boolean c960 = currentBoard.isChess960();

        boolean whiteKingOnRank1 = c960 ? Chess960.castlingRookFile(currentBoard, true, true) >= 0
                || Chess960.castlingRookFile(currentBoard, true, false) >= 0 : (currentBoard.getPiece(4) == 'K');
        boolean whiteKingRookOnRank1 = c960 ? Chess960.castlingRookFile(currentBoard, true, true) >= 0 : (currentBoard.getPiece(7) == 'R');
        boolean whiteQueenRookOnRank1 = c960 ? Chess960.castlingRookFile(currentBoard, true, false) >= 0 : (currentBoard.getPiece(0) == 'R');

        boolean noCastling = currentBoard.isAntiChess() || currentBoard.isRacingKings(); // no castling in these variants

        whiteKingSideCb.setEnabled(!noCastling && whiteKingOnRank1 && whiteKingRookOnRank1);
        whiteQueenSideCb.setEnabled(!noCastling && whiteKingOnRank1 && whiteQueenRookOnRank1);

        whiteKingSideCb.setSelected(whiteKingSideCb.isEnabled() && currentBoard.canWhiteCastleKingSide());
        whiteQueenSideCb.setSelected(whiteQueenSideCb.isEnabled() && currentBoard.canWhiteCastleQueenSide());

        boolean blackKingOnRank8 = c960 ? Chess960.castlingRookFile(currentBoard, false, true) >= 0
                || Chess960.castlingRookFile(currentBoard, false, false) >= 0 : (currentBoard.getPiece(60) == 'k');
        boolean blackKingRookOnRank8 = c960 ? Chess960.castlingRookFile(currentBoard, false, true) >= 0 : (currentBoard.getPiece(63) == 'r');
        boolean blackQueenRookOnRank8 = c960 ? Chess960.castlingRookFile(currentBoard, false, false) >= 0 : (currentBoard.getPiece(56) == 'r');

        blackKingSideCb.setEnabled(!noCastling && blackKingOnRank8 && blackKingRookOnRank8);
        blackQueenSideCb.setEnabled(!noCastling && blackKingOnRank8 && blackQueenRookOnRank8);

        blackKingSideCb.setSelected(blackKingSideCb.isEnabled() && currentBoard.canBlackCastleKingSide());
        blackQueenSideCb.setSelected(blackQueenSideCb.isEnabled() && currentBoard.canBlackCastleQueenSide());
    }

    private void startNewGame() {
        Board freshBoard = new Board();
        rootNode = new MoveNode(freshBoard.copy(), null, "Start", 0);
        selectNode(rootNode);
    }

    private void loadFEN() {
        String fen = JOptionPane.showInputDialog(this, "Enter FEN String:", "Load FEN", JOptionPane.PLAIN_MESSAGE);
        if (fen != null && !fen.trim().isEmpty()) {
            try {
                Board board = Board.fromFEN(fen.trim());
                rootNode = new MoveNode(board.copy(), null, "FEN", 0);
                selectNode(rootNode);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid FEN string format.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void makeEngineMove() {
        if (isEditorMode || gameIsOver) return;

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        Board searchBoard = currentNode.board.copy();

        // every position of the current line, so the engine can avoid / seek repetitions
        final List<Board> history = new ArrayList<>();
        for (MoveNode n = currentNode; n != null; n = n.parent) {
            history.add(n.board.copy());
        }

        SwingWorker<Move, Void> worker = new SwingWorker<Move, Void>() {
            @Override
            protected Move doInBackground() throws Exception {
                return engine.findBestMove(searchBoard, 16, 5000, history);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                if (knightsTour.isActive()) {
                    return; // the knight's tour took over the board while the engine was thinking
                }
                try {
                    Move bestMove = get();
                    if (bestMove != null) {
                        Board newBoard = currentNode.board.copy();
                        MoveGenerator.makeMove(newBoard, bestMove);
                        recordMove(currentNode.board, newBoard, bestMove);
                    } else {
                        JOptionPane.showMessageDialog(
                            ChessFrame.this,
                            "No legal moves available.",
                            "Game Over",
                            JOptionPane.INFORMATION_MESSAGE
                        );
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        worker.execute();
    }
    private void handleManualMove(Board before, Board after) {
        if (gameIsOver) return;
        Move move = findMoveBetween(before, after);
        if (move != null) {
            recordMove(before, after, move);
        }
    }

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

        char piece = switch (choice) {
            case 1 -> 'R';
            case 2 -> 'B';
            case 3 -> 'N';
            default -> 'Q';
        };

        return isWhite ? Character.toUpperCase(piece) : Character.toLowerCase(piece);
    }

    private Move findMoveBetween(Board before, Board after) {
        List<Move> legalMoves = MoveGenerator.generateLegalMoves(before);

        for (Move move : legalMoves) {
            Board test = before.copy();
            MoveGenerator.makeMove(test, move);
            if (boardsEqual(test, after)) {
                return move;
            }
        }

        Move candidatePromotion = null;
        for (Move move : legalMoves) {
            if (move.promotion != '\0' && move.promotion != ' ') {
                if (before.getPiece(move.from) == 'P' || before.getPiece(move.from) == 'p') {
                    if (after.getPiece(move.to) != '.' && Math.abs(move.from - move.to) >= 7) {
                        candidatePromotion = move;
                        break;
                    }
                }
            }
        }

        if (candidatePromotion != null) {
            char chosen = promptPromotionPiece(before.isWhiteToMove());
            for (Move move : legalMoves) {
                if (move.from == candidatePromotion.from
                        && move.to == candidatePromotion.to
                        && Character.toUpperCase(move.promotion) == Character.toUpperCase(chosen)) {
                    return move;
                }
            }
        }

        return null;
    }

    private boolean boardsEqual(Board a, Board b) {
        if (a.isWhiteToMove() != b.isWhiteToMove()) return false;
        if (a.getEnPassantSquare() != b.getEnPassantSquare()) return false;
        if (a.canWhiteCastleKingSide() != b.canWhiteCastleKingSide()) return false;
        if (a.canWhiteCastleQueenSide() != b.canWhiteCastleQueenSide()) return false;
        if (a.canBlackCastleKingSide() != b.canBlackCastleKingSide()) return false;
        if (a.canBlackCastleQueenSide() != b.canBlackCastleQueenSide()) return false;

        for (int square = 0; square < 64; square++) {
            if (a.getPiece(square) != b.getPiece(square)) {
                return false;
            }
        }
        return true;
    }

    private boolean isKingInCheck(Board board, boolean isWhite) {
        if (board.isAntiChess() || board.isRacingKings()) {
            return false; // no check in Anti-Chess; in Racing Kings a check can never occur
        }

        if (board.isAtomic()) {
            return AtomicChess.isKingInCheck(board, isWhite);
        }

        char targetKing = isWhite ? 'K' : 'k';
        int kingSquare = -1;

        for (int sq = 0; sq < 64; sq++) {
            if (board.getPiece(sq) == targetKing) {
                kingSquare = sq;
                break;
            }
        }

        if (kingSquare == -1) return false;
        return MoveGenerator.isSquareAttacked(board, kingSquare, !isWhite);
    }

    /**
     * Standard disambiguation for pieces of the same type that can legally reach the same square
     * (knights, bishops, rooks, queens): the file if it is enough, else the rank, else both.
     * Kings never need it, and pawn captures already carry their file.
     */
    private String disambiguation(Board before, Move move) {
        char piece = before.getPiece(move.from);
        if (Character.toUpperCase(piece) == 'K') {
            return "";
        }

        boolean sameFile = false;
        boolean sameRank = false;
        boolean ambiguous = false;

        for (Move other : MoveGenerator.generateLegalMoves(before)) {
            if (other.to != move.to || other.from == move.from) {
                continue;
            }
            if (before.getPiece(other.from) != piece) {
                continue;
            }

            ambiguous = true;
            if (other.from % 8 == move.from % 8) {
                sameFile = true;
            }
            if (other.from / 8 == move.from / 8) {
                sameRank = true;
            }
        }

        if (!ambiguous) {
            return "";
        }

        String file = String.valueOf((char) ('a' + move.from % 8));
        String rank = String.valueOf(move.from / 8 + 1);

        if (!sameFile) {
            return file;
        }
        if (!sameRank) {
            return rank;
        }
        return file + rank;
    }

    private String createNotation(Board before, Board after, Move move, int whiteChecksGiven, int blackChecksGiven) {
        StringBuilder sb = new StringBuilder();

        // Castling gets the same check / checkmate suffix as every other move
        if (move.castleKingSide || move.castleQueenSide) {
            sb.append(move.castleKingSide ? "O-O" : "O-O-O");
        } else {
            char piece = Character.toUpperCase(before.getPiece(move.from));
            boolean isCapture = (before.getPiece(move.to) != '.') || move.enPassant;

            if (piece != 'P') {
                sb.append(piece);
                sb.append(disambiguation(before, move));
            } else if (isCapture) {
                int file = move.from % 8;
                sb.append((char) ('a' + file));
            }

            if (isCapture) {
                sb.append('x');
            }

            int toCol = move.to % 8;
            int toRank = move.to / 8;
            sb.append((char) ('a' + toCol)).append(toRank + 1);

            if (move.promotion != '\0' && move.promotion != ' ') {
                sb.append('=').append(Character.toUpperCase(move.promotion));
            }
        }

        // --- Variant Game Ending Notation Checks ---

        if (after.isHorde() && HordeChess.whiteHasNoPieces(after)) {
            sb.append('#'); // Black takes all pieces from white in horde variation
        } else if (after.isAtomic() && AtomicChess.hasLostKing(after, after.isWhiteToMove())) {
            sb.append('#'); // The king was blown off
        } else if (after.isRacingKings()) {
            boolean moverWhite = before.isWhiteToMove();
            boolean arrived = RacingKings.kingOnEighth(after, moverWhite)
                    && !RacingKings.kingOnEighth(before, moverWhite);

            if (arrived && RacingKings.kingOnEighth(after, !moverWhite)) {
                sb.append(" 1/2-1/2");   // Black answered White's arrival: drawn race
            } else if (arrived) {
                sb.append('#');          // first king to reach the 8th rank
            } else if (RacingKings.getResult(after) == RacingKings.Result.STALEMATE) {
                sb.append(" 1/2-1/2");
            }
        } else if (after.isKingOfTheHill() && KingOfTheHill.hasKingOnHill(after, before.isWhiteToMove())) {
            sb.append('#'); // the king has climbed the hill
        } else if (after.isAntiChess()) {
            // Anti-Chess: The game ends with '#' when a side loses all pieces or has no legal moves
            boolean previousMoverIsWhite = before.isWhiteToMove();
            boolean previousMoverHasNoPieces = (after.getMaterialScore(previousMoverIsWhite) == 0);
            List<Move> legalMoves = MoveGenerator.generateLegalMoves(after);

            if (previousMoverHasNoPieces || legalMoves.isEmpty()) {
                sb.append('#');
            }
        } else if (after.isThreeCheck()) {
            // Three-Check: '+' for standard checks, '+$' if 3 checks are delivered or checkmate occurs
            if (ThreeCheck.isKingInCheck(after, after.isWhiteToMove())) {
                List<Move> legal = MoveGenerator.generateLegalMoves(after);

                // Fetch check count for the side that made the move
                int checksGiven = before.isWhiteToMove() ? whiteChecksGiven : blackChecksGiven;

                // This move delivers a check, so the resulting count is (checksGiven + 1)
                boolean is3rdCheck = (checksGiven + 1 >= 3);

                if (legal.isEmpty() || is3rdCheck) {
                    sb.append("+#");
                } else {
                    sb.append('+');
                }
            }
        } else if (isKingInCheck(after, after.isWhiteToMove())) {
            // Standard Chess Check / Checkmate
            List<Move> legal = MoveGenerator.generateLegalMoves(after);
            if (legal.isEmpty()) {
                sb.append('#');
            } else {
                sb.append('+');
            }
        }

        return sb.toString();
    }
    private void recordMove(Board before, Board after, Move move) {
        selectNode(findOrCreateChild(currentNode, before, after, move));
    }

    /**
     * Returns the child of {@code parent} that plays {@code move}, creating it when the move is
     * new. Does not change the selected node.
     */
    private MoveNode findOrCreateChild(MoveNode parent, Board before, Board after, Move move) {
        String notation = createNotation(before, after, move,
                before.getWhiteChecks(), before.getBlackChecks());

        for (MoveNode child : parent.children) {
            // compare the real move too, so two different pieces never get merged into one branch
            if (child.notation.equals(notation) && child.move != null && child.move.equals(move)) {
                return child;
            }
        }

        MoveNode newNode = new MoveNode(after.copy(), move, notation, parent.ply + 1);
        newNode.parent = parent;
        parent.children.add(newNode);
        return newNode;
    }

    private void selectNode(MoveNode target) {
        currentNode = target;
        boardPanel.setBoard(currentNode.board.copy());
        syncCastlingRightsUI();
        recalculateMaterialScore();
        updateStatus();
        updateNotationDisplay();
        updateNavigationButtons();
        refreshAnalysis();
    }

    private void goBack() {
        if (currentNode.parent != null) {
            selectNode(currentNode.parent);
        }
    }

    private void goForward() {
        if (!currentNode.children.isEmpty()) {
            selectNode(currentNode.children.get(0));
        }
    }

    private void updateNavigationButtons() {
        backButton.setEnabled(currentNode != null && currentNode.parent != null);
        forwardButton.setEnabled(currentNode != null && !currentNode.children.isEmpty());
    }

    private void updateStatus() {
        updateCheckBoxes();

        if (isEditorMode) {
            statusLabel.setText("Board Editor Active");
            return;
        }

        Board board = currentNode.board;
        List<Move> legalMoves = MoveGenerator.generateLegalMoves(board);

        boolean whiteToMove = board.isWhiteToMove();
        boolean inCheck = isKingInCheck(board, whiteToMove);

        boolean gameOver = true;

        if (board.isHorde()) {
            updateHordeStatus(board, whiteToMove, inCheck);
            return;
        }

        if (board.isAntiChess()) {
            updateAntiChessStatus(board, whiteToMove);
            return;
        }

        if (board.isKingOfTheHill()) {
            updateKingOfTheHillStatus(board, whiteToMove, inCheck);
            return;
        }

        if (board.isRacingKings()) {
            updateRacingKingsStatus(board, whiteToMove);
            return;
        }

        if (board.isThreeCheck()) {
            updateThreeCheckStatus(board, whiteToMove, inCheck);
            return;
        }

        if (board.isAtomic()) {
            updateAtomicStatus(board, whiteToMove, inCheck);
            return;
        }

        if (legalMoves.isEmpty()) {
            if (inCheck) {
                statusLabel.setText("Checkmate! " + (whiteToMove ? "Black wins" : "White wins"));
            } else {
                statusLabel.setText("Stalemate! Game drawn.");
            }
        } else if (isInsufficientMaterial(board)) {
            statusLabel.setText("Insufficient mate material,Stalemate!");
        } else if (isFiftyMoveRuleDraw(currentNode)) {
            statusLabel.setText("Stalemate by 50-move rule");
        } else if (isThreefoldRepetition(currentNode)) {
            statusLabel.setText("Stalemate due to threefold repetition");
        } else if (inCheck) {
            gameOver = false;
            statusLabel.setText((whiteToMove ? "White" : "Black") + " is in CHECK");
        } else if (board.isChess960() && board.getChess960Id() >= 0) {
            gameOver = false;
            statusLabel.setText((whiteToMove ? "White" : "Black") + " to play, " + board.getChess960Id() + " position");
        } else {
            gameOver = false;
            statusLabel.setText((whiteToMove ? "White" : "Black") + " to move");
        }

        applyGameOverState(gameOver);
    }

    /**
     * Atomic chess: the game ends when a king is blown off, or on checkmate / stalemate.
     * (The standard insufficient-material draws do not apply to atomic chess.)
     */
    private void updateAtomicStatus(Board board, boolean whiteToMove, boolean inCheck) {
        boolean gameOver = true;

        switch (AtomicChess.getResult(board)) {
            case WHITE_KING_EXPLODED:
                statusLabel.setText("White king blown off! Black wins");
                break;
            case BLACK_KING_EXPLODED:
                statusLabel.setText("Black king blown off! White wins");
                break;
            case WHITE_CHECKMATED:
                statusLabel.setText("Checkmate! Black wins");
                break;
            case BLACK_CHECKMATED:
                statusLabel.setText("Checkmate! White wins");
                break;
            case STALEMATE:
                statusLabel.setText("Stalemate! Game drawn.");
                break;
            case BARE_KINGS:
                statusLabel.setText("Insufficient mate material,Stalemate!");
                break;
            default:
                if (isFiftyMoveRuleDraw(currentNode)) {
                    statusLabel.setText("Stalemate by 50-move rule");
                } else if (isThreefoldRepetition(currentNode)) {
                    statusLabel.setText("Stalemate due to threefold repetition");
                } else if (inCheck) {
                    gameOver = false;
                    statusLabel.setText((whiteToMove ? "White" : "Black") + " is in CHECK");
                } else {
                    gameOver = false;
                    statusLabel.setText((whiteToMove ? "White" : "Black") + " to move");
                }
                break;
        }

        applyGameOverState(gameOver);
    }

    /**
     * Horde chess: White wins by checkmating Black, Black wins by capturing every white piece.
     * (Insufficient-material draws do not apply: White has no king.)
     */
    private void updateHordeStatus(Board board, boolean whiteToMove, boolean inCheck) {
        boolean gameOver = true;

        switch (HordeChess.getResult(board)) {
            case WHITE_WINS:
                statusLabel.setText(HordeChess.WHITE_WINS_STATUS);
                break;
            case BLACK_WINS:
                statusLabel.setText(HordeChess.BLACK_WINS_STATUS);
                break;
            case STALEMATE:
                statusLabel.setText("Stalemate! Game drawn.");
                break;
            default:
                if (isFiftyMoveRuleDraw(currentNode)) {
                    statusLabel.setText("Stalemate by 50-move rule");
                } else if (isThreefoldRepetition(currentNode)) {
                    statusLabel.setText("Stalemate due to threefold repetition");
                } else if (inCheck) {
                    gameOver = false;
                    statusLabel.setText((whiteToMove ? "White" : "Black") + " is in CHECK");
                } else {
                    gameOver = false;
                    statusLabel.setText((whiteToMove ? "White" : "Black") + " to move");
                }
                break;
        }

        applyGameOverState(gameOver);
    }

    /**
     * Racing Kings: first king to the 8th rank wins; if White arrives first and Black follows, it is a draw.
     */
    private void updateRacingKingsStatus(Board board, boolean whiteToMove) {
        boolean gameOver = true;

        switch (RacingKings.getResult(board)) {
            case WHITE_WINS:
                statusLabel.setText("King reached the 8th rank, White wins !");
                break;
            case BLACK_WINS:
                statusLabel.setText("King reached the 8th rank, Black wins !");
                break;
            case DRAW_BOTH_REACHED:
                statusLabel.setText("Both kings reached the 8th rank! Game drawn.");
                break;
            case STALEMATE:
                statusLabel.setText("Stalemate! Game drawn.");
                break;
            default:
                if (isFiftyMoveRuleDraw(currentNode)) {
                    statusLabel.setText("Stalemate by 50-move rule");
                } else if (isThreefoldRepetition(currentNode)) {
                    statusLabel.setText("Stalemate due to threefold repetition");
                } else if (!whiteToMove && RacingKings.kingOnEighth(board, true)) {
                    gameOver = false;
                    statusLabel.setText("White reached the 8th rank - Black must follow to draw");
                } else {
                    gameOver = false;
                    statusLabel.setText((whiteToMove ? "White" : "Black") + " to move");
                }
                break;
        }

        applyGameOverState(gameOver);
    }

    /**
     * King of the Hill: a king on d4/e4/d5/e5 wins, otherwise normal chess rules.
     * (Insufficient-material draws do not apply: a lone king can still walk to the hill.)
     */
    private void updateKingOfTheHillStatus(Board board, boolean whiteToMove, boolean inCheck) {
        boolean gameOver = true;

        switch (KingOfTheHill.getResult(board)) {
            case WHITE_KING_ON_HILL:
                statusLabel.setText(KingOfTheHill.winMessage(true));
                break;
            case BLACK_KING_ON_HILL:
                statusLabel.setText(KingOfTheHill.winMessage(false));
                break;
            case WHITE_CHECKMATED:
                statusLabel.setText("Checkmate! Black wins");
                break;
            case BLACK_CHECKMATED:
                statusLabel.setText("Checkmate! White wins");
                break;
            case STALEMATE:
                statusLabel.setText("Stalemate! Game drawn.");
                break;
            default:
                if (isFiftyMoveRuleDraw(currentNode)) {
                    statusLabel.setText("Stalemate by 50-move rule");
                } else if (isThreefoldRepetition(currentNode)) {
                    statusLabel.setText("Stalemate due to threefold repetition");
                } else if (inCheck) {
                    gameOver = false;
                    statusLabel.setText((whiteToMove ? "White" : "Black") + " is in CHECK");
                } else {
                    gameOver = false;
                    statusLabel.setText((whiteToMove ? "White" : "Black") + " to move");
                }
                break;
        }

        applyGameOverState(gameOver);
    }

    /**
     * Anti-Chess: whoever loses all their pieces (or is stalemated) WINS.
     */
    private void updateAntiChessStatus(Board board, boolean whiteToMove) {
        boolean gameOver = true;

        switch (AntiChess.getResult(board)) {
            case WHITE_NO_PIECES:
                statusLabel.setText("White has no pieces left! White wins");
                break;
            case BLACK_NO_PIECES:
                statusLabel.setText("Black has no pieces left! Black wins");
                break;
            case WHITE_STALEMATED:
                statusLabel.setText("White has no moves! White wins");
                break;
            case BLACK_STALEMATED:
                statusLabel.setText("Black has no moves! Black wins");
                break;
            default:
                if (isFiftyMoveRuleDraw(currentNode)) {
                    statusLabel.setText("Stalemate by 50-move rule");
                } else if (isThreefoldRepetition(currentNode)) {
                    statusLabel.setText("Stalemate due to threefold repetition");
                } else {
                    gameOver = false;
                    statusLabel.setText((whiteToMove ? "White" : "Black") + " to move (captures are forced)");
                }
                break;
        }

        applyGameOverState(gameOver);
    }

    /**
     * Three-Check: the first side to give three checks wins (checkmate also wins).
     */
    private void updateThreeCheckStatus(Board board, boolean whiteToMove, boolean inCheck) {
        boolean gameOver = true;
        String checks = ""; // the check count is shown in the boxes beside the board

        switch (ThreeCheck.getResult(board, board.getWhiteChecks(), board.getBlackChecks())) {
            case WHITE_GAVE_THREE_CHECKS:
                statusLabel.setText("Three checks! White wins");
                break;
            case BLACK_GAVE_THREE_CHECKS:
                statusLabel.setText("Three checks! Black wins");
                break;
            case WHITE_CHECKMATED:
                statusLabel.setText("Checkmate! Black wins");
                break;
            case BLACK_CHECKMATED:
                statusLabel.setText("Checkmate! White wins");
                break;
            case STALEMATE:
                statusLabel.setText("Stalemate! Game drawn.");
                break;
            default:
                if (isFiftyMoveRuleDraw(currentNode)) {
                    statusLabel.setText("Stalemate by 50-move rule");
                } else if (isThreefoldRepetition(currentNode)) {
                    statusLabel.setText("Stalemate due to threefold repetition");
                } else if (inCheck) {
                    gameOver = false;
                    statusLabel.setText((whiteToMove ? "White" : "Black") + " is in CHECK" + checks);
                } else {
                    gameOver = false;
                    statusLabel.setText((whiteToMove ? "White" : "Black") + " to move" + checks);
                }
                break;
        }

        applyGameOverState(gameOver);
    }

    /**
     * Once the game is over (checkmate or any kind of stalemate) neither the user
     * nor the engine may move. Going back one move re-runs updateStatus(), which
     * enables everything again if the earlier position is not game over.
     */
    private void applyGameOverState(boolean gameOver) {
        gameIsOver = gameOver;

        engineButton.setEnabled(!gameOver);

        if (boardPanel.isMovesEnabled() == gameOver) {
            boardPanel.setMovesEnabled(!gameOver);
        }
    }

    /*
     Insufficient mating material (a draw, whoever is to move):
      - king against king
      - king against king + knight, or king against king + bishop (either colour)
      - king + bishop against king + knight (one side has a bishop, the other a knight)
      - only bishops left, and all of them (of both colours) stand on squares of the
        same colour, e.g. king + light-squared bishop against king + light-squared bishop
      Any pawn, rook or queen on the board means there is still mating material.
     */
    private static boolean isInsufficientMaterial(Board board) {
        int whiteMinors = 0;
        int blackMinors = 0;
        char whiteMinor = '\0';
        char blackMinor = '\0';

        int knights = 0;
        int bishops = 0;
        int lightBishops = 0;
        int darkBishops = 0;

        for (int sq = 0; sq < 64; sq++) {
            char piece = board.getPiece(sq);

            switch (piece) {
                case 'P': case 'R': case 'Q':
                case 'p': case 'r': case 'q':
                    return false;
                case 'N': case 'B':
                    whiteMinors++;
                    whiteMinor = piece;
                    break;
                case 'n': case 'b':
                    blackMinors++;
                    blackMinor = Character.toUpperCase(piece);
                    break;
                default:
                    break;
            }

            if (piece == 'N' || piece == 'n') {
                knights++;
            } else if (piece == 'B' || piece == 'b') {
                bishops++;
                // a1 (square 0) is a dark square: file + rank even = dark
                if (((sq % 8) + (sq / 8)) % 2 == 0) {
                    darkBishops++;
                } else {
                    lightBishops++;
                }
            }
        }

        int totalMinors = whiteMinors + blackMinors;

        // K v K, K+N v K, K+B v K
        if (totalMinors <= 1) {
            return true;
        }

        // One side has a bishop and the other side has a knight
        if (whiteMinors == 1 && blackMinors == 1 && whiteMinor != blackMinor) {
            return true;
        }

        // Only bishops, all on the same square colour (no mate is possible)
        if (knights == 0 && bishops > 0 && (lightBishops == 0 || darkBishops == 0)) {
            return true;
        }

        return false;
    }

    // Draw rules (50-move rule, threefold repetition)

    /*
      A move is irreversible if it is a pawn move or a capture. After one,
      no earlier position can ever occur again.
     */
    private boolean isIrreversibleMove(MoveNode node) {
        if (node == null || node.parent == null || node.move == null) {
            return false;
        }

        Board before = node.parent.board;
        char moved = before.getPiece(node.move.from);

        boolean pawnMove = Character.toLowerCase(moved) == 'p';
        boolean capture = before.getPiece(node.move.to) != '.' || node.move.enPassant;

        return pawnMove || capture;
    }

    /*
     50-move rule: 50 moves by each side (100 plies) with no pawn move and
     no capture. The count starts from the start of the move list.
     */
    private boolean isFiftyMoveRuleDraw(MoveNode node) {
        int plies = 0;

        while (node != null && node.parent != null) {
            if (isIrreversibleMove(node)) {
                break;
            }
            plies++;
            node = node.parent;
        }

        return plies >= 100;
    }

    /*
      Threefold repetition: the current position (same pieces, same side to
     move, same castling rights, same en passant possibilities) has occurred
     at least three times in the line leading here, consecutively or not.
     */
    private boolean isThreefoldRepetition(MoveNode current) {
        String key = positionKey(current.board);
        int occurrences = 0;

        MoveNode node = current;
        while (node != null) {
            if (positionKey(node.board).equals(key)) {
                occurrences++;
                if (occurrences >= 3) {
                    return true;
                }
            }

            // Positions before a pawn move or capture can never repeat
            if (isIrreversibleMove(node)) {
                break;
            }
            node = node.parent;
        }

        return false;
    }

    private String positionKey(Board board) {
        StringBuilder sb = new StringBuilder(80);

        for (int sq = 0; sq < 64; sq++) {
            sb.append(board.getPiece(sq));
        }

        sb.append(board.isWhiteToMove() ? 'w' : 'b');
        sb.append(board.canWhiteCastleKingSide() ? 'K' : '-');
        sb.append(board.canWhiteCastleQueenSide() ? 'Q' : '-');
        sb.append(board.canBlackCastleKingSide() ? 'k' : '-');
        sb.append(board.canBlackCastleQueenSide() ? 'q' : '-');

        // The en passant square only matters if an en passant capture is really possible
        boolean enPassantPossible = false;
        if (board.getEnPassantSquare() != -1) {
            for (Move m : MoveGenerator.generateLegalMoves(board)) {
                if (m.enPassant) {
                    enPassantPossible = true;
                    break;
                }
            }
        }
        sb.append(enPassantPossible ? board.getEnPassantSquare() : -1);

        return sb.toString();
    }

    private void recalculateMaterialScore() {
        Board board = isEditorMode ? boardPanel.getBoard() : currentNode.board;
        int whiteVal = 0;
        int blackVal = 0;

        for (int sq = 0; sq < 64; sq++) {
            char p = board.getPiece(sq);
            int val = getPieceValue(p);
            if (Character.isUpperCase(p)) {
                whiteVal += val;
            } else if (Character.isLowerCase(p)) {
                blackVal += val;
            }
        }

        int diff = whiteVal - blackVal;
        if (diff > 0) {
            whiteMaterialLabel.setText("White: +" + diff);
            blackMaterialLabel.setText("Black: +0");
        } else if (diff < 0) {
            whiteMaterialLabel.setText("White: +0");
            blackMaterialLabel.setText("Black: +" + Math.abs(diff));
        } else {
            whiteMaterialLabel.setText("White: +0");
            blackMaterialLabel.setText("Black: +0");
        }
    }

    private int getPieceValue(char p) {
        return switch (Character.toUpperCase(p)) {
            case 'P' -> 1;
            case 'N', 'B' -> 3;
            case 'R' -> 5;
            case 'Q' -> 9;
            default -> 0;
        };
    }

    // --- HTML Notation & Variation Rendering ---
    private void updateNotationDisplay() {
        nodeMap.clear();
        nodeCounter = 0;

        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>")
            .append("body { font-family: sans-serif; font-size: 13px; margin: 0; padding: 0; } ")
            .append("a { text-decoration: none; color: #2b5797; font-weight: bold; } ")
            .append(".current { background-color: #ffe169; border-radius: 3px; padding: 1px 3px; } ")
            .append(".var { color: #555555; font-style: italic; } ")
            .append("</style></head><body>");

        renderMoveTreeHTML(rootNode, html, true);

        html.append("</body></html>");
        notationPane.setText(html.toString());
        notationPane.setCaretPosition(0);
    }

    private void renderMoveTreeHTML(MoveNode node, StringBuilder html, boolean isMainLine) {
        if (node == null || node.children.isEmpty()) return;

        MoveNode mainChild = node.children.get(0);
        int moveNum = (node.ply / 2) + 1;
        boolean isWhite = (node.ply % 2 == 0);

        String id = "node_" + (++nodeCounter);
        nodeMap.put(id, mainChild);

        if (isWhite) {
            html.append("<b>").append(moveNum).append(".</b> ");
        } else if (isMainLine && node == rootNode) {
            html.append("<b>").append(moveNum).append("...</b> ");
        }

        boolean isCurrent = (mainChild == currentNode);
        if (isCurrent) html.append("<span class='current'>");
        html.append("<a href='").append(id).append("'>").append(mainChild.notation).append("</a>");
        if (isCurrent) html.append("</span>");
        html.append(" ");

        // Alternatives / Variations
        if (node.children.size() > 1) {
            for (int i = 1; i < node.children.size(); i++) {
                html.append("<span class='var'>(");
                MoveNode varChild = node.children.get(i);
                String varId = "node_" + (++nodeCounter);
                nodeMap.put(varId, varChild);

                if (isWhite) {
                    html.append(moveNum).append(". ");
                } else {
                    html.append(moveNum).append("... ");
                }

                boolean isVarCurrent = (varChild == currentNode);
                if (isVarCurrent) html.append("<span class='current'>");
                html.append("<a href='").append(varId).append("'>").append(varChild.notation).append("</a>");
                if (isVarCurrent) html.append("</span>");
                html.append(" ");

                renderMoveTreeHTML(varChild, html, false);
                html.append(")</span> ");
            }
        }

        // Continue Main Subtree
        renderMoveTreeHTML(mainChild, html, isMainLine);
    }

    // --- Context Menu Operations ---
    private void showContextMenuAtPoint(Point point) {
        int pos = notationPane.viewToModel2D(point);
        targetContextMenuNode = null;

        if (pos >= 0) {
            javax.swing.text.StyledDocument doc = notationPane.getStyledDocument();
            javax.swing.text.Element elem = doc.getCharacterElement(pos);
            javax.swing.text.AttributeSet attrs = elem.getAttributes();
            Object anchor = attrs.getAttribute(javax.swing.text.html.HTML.Tag.A);

            if (anchor instanceof javax.swing.text.AttributeSet) {
                Object href = ((javax.swing.text.AttributeSet) anchor).getAttribute(javax.swing.text.html.HTML.Attribute.HREF);
                if (href != null) {
                    targetContextMenuNode = nodeMap.get(href.toString());
                }
            }
        }

        if (targetContextMenuNode == null) {
            targetContextMenuNode = currentNode;
        }

        JPopupMenu menu = new JPopupMenu();

        JMenuItem deleteItem = new JMenuItem("Delete from here");
        deleteItem.addActionListener(e -> deleteFromTargetPosition());

        JMenuItem promoteItem = new JMenuItem("Promote variation to main line");
        promoteItem.setEnabled(isNodeInVariation(targetContextMenuNode));
        promoteItem.addActionListener(e -> promoteTargetVariation());

        menu.add(deleteItem);
        menu.add(promoteItem);
        menu.show(notationPane, point.x, point.y);
    }

    private boolean isNodeInVariation(MoveNode node) {
        if (node == null || node == rootNode || node.parent == null) return false;
        return node.parent.children.indexOf(node) > 0;
    }

    private void deleteFromTargetPosition() {
        if (targetContextMenuNode == null || targetContextMenuNode.parent == null) return;

        // Check if attempting to delete from Move 1 of the main line
        boolean isDeletingFromMove1MainLine = (targetContextMenuNode.parent == rootNode) 
                && (rootNode.children.indexOf(targetContextMenuNode) == 0);

        int totalMoves = countMoves(rootNode);

        // Show confirmation dialog ONLY when deleting from Move 1 of the main line (and tree has >= 10 moves)
        if (isDeletingFromMove1MainLine && totalMoves >= 10) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Delete " + totalMoves + " moves?",
                    "Delete Moves",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (choice != JOptionPane.YES_OPTION) {
                return;
            }

            // Wipe entire tree from move 1
            rootNode.children.clear();
            targetContextMenuNode = null;
            selectNode(rootNode);
            return;
        }

        // For any other move or variation (or if total moves < 10), delete from target position
        MoveNode parent = targetContextMenuNode.parent;
        parent.children.remove(targetContextMenuNode);

        // If current active node was inside the deleted subtree, fall back to parent
        if (isSubtreeOf(currentNode, targetContextMenuNode)) {
            selectNode(parent);
        } else {
            updateNotationDisplay();
            updateNavigationButtons();
        }
    }

    /*
     Counts every move in the tree (main line and variations), excluding the root.
     */
    private int countMoves(MoveNode node) {
        int count = 0;
        for (MoveNode child : node.children) {
            count += 1 + countMoves(child);
        }
        return count;
    }

    private void promoteTargetVariation() {
        if (!isNodeInVariation(targetContextMenuNode)) return;

        MoveNode parent = targetContextMenuNode.parent;
        int idx = parent.children.indexOf(targetContextMenuNode);
        if (idx > 0) {
            // Shift variation node to first child index (main line)
            parent.children.remove(idx);
            parent.children.add(0, targetContextMenuNode);
            updateNotationDisplay();
        }
    }

    private boolean isSubtreeOf(MoveNode node, MoveNode root) {
        MoveNode curr = node;
        while (curr != null) {
            if (curr == root) return true;
            curr = curr.parent;
        }
        return false;
    }

    // Move Node class for tree representation
    public static class MoveNode {
        public Board board;
        public Move move;
        public String notation;
        public int ply;
        public MoveNode parent;
        public List<MoveNode> children = new ArrayList<>();

        public MoveNode(Board board, Move move, String notation, int ply) {
            this.board = board;
            this.move = move;
            this.notation = notation;
            this.ply = ply;
        }
    }
}