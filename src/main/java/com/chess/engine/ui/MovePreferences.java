package com.chess.engine.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/*
  Move preferences: Auto-Queen, premoves and how pieces are moved (click, drag and drop, or both).
 
  This one class holds the settings (saved in ~/.javachess/profile.properties), the small "Move Preferences"
  window (TOOLS > Settings > Move Preferences, with Save and Cancel) and the questions the rest of the program
  asks ("may pieces be dragged?", "is Auto-Queen on?", ...). {@link ChessBoardPanel} reads them on every mouse
  event, so a saved change is effective at once on every board; {@link PlayPanel} uses the premove settings.
 */
public final class MovePreferences {

    /* Premoves: moves made while it is the opponent's turn. */
    public enum PremoveMode {
        DISABLED("Disabled", "No premoves."),
        SINGLE("Single premove", "One premove at a time. It costs no time on the clock when playing against the computer."),
        MULTIPLE("Multiple premoves", "Queue several premoves in a row. Each one uses 0.1 second on your clock.");

        final String label;
        final String description;

        PremoveMode(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    /* How a piece is moved on the board. */
    public enum PieceMovement {
        CLICK("Click to move", "Click a piece, then click the square it should go to."),
        DRAG("Drag and drop", "Drag the piece to its new square."),
        BOTH("Both", "Pieces can be clicked or dragged.");

        final String label;
        final String description;

        PieceMovement(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    private static final String KEY_AUTO_QUEEN = "move.autoQueen";
    private static final String KEY_PREMOVE = "move.premove";
    private static final String KEY_MOVEMENT = "move.movement";

    /** Time one premove costs in "Multiple premoves" mode (milliseconds). */
    public static final long MULTIPLE_PREMOVE_COST_MS = 100;

    private static boolean loaded;
    private static boolean autoQueen = false;                        // default: off
    private static PremoveMode premoveMode = PremoveMode.DISABLED;   // default: disabled
    private static PieceMovement movement = PieceMovement.BOTH;      // default: both
    private static final List<Runnable> LISTENERS = new ArrayList<>();

    private MovePreferences() { }

    //  the settings

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        autoQueen = PuzzleProfile.getInt(KEY_AUTO_QUEEN, 0) != 0;
        premoveMode = pick(PremoveMode.values(), PuzzleProfile.getInt(KEY_PREMOVE, PremoveMode.DISABLED.ordinal()), PremoveMode.DISABLED);
        movement = pick(PieceMovement.values(), PuzzleProfile.getInt(KEY_MOVEMENT, PieceMovement.BOTH.ordinal()), PieceMovement.BOTH);
    }

    private static <T> T pick(T[] values, int index, T fallback) {
        return index >= 0 && index < values.length ? values[index] : fallback;
    }

    /** True when a pawn reaching the last rank always becomes a queen, without asking. */
    public static boolean isAutoQueen() {
        load();
        return autoQueen;
    }

    public static PremoveMode getPremoveMode() {
        load();
        return premoveMode;
    }

    public static boolean premovesEnabled() {
        return getPremoveMode() != PremoveMode.DISABLED;
    }

    public static boolean multiplePremoves() {
        return getPremoveMode() == PremoveMode.MULTIPLE;
    }

    /* Clock time one executed premove uses: 0 for a single premove, 0.1 s each for multiple premoves. */
    public static long premoveCostMs() {
        return multiplePremoves() ? MULTIPLE_PREMOVE_COST_MS : 0;
    }

    public static PieceMovement getPieceMovement() {
        load();
        return movement;
    }

    /* Pieces may be moved with two clicks. */
    public static boolean allowsClick() {
        return getPieceMovement() != PieceMovement.DRAG;
    }

    /* Pieces may be dragged. */
    public static boolean allowsDrag() {
        return getPieceMovement() != PieceMovement.CLICK;
    }

    /* Stores the settings (also in the profile file) and tells the listeners. */
    public static void set(boolean autoQueenOn, PremoveMode premoves, PieceMovement pieceMovement) {
        load();
        List<Runnable> toTell;
        synchronized (MovePreferences.class) {
            autoQueen = autoQueenOn;
            premoveMode = premoves;
            movement = pieceMovement;
            toTell = new ArrayList<>(LISTENERS);
        }
        PuzzleProfile.putInt(KEY_AUTO_QUEEN, autoQueenOn ? 1 : 0);
        PuzzleProfile.putInt(KEY_PREMOVE, premoves.ordinal());
        PuzzleProfile.putInt(KEY_MOVEMENT, pieceMovement.ordinal());
        for (Runnable r : toTell) r.run();
    }

    /** Back to the defaults (Auto-Queen off, premoves disabled, click and drag). */
    public static void resetToDefaults() {
        set(false, PremoveMode.DISABLED, PieceMovement.BOTH);
    }

    public static synchronized void addChangeListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    // the window

    /*
      Opens the Move Preferences window. Nothing changes until "Save" is pressed; "Cancel" (or closing the
      window) throws the changes away. Returns true when the settings were saved.
     */
    public static boolean open(Window owner) {
        PrefsDialog d = new PrefsDialog(owner);
        d.setVisible(true);
        return d.saved;
    }

    @SuppressWarnings("serial")
    private static final class PrefsDialog extends JDialog {
        boolean saved;
        private final UiKit.Toggle autoQueenToggle;
        private final ButtonGroup premoveGroup = new ButtonGroup();
        private final ButtonGroup movementGroup = new ButtonGroup();
        private final JRadioButton[] premoveRadios = new JRadioButton[PremoveMode.values().length];
        private final JRadioButton[] movementRadios = new JRadioButton[PieceMovement.values().length];

        PrefsDialog(Window owner) {
            super(owner, "Move Preferences", ModalityType.APPLICATION_MODAL);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);

            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBackground(UiKit.PANEL);
            content.setBorder(new EmptyBorder(18, 26, 14, 26));

            JLabel title = UiKit.label("Move Preferences", Font.PLAIN, 24, UiKit.BRIGHT);
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(title);
            content.add(Box.createVerticalStrut(14));

            // --- Auto-Queen
            autoQueenToggle = new UiKit.Toggle(isAutoQueen(), null);
            JPanel autoRow = new JPanel(new BorderLayout(14, 0));
            autoRow.setOpaque(false);
            JPanel autoTexts = new JPanel(new GridLayout(0, 1, 0, 2));
            autoTexts.setOpaque(false);
            autoTexts.add(UiKit.label("Auto-Queen", Font.BOLD, 16, UiKit.BRIGHT));
            autoTexts.add(wrapped("Automatically promote pawns to a queen without showing the promotion dialog."));
            autoRow.add(autoTexts, BorderLayout.CENTER);
            JPanel toggleHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
            toggleHolder.setOpaque(false);
            toggleHolder.add(autoQueenToggle);
            autoRow.add(toggleHolder, BorderLayout.EAST);
            autoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(autoRow);
            content.add(separator());

            // --- Premoves
            content.add(heading("Premoves"));
            PremoveMode currentPremove = getPremoveMode();
            for (PremoveMode m : PremoveMode.values()) {
                JRadioButton r = radio(m.label, m.description, m == currentPremove, premoveGroup, content);
                premoveRadios[m.ordinal()] = r;
            }
            content.add(separator());

            // Piece movement
            content.add(heading("Piece movement"));
            PieceMovement currentMovement = getPieceMovement();
            for (PieceMovement m : PieceMovement.values()) {
                JRadioButton r = radio(m.label, m.description, m == currentMovement, movementGroup, content);
                movementRadios[m.ordinal()] = r;
            }

            //  buttons
            UiKit.FlatButton save = new UiKit.FlatButton("Save", UiKit.GREEN);
            UiKit.FlatButton cancel = new UiKit.FlatButton("Cancel", UiKit.PANEL_HI);
            for (UiKit.FlatButton b : new UiKit.FlatButton[]{save, cancel}) {
                b.setFont(UiKit.font(Font.BOLD, 15));
                b.setForeground(UiKit.BRIGHT);
                b.setPreferredSize(new Dimension(120, 40));
            }
            save.addActionListener(e -> {
                PremoveMode p = PremoveMode.DISABLED;
                for (PremoveMode m : PremoveMode.values()) if (premoveRadios[m.ordinal()].isSelected()) p = m;
                PieceMovement pm = PieceMovement.BOTH;
                for (PieceMovement m : PieceMovement.values()) if (movementRadios[m.ordinal()].isSelected()) pm = m;
                set(autoQueenToggle.isOn(), p, pm);
                saved = true;
                dispose();
            });
            cancel.addActionListener(e -> dispose());

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            buttons.setOpaque(false);
            buttons.add(cancel);
            buttons.add(save);
            buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(Box.createVerticalStrut(16));
            content.add(buttons);

            JScrollPane scroll = new JScrollPane(content, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(null);
            scroll.getViewport().setBackground(UiKit.PANEL);
            setContentPane(scroll);
            getRootPane().setDefaultButton(save);
            getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                    JComponent.WHEN_IN_FOCUSED_WINDOW);

            pack();
            Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            setSize(Math.max(getWidth() + 16, 480), Math.min(getHeight(), screen.height - 40));
            setLocationRelativeTo(owner);
        }

        private JRadioButton radio(String label, String description, boolean selected, ButtonGroup group, JPanel into) {
            JRadioButton r = new JRadioButton("<html><b>" + label + "</b><br><span style='font-size:10px;color:#9a9895'>"
                    + description + "</span></html>", selected);
            r.setOpaque(false);
            r.setForeground(UiKit.BRIGHT);
            r.setFont(UiKit.font(Font.PLAIN, 14));
            r.setFocusPainted(false);
            r.setAlignmentX(Component.LEFT_ALIGNMENT);
            r.setBorder(new EmptyBorder(4, 0, 4, 0));
            r.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            r.setIcon(new RadioIcon(false));
            r.setSelectedIcon(new RadioIcon(true));
            group.add(r);
            into.add(r);
            return r;
        }

        private JLabel heading(String text) {
            JLabel l = UiKit.label(text, Font.BOLD, 16, UiKit.BRIGHT);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            l.setBorder(new EmptyBorder(0, 0, 4, 0));
            return l;
        }

        private JLabel wrapped(String text) {
            return UiKit.label("<html><div style='width:290px'>" + text + "</div></html>", Font.PLAIN, 12, UiKit.TEXT_DIM);
        }

        private JComponent separator() {
            JPanel p = new JPanel();
            p.setBackground(UiKit.LINE);
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            p.setPreferredSize(new Dimension(10, 1));
            p.setAlignmentX(Component.LEFT_ALIGNMENT);
            JPanel wrap = new JPanel();
            wrap.setOpaque(false);
            wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
            wrap.setBorder(new EmptyBorder(12, 0, 12, 0));
            wrap.add(p);
            wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
            return wrap;
        }
    }

    // A round radio mark that is clearly visible on the dark background. 
    private static final class RadioIcon implements Icon {
        private final boolean on;

        RadioIcon(boolean on) { this.on = on; }

        @Override public int getIconWidth() { return 22; }

        @Override public int getIconHeight() { return 22; }

        @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(2f));
            g.setColor(on ? UiKit.GREEN : UiKit.TEXT_DIM);
            g.drawOval(x + 3, y + 3, 15, 15);
            if (on) g.fillOval(x + 7, y + 7, 8, 8);
            g.dispose();
        }
    }
}