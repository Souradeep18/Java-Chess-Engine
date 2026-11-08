package com.chess.engine.ui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Read-only, numbered two-column move list (dark style) used by the Play and Puzzle screens. */
@SuppressWarnings("serial")
final class MoveListView extends JScrollPane {

    private final JEditorPane pane = new JEditorPane();
    private final List<String> sans = new ArrayList<>();
    private boolean startWhite = true;
    private int startNumber = 1;

    MoveListView() {
        super(null, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setBackground(UiKit.PANEL);
        pane.setFocusable(false);
        setViewportView(pane);
        setBorder(BorderFactory.createLineBorder(UiKit.LINE));
        getViewport().setBackground(UiKit.PANEL);
        getVerticalScrollBar().setUnitIncrement(18);
        render();
    }

    void reset(boolean whiteToMove, int fullMoveNumber) {
        sans.clear();
        startWhite = whiteToMove;
        startNumber = Math.max(1, fullMoveNumber);
        render();
    }

    void add(String san) {
        sans.add(san);
        render();
    }

    void removeLast() {
        if (!sans.isEmpty()) {
            sans.remove(sans.size() - 1);
            render();
        }
    }

    int count() { return sans.size(); }

    private void render() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:SansSerif;font-size:13px;color:#bababa;margin:4px;'>");
        sb.append("<table width='100%' cellspacing='0' cellpadding='3'>");
        int i = 0;
        int number = startNumber;
        boolean white = startWhite;
        List<String> row = new ArrayList<>();
        // a black-first list starts with an empty white cell
        if (!white) {
            row.add("&hellip;");
        }
        for (; i < sans.size(); i++) {
            boolean last = i == sans.size() - 1;
            String s = escape(sans.get(i));
            row.add(last ? "<b style='color:#ffffff;background:#3692e7;'>&nbsp;" + s + "&nbsp;</b>" : s);
            if (row.size() == 2) {
                sb.append(rowHtml(number, row));
                number++;
                row.clear();
            }
        }
        if (!row.isEmpty()) sb.append(rowHtml(number, row));
        sb.append("</table></body></html>");
        pane.setText(sb.toString());
        SwingUtilities.invokeLater(() -> pane.setCaretPosition(pane.getDocument().getLength()));
    }

    private static String rowHtml(int number, List<String> cells) {
        StringBuilder r = new StringBuilder("<tr><td width='28' style='color:#7a7774;background:#302e2c;'>")
                .append(number).append("</td>");
        for (int c = 0; c < 2; c++) {
            r.append("<td width='46%'>").append(c < cells.size() ? cells.get(c) : "").append("</td>");
        }
        return r.append("</tr>").toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public Dimension getMinimumSize() { return new Dimension(120, 80); }
}