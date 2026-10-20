package com.chess.engine.ui;

import com.chess.engine.engine.Chess960;

import javax.swing.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.AbstractDocument;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;
import java.util.function.IntConsumer;

/*
  The "Chess960 position" section of the board editor: a box holding a number from 0 to 959 (typed in,
  or stepped with the small up / down arrows that only show while the mouse is over the box) and a
  dice button that picks a random number. Every change is reported to the listener.
 */
@SuppressWarnings("serial")
public class Chess960PositionBox extends JPanel {

    private final JTextField field = new JTextField(3);
    private final ArrowStrip arrows = new ArrowStrip();
    private final JPanel box = new JPanel(new BorderLayout());
    private final Random random = new Random();

    private int value = Chess960.DEFAULT_POSITION;
    private boolean updatingText;
    private IntConsumer listener = v -> { };

    public Chess960PositionBox() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel("Chess960 position");
        title.setFont(new Font("SansSerif", Font.BOLD, 14));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(6));

        // --- the number box
        field.setText(String.valueOf(value));
        field.setHorizontalAlignment(JTextField.CENTER);
        field.setFont(new Font("SansSerif", Font.PLAIN, 20));
        field.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 0));
        field.setOpaque(false);

        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr) throws BadLocationException {
                replace(fb, offset, 0, text, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                String current = fb.getDocument().getText(0, fb.getDocument().getLength());
                String proposed = current.substring(0, offset) + (text == null ? "" : text)
                        + current.substring(offset + length);

                if (!proposed.matches("\\d{0,3}")) {
                    return;// digits only
                }
                if (!proposed.isEmpty() && Integer.parseInt(proposed) > Chess960.MAX_POSITION) {
                    return; // never above 959
                }

                super.replace(fb, offset, length, text, attrs);
                textChanged(proposed);
            }

            @Override
            public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
                super.remove(fb, offset, length);
                textChanged(fb.getDocument().getText(0, fb.getDocument().getLength()));
            }
        });

        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up960");
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down960");
        field.getActionMap().put("up960", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { step(1); }
        });
        field.getActionMap().put("down960", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { step(-1); }
        });
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                showValue();                                 // an emptied box goes back to the last number
            }
        });

        box.setBackground(UIManager.getColor("TextField.background") != null
                ? UIManager.getColor("TextField.background") : Color.WHITE);
        box.setBorder(BorderFactory.createLineBorder(new Color(150, 150, 150)));
        box.add(field, BorderLayout.CENTER);
        box.add(arrows, BorderLayout.EAST);
        box.setPreferredSize(new Dimension(110, 38));

        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                arrows.setShown(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // still inside the box (over the text field or the arrows)? then keep the arrows
                arrows.setShown(box.getMousePosition(true) != null);
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                step(e.getWheelRotation() < 0 ? 1 : -1);
            }
        };
        box.addMouseListener(hover);
        box.addMouseWheelListener(hover);
        field.addMouseListener(hover);
        field.addMouseWheelListener(hover);
        arrows.addMouseListener(hover);
        arrows.addMouseWheelListener(hover);

        
     // Create a custom Icon for the 6-sided die for chess960 random position
        Icon royalBlueDiceIcon = new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Royal Blue die body
                g2.setColor(new Color(65, 105, 225)); // Royal Blue
                g2.fillRoundRect(x, y, 25, 25, 6, 6);

                // the 6 White dots
                g2.setColor(Color.WHITE);
                int dotSize = 4;
                
                // Left column dots
                g2.fillOval(x + 5,  y + 5,  dotSize, dotSize);
                g2.fillOval(x + 5,  y + 11, dotSize, dotSize);
                g2.fillOval(x + 5,  y + 17, dotSize, dotSize);
                
                // Right column dots
                g2.fillOval(x + 17, y + 5,  dotSize, dotSize);
                g2.fillOval(x + 17, y + 11, dotSize, dotSize);
                g2.fillOval(x + 17, y + 17, dotSize, dotSize);

                g2.dispose();
            }

            @Override
            public int getIconWidth() { return 26; }

            @Override
            public int getIconHeight() { return 26; }
        };

        JButton dice = new JButton(royalBlueDiceIcon);
        dice.setFocusPainted(false);
        dice.setContentAreaFilled(false); // Removes the default rectangular button border/background
        dice.setBorderPainted(false);      // Makes the icon look like a clean standalone die
        dice.setMargin(new Insets(0, 4, 0, 4));
        dice.setToolTipText("Random position");
        dice.setPreferredSize(new Dimension(44, 38));
        dice.addActionListener(e -> setValue(random.nextInt(Chess960.MAX_POSITION + 1), true));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(box);
        row.add(Box.createHorizontalStrut(10));
        row.add(dice);
        row.setMaximumSize(new Dimension(320, 42));
        add(row);

    }

    public void setListener(IntConsumer listener) {
        this.listener = listener == null ? v -> { } : listener;
    }

    public int getValue() {
        return value;
    }

    // Sets the number (clamped to 0..959); fire = tell the listener as well.
    public void setValue(int newValue, boolean fire) {
        value = Math.max(Chess960.MIN_POSITION, Math.min(Chess960.MAX_POSITION, newValue));
        showValue();
        if (fire) {
            listener.accept(value);
        }
    }

    private void step(int delta) {
        int next = value + delta;
        if (next < Chess960.MIN_POSITION || next > Chess960.MAX_POSITION) {
            return;
        }
        setValue(next, true);
    }

    private void showValue() {
        updatingText = true;
        try {
            field.setText(String.valueOf(value));
        } finally {
            updatingText = false;
        }
    }

    // called while the user types: every complete valid number is applied at once
    private void textChanged(String text) {
        if (updatingText || text.isEmpty()) {
            return;
        }
        int typed = Integer.parseInt(text);
        if (typed != value) {
            value = typed;
            listener.accept(value);
        }
    }

    // The little up / down arrows at the right edge of the box; drawn only while the mouse is over the box.
    private final class ArrowStrip extends JComponent {
        private boolean shown;

        ArrowStrip() {
            setPreferredSize(new Dimension(20, 36));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (!shown) return;
                    step(e.getY() < getHeight() / 2 ? 1 : -1);
                    field.requestFocusInWindow();
                }
            });
        }

        void setShown(boolean shown) {
            if (this.shown != shown) {
                this.shown = shown;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (!shown) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight(), mid = h / 2;
            g2.setColor(new Color(235, 235, 235));
            g2.fillRect(0, 0, w, h);
            g2.setColor(new Color(190, 190, 190));
            g2.drawLine(0, 0, 0, h);
            g2.drawLine(0, mid, w, mid);

            g2.setColor(new Color(70, 70, 70));
            int cx = w / 2 + 1;
            g2.fillPolygon(new int[]{cx - 4, cx + 4, cx}, new int[]{mid - 5, mid - 5, mid - 11}, 3);
            g2.fillPolygon(new int[]{cx - 4, cx + 4, cx}, new int[]{mid + 5, mid + 5, mid + 11}, 3);
            g2.dispose();
        }
    }
}