package com.chess.engine.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.util.List;

/* "Chess Principles": a course of short lessons, each with interactive mini boards. */
@SuppressWarnings("serial")
public class LearnPanel extends JPanel {

    private static final String KEY_LAST = "learn.last";

    private final List<LessonData.Lesson> lessons = LessonData.all();
    private final JPanel menu = new JPanel();
    private final JLabel sectionLabel = UiKit.label(" ", Font.BOLD, 13, UiKit.TEXT_DIM);
    private final JLabel titleLabel = UiKit.label(" ", Font.BOLD, 28, UiKit.BRIGHT);
    private final JEditorPane text = new JEditorPane();
    private final JPanel boards = new JPanel();
    private final JScrollPane boardScroll;
    private final JScrollPane textScroll;
    private final UiKit.FlatButton prev = new UiKit.FlatButton("◀ Previous", UiKit.PANEL_HI);
    private final UiKit.FlatButton next = new UiKit.FlatButton("Next lesson ▶", UiKit.GREEN);
    private final JLabel counter = UiKit.label(" ", Font.PLAIN, 13, UiKit.TEXT_DIM);
    private final UiKit.FlatButton[] menuButtons;
    private int current = -1;

    public LearnPanel() {
        super(new BorderLayout(16, 0));
        setBackground(UiKit.BG);
        setBorder(new EmptyBorder(18, 22, 18, 22));

        //  left: table of contents
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setOpaque(false);
        menuButtons = new UiKit.FlatButton[lessons.size()];
        String lastSection = null;
        for (int i = 0; i < lessons.size(); i++) {
            LessonData.Lesson ls = lessons.get(i);
            if (!ls.section.equals(lastSection)) {
                lastSection = ls.section;
                JLabel h = UiKit.label(ls.section.toUpperCase(), Font.BOLD, 12, UiKit.TEXT_DIM);
                h.setBorder(new EmptyBorder(12, 6, 4, 0));
                h.setAlignmentX(Component.LEFT_ALIGNMENT);
                menu.add(h);
            }
            final int index = i;
            UiKit.FlatButton b = new UiKit.FlatButton(ls.title, UiKit.PANEL);
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setFont(UiKit.font(Font.PLAIN, 13));
            b.setBorder(new EmptyBorder(7, 8, 7, 4));
            b.setFocusable(false);
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            b.addActionListener(e -> show(index));
            menuButtons[i] = b;
            menu.add(b);
            menu.add(Box.createVerticalStrut(2));
        }
        JPanel menuWrap = UiKit.darkPanel(new BorderLayout());
        menuWrap.setBorder(new EmptyBorder(8, 8, 8, 8));
        menuWrap.add(UiKit.label("Chess Principles", Font.BOLD, 20, UiKit.BRIGHT), BorderLayout.NORTH);
        JScrollPane ms = new JScrollPane(menu);
        styleScroll(ms);
        menuWrap.add(ms, BorderLayout.CENTER);
        menuWrap.setPreferredSize(new Dimension(290, 600));
        add(menuWrap, BorderLayout.WEST);

        // centre: lesson text
        JPanel centre = UiKit.darkPanel(new BorderLayout(0, 8));
        centre.setBorder(new EmptyBorder(18, 24, 14, 24));
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        sectionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(sectionLabel);
        head.add(Box.createVerticalStrut(4));
        head.add(titleLabel);
        head.add(Box.createVerticalStrut(10));
        centre.add(head, BorderLayout.NORTH);

        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet css = new StyleSheet();
        css.addRule("body { font-family: SansSerif; font-size: 15px; color: #d0d0d0; }");
        css.addRule("p { margin-top: 0; margin-bottom: 10px; }");
        css.addRule("li { margin-bottom: 6px; }");
        css.addRule("b { color: #ffffff; }");
        kit.setStyleSheet(css);
        text.setEditorKit(kit);
        text.setContentType("text/html");
        text.setEditable(false);
        text.setOpaque(false);
        text.setBorder(null);
        textScroll = new JScrollPane(text);
        styleScroll(textScroll);
        centre.add(textScroll, BorderLayout.CENTER);

        JPanel nav = new JPanel(new BorderLayout());
        nav.setOpaque(false);
        prev.setBorder(new EmptyBorder(8, 16, 8, 16));
        next.setBorder(new EmptyBorder(8, 16, 8, 16));
        prev.addActionListener(e -> show(current - 1));
        next.addActionListener(e -> show(current + 1));
        counter.setHorizontalAlignment(SwingConstants.CENTER);
        nav.add(prev, BorderLayout.WEST);
        nav.add(counter, BorderLayout.CENTER);
        nav.add(next, BorderLayout.EAST);
        centre.add(nav, BorderLayout.SOUTH);
        add(centre, BorderLayout.CENTER);

        // right: boards
        boards.setLayout(new BoxLayout(boards, BoxLayout.Y_AXIS));
        boards.setOpaque(false);
        JPanel boardsHolder = new JPanel(new BorderLayout());
        boardsHolder.setOpaque(false);
        boardsHolder.add(boards, BorderLayout.NORTH);
        boardScroll = new JScrollPane(boardsHolder);
        styleScroll(boardScroll);
        boardScroll.setPreferredSize(new Dimension(372, 600));
        add(boardScroll, BorderLayout.EAST);

        show(Math.max(0, Math.min(lessons.size() - 1, PuzzleProfile.getInt(KEY_LAST, 0))));
    }

    public void activate() { }

    private static void styleScroll(JScrollPane sp) {
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setUnitIncrement(18);
    }

    private void show(int index) {
        if (index < 0 || index >= lessons.size()) return;
        current = index;
        PuzzleProfile.putInt(KEY_LAST, index);
        LessonData.Lesson ls = lessons.get(index);
        sectionLabel.setText(ls.section.toUpperCase());
        titleLabel.setText(ls.title);
        text.setText("<html><body>" + ls.html + "</body></html>");
        text.setCaretPosition(0);

        boards.removeAll();
        for (LessonData.Diagram d : ls.diagrams) {
            MiniBoard mb = new MiniBoard(310);
            switch (d.mode) {
                case EXPLORE: mb.showExplore(d.caption, d.fen, d.marks); break;
                case LINE: mb.showLine(d.caption, d.fen, d.moves, d.marks); break;
                default: mb.showFixed(d.caption, d.fen, d.marks, d.arrows); break;
            }
            JPanel card = UiKit.darkPanel(new BorderLayout());
            card.setBorder(new EmptyBorder(10, 10, 10, 10));
            card.add(mb, BorderLayout.CENTER);
            card.setAlignmentX(Component.CENTER_ALIGNMENT);
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height + 80));
            boards.add(card);
            boards.add(Box.createVerticalStrut(12));
        }
        boards.revalidate();
        boards.repaint();
        boardScroll.getVerticalScrollBar().setValue(0);

        for (int i = 0; i < menuButtons.length; i++) menuButtons[i].setSelectedLook(i == index);
        prev.setEnabled(index > 0);
        next.setEnabled(index < lessons.size() - 1);
        counter.setText("Lesson " + (index + 1) + " of " + lessons.size());
        SwingUtilities.invokeLater(() -> textScroll.getVerticalScrollBar().setValue(0));
    }
}