package com.chess.engine.ui;

import com.chess.engine.analysis.PgnGame;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

/**
 * The "Load/Analyze PGN" popup: a text area for the pasted game and the buttons "Load" and "Analysis".
 * The text is parsed here, so a game that cannot be read is reported inside the popup and nothing is closed.
 */
@SuppressWarnings("serial")
public class PgnDialog extends JDialog {

    private final JTextArea text = new JTextArea(16, 56);
    private final JLabel message = new JLabel(" ");

    /**
     * @param onLoad     receives the parsed game when the user presses "Load"
     * @param onAnalysis receives the parsed game when the user presses "Analysis"
     */
    public PgnDialog(Frame owner, Consumer<PgnGame> onLoad, Consumer<PgnGame> onAnalysis) {
        super(owner, "Load/Analyze PGN", true);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(new EmptyBorder(14, 14, 12, 14));

        JLabel hint = new JLabel("Paste the game moves in PGN format:");
        hint.setFont(hint.getFont().deriveFont(Font.BOLD, 13f));
        content.add(hint, BorderLayout.NORTH);

        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setMargin(new Insets(6, 8, 6, 8));
        JScrollPane scroll = new JScrollPane(text);
        content.add(scroll, BorderLayout.CENTER);

        message.setForeground(new Color(190, 40, 40));
        message.setBorder(new EmptyBorder(2, 2, 2, 2));

        JButton load = new JButton("Load");
        JButton analysis = new JButton("Analysis");
        JButton cancel = new JButton("Cancel");
        load.setToolTipText("Put the moves of the game in the MOVES list (the position on the board stays as it is)");
        analysis.setToolTipText("Load the game and review it with the engine");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancel);
        buttons.add(load);
        buttons.add(analysis);

        JPanel south = new JPanel(new BorderLayout());
        south.add(message, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.EAST);
        content.add(south, BorderLayout.SOUTH);

        setContentPane(content);

        load.addActionListener(e -> submit(onLoad));
        analysis.addActionListener(e -> submit(onAnalysis));
        cancel.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(load);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        pack();
        setMinimumSize(new Dimension(520, 360));
        setLocationRelativeTo(owner);
    }

    private void submit(Consumer<PgnGame> action) {
        PgnGame game;
        try {
            game = PgnGame.parse(text.getText());
        } catch (IllegalArgumentException ex) {
            message.setText("<html>" + escape(ex.getMessage()) + "</html>");
            return;
        } catch (RuntimeException ex) {
            message.setText("The text could not be read as a PGN game.");
            return;
        }

        dispose();
        action.accept(game);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}