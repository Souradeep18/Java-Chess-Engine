package com.chess.engine.ui;

import com.chess.engine.engine.Board;
import com.chess.engine.engine.Chess960;
import com.chess.engine.engine.HordeChess;
import com.chess.engine.engine.RacingKings;

import java.util.Random;

// The chess variants that can be played against the computer.
enum PlayVariant {
    STANDARD("Standard", "Standard rules of chess (FIDE)", "♛"),
    CHESS960("Chess960", "Shuffled back rank: one of 960 starting positions", "♞"),
    ATOMIC("Atomic", "Captures explode the surrounding pieces; blow up the enemy king", "☢"),
    HORDE("Horde", "White has 36 pawns and no king; Black must capture them all", "♟"),
    KING_OF_THE_HILL("King of the Hill", "Move your king to the centre (d4, e4, d5, e5) to win", "▲"),
    ANTICHESS("Anti-chess", "Captures are forced; lose all your pieces to win", "⇄"),
    RACING_KINGS("Racing Kings", "No checks allowed; first king to the eighth rank wins", "⚑"),
    THREE_CHECK("Three-check", "Give three checks to win", "✔");

    final String label;
    final String description;
    final String glyph;

    PlayVariant(String label, String description, String glyph) {
        this.label = label;
        this.description = description;
        this.glyph = glyph;
    }

    boolean standardLike() {
        return this == STANDARD || this == CHESS960 || this == THREE_CHECK;
    }

    // A fresh starting position for this variant (the same set-up calls the analysis board uses).
    Board newBoard(Random rnd) {
        Board board = new Board();
        switch (this) {
            case CHESS960: Chess960.setupStartingPosition(board, rnd.nextInt(960)); break;
            case ATOMIC: board.setAtomic(true); break;
            case HORDE: HordeChess.setupStartingPosition(board); break;
            case KING_OF_THE_HILL: board.setKingOfTheHill(true); break;
            case ANTICHESS: board.setAntiChess(true); break;
            case RACING_KINGS: RacingKings.setupStartingPosition(board); break;
            case THREE_CHECK: board.setThreeCheck(true); break;
            default: break;
        }
        return board;
    }
}