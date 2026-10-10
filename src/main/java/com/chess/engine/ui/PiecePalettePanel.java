package com.chess.engine.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

@SuppressWarnings("serial")
public class PiecePalettePanel extends JPanel {

    public PiecePalettePanel() {
        setLayout(new GridLayout(2, 6, 5, 5));
        setBorder(BorderFactory.createTitledBorder("Piece Palette"));

        // White Pieces
        add(createPaletteButton('P', "♙"));
        add(createPaletteButton('N', "♘"));
        add(createPaletteButton('B', "♗"));
        add(createPaletteButton('R', "♖"));
        add(createPaletteButton('Q', "♕"));
        add(createPaletteButton('K', "♔"));

        // Black Pieces
        add(createPaletteButton('p', "♟"));
        add(createPaletteButton('n', "♞"));
        add(createPaletteButton('b', "♝"));
        add(createPaletteButton('r', "♜"));
        add(createPaletteButton('q', "♛"));
        add(createPaletteButton('k', "♚"));
    }

    //Helper method to build draggable palette buttons.
    public JButton createPaletteButton(char pieceChar, String pieceSymbol) {
        JButton pieceButton = new JButton(pieceSymbol);
        pieceButton.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 28));
        pieceButton.setFocusPainted(false);

        pieceButton.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                JComponent comp = (JComponent) e.getSource();
                TransferHandler handler = new TransferHandler("text") {
                    @Override
                    protected Transferable createTransferable(JComponent c) {
                        return new StringSelection(String.valueOf(pieceChar));
                    }

                    @Override
                    public int getSourceActions(JComponent c) {
                        return COPY;
                    }
                };
                comp.setTransferHandler(handler);
                handler.exportAsDrag(comp, e, TransferHandler.COPY);
            }
        });

        return pieceButton;
    }
}