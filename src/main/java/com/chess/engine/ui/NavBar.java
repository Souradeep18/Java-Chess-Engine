package com.chess.engine.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/*
 The top navigation bar: PLAY, PUZZLES, LEARN and TOOLS.
 PLAY opens the "play against the computer" dialog; the other three open drop-down menus.
 The knight's-tour and settings buttons that used to sit beside the board now live under TOOLS.
 */
@SuppressWarnings("serial")
public class NavBar extends JPanel {

    /** What the navbar can ask the main window to do. */
    public interface Listener {
        void onPlay();
        void onPuzzles();
        void onPuzzleRush();
        void onChessPrinciples();
        void onCoordinates();
        void onAnalysisBoard();
        void onKnightsTour();
        void onThemes();
    }

    private final Listener listener;
    private final List<NavButton> buttons = new ArrayList<>();
    private JPopupMenu openMenu;
    private NavButton openOwner;
    private NavButton active;

    public NavBar(Listener listener) {
        super(new FlowLayout(FlowLayout.LEFT, 4, 0));
        this.listener = listener;
        setBackground(UiKit.NAV_BG);
        setBorder(new EmptyBorder(0, 14, 0, 14));
        setPreferredSize(new Dimension(100, 46));

        NavButton play = addButton("PLAY", null);
        play.setAction(() -> listener.onPlay());

        NavButton puzzles = addButton("PUZZLES", () -> {
            JPopupMenu m = newMenu();
            m.add(item("Puzzles", "Rated puzzles by theme - Easy, Normal or Hard", null, listener::onPuzzles));
            m.add(item("Puzzle Rush", "3 minutes, 5 minutes or unlimited", null, listener::onPuzzleRush));
            return m;
        });

        NavButton learn = addButton("LEARN", () -> {
            JPopupMenu m = newMenu();
            m.add(item("Chess Principles", "Pieces, openings, strategy, middlegame and endgame ideas", null, listener::onChessPrinciples));
            m.add(item("Coordinates", "Learn the names of the squares", null, listener::onCoordinates));
            return m;
        });

        NavButton tools = addButton("TOOLS", () -> {
            JPopupMenu m = newMenu();
            m.add(item("Analysis Board", "Free board, engine analysis, PGN and FEN", null, listener::onAnalysisBoard));
            m.add(item("Knight's Tour", "The knight's tour puzzle", new KnightIcon(), listener::onKnightsTour));
            m.add(item("Settings", "Themes: board colours and piece sets", new GearIcon(20, UiKit.TEXT), listener::onThemes));
            return m;
        });

        add(play);
        add(puzzles);
        add(learn);
        add(tools);
    }

    /** Highlights the section that is on screen (PLAY, PUZZLES, LEARN, TOOLS or none). */
    public void setActiveSection(String name) {
        active = null;
        for (NavButton b : buttons) {
            boolean on = b.getText().equalsIgnoreCase(name);
            b.setActiveLook(on);
            if (on) active = b;
        }
    }

    private NavButton addButton(String text, MenuFactory factory) {
        NavButton b = new NavButton(text, factory);
        buttons.add(b);
        return b;
    }

    private interface MenuFactory { JPopupMenu create(); }

    private JPopupMenu newMenu() {
        JPopupMenu m = new JPopupMenu();
        m.setBackground(UiKit.NAV_BG);
        m.setBorder(BorderFactory.createLineBorder(UiKit.LINE));
        m.setLightWeightPopupEnabled(false);
        return m;
    }

    private JMenuItem item(String title, String subtitle, Icon icon, Runnable action) {
        DarkItem it = new DarkItem(title, subtitle, icon);
        it.addActionListener(e -> {
            closeMenu();
            action.run();
        });
        return it;
    }

    private void closeMenu() {
        if (openMenu != null) {
            openMenu.setVisible(false);
            openMenu = null;
        }
        if (openOwner != null) {
            openOwner.setOpenLook(false);
            openOwner = null;
        }
    }

    private void showMenu(NavButton owner) {
        closeMenu();
        if (owner.factory == null) return;
        JPopupMenu menu = owner.factory.create();
        openMenu = menu;
        openOwner = owner;
        owner.setOpenLook(true);
        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) { }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                owner.setOpenLook(false);
                if (openMenu == menu) {
                    openMenu = null;
                    openOwner = null;
                }
            }
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) { }
        });
        menu.show(owner, 0, owner.getHeight());
    }

    /** One word in the bar. */
    private final class NavButton extends JComponent {
        private final String text;
        private final MenuFactory factory;
        private Runnable action;
        private boolean hover;
        private boolean open;
        private boolean activeLook;

        NavButton(String text, MenuFactory factory) {
            this.text = text;
            this.factory = factory;
            setFont(UiKit.font(Font.BOLD, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            FontMetrics fm = getFontMetrics(getFont());
            setPreferredSize(new Dimension(fm.stringWidth(text) + 36, 46));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                    if (openMenu != null && openOwner != NavButton.this && factory != null) showMenu(NavButton.this);
                }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) {
                    if (NavButton.this.factory != null) {
                        if (openOwner == NavButton.this) closeMenu(); else showMenu(NavButton.this);
                    } else {
                        closeMenu();
                        if (NavButton.this.action != null) NavButton.this.action.run();
                    }
                }
            });
        }

        String getText() { return text; }
        void setAction(Runnable r) { this.action = r; }
        void setOpenLook(boolean b) { open = b; repaint(); }
        void setActiveLook(boolean b) { activeLook = b; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (open || hover) {
                g2.setColor(UiKit.PANEL_HI);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            g2.setFont(getFont());
            g2.setColor(open || hover || activeLook ? Color.WHITE : new Color(0xa8, 0xa6, 0xa3));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            if (activeLook) {
                g2.setColor(UiKit.GREEN);
                g2.fillRect(8, getHeight() - 3, getWidth() - 16, 3);
            }
            g2.dispose();
        }
    }

    /** Menu entry with a bright title and a dim one-line description. */
    private static final class DarkItem extends JMenuItem {
        private final String title;
        private final String subtitle;
        private final Icon icon;

        DarkItem(String title, String subtitle, Icon icon) {
            super(title);
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
            setOpaque(false);
            setBorder(new EmptyBorder(0, 0, 0, 0));
            setPreferredSize(new Dimension(380, 56));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(isArmed() ? UiKit.PANEL_HI : UiKit.NAV_BG);
            g2.fillRect(0, 0, getWidth(), getHeight());
            int x = 18;
            if (icon != null) {
                icon.paintIcon(this, g2, x, (getHeight() - icon.getIconHeight()) / 2);
                x += icon.getIconWidth() + 12;
            }
            g2.setFont(UiKit.font(Font.BOLD, 15));
            g2.setColor(isArmed() ? Color.WHITE : UiKit.BRIGHT);
            g2.drawString(title, x, 24);
            g2.setFont(UiKit.font(Font.PLAIN, 12));
            g2.setColor(UiKit.TEXT_DIM);
            g2.drawString(subtitle, x, 42);
            g2.dispose();
        }
    }

    /** A tiny knight glyph for the Tools menu (the old button used an emoji). */
    private static final class KnightIcon implements Icon {
        @Override public int getIconWidth() { return 22; }
        @Override public int getIconHeight() { return 22; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(UiKit.TEXT);
            g2.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 22));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("♞", x, y + fm.getAscent() - 2);
            g2.dispose();
        }
    }
}