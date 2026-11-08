package com.chess.engine.ui;

import javax.swing.*;
import java.awt.*;

/* Holds one component and keeps it square and centred (used for the boards). */
@SuppressWarnings("serial")
final class SquareHolder extends JPanel {

    SquareHolder(Component child) {
        super(null);
        setOpaque(false);
        add(child);
        setMinimumSize(new Dimension(320, 320));
        setPreferredSize(new Dimension(640, 640));
    }

    @Override
    public void doLayout() {
        if (getComponentCount() == 0) return;
        Insets in = getInsets();
        int w = getWidth() - in.left - in.right;
        int h = getHeight() - in.top - in.bottom;
        int side = Math.max(0, Math.min(w, h));
        side -= side % 8;
        Component c = getComponent(0);
        c.setBounds(in.left + (w - side) / 2, in.top + (h - side) / 2, side, side);
    }
}