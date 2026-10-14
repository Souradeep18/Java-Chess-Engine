package com.chess.engine.engine;

import java.util.Collections;
import java.util.List;

/*
  King of the Hill: normal chess, plus an extra way to win - the first king to step onto one of
  the four central squares (d4, e4, d5, e5) wins at once. Checkmate still wins, stalemate still
  draws, and a king in check must still be saved the usual way.
 */
public final class KingOfTheHill {

    public static final String VARIANT_KING_OF_THE_HILL = "variant-king of the hill";

    // d4, e4, d5, e5 (index 0 = a1 ... 63 = h8)
    public static final int[] HILL_SQUARES = {27, 28, 35, 36};

    public enum Result {
        ONGOING,
        WHITE_KING_ON_HILL,   // White wins
        BLACK_KING_ON_HILL,   // Black wins
        WHITE_CHECKMATED,     // Black wins
        BLACK_CHECKMATED,     // White wins
        STALEMATE
    }

    private KingOfTheHill() {
    }

    public static boolean isHillSquare(int square) {
        return square == 27 || square == 28 || square == 35 || square == 36;
    }

    public static boolean hasKingOnHill(Board board, boolean white) {
        char king = white ? 'K' : 'k';
        for (int sq : HILL_SQUARES) {
            if (board.getPiece(sq) == king) {
                return true;
            }
        }
        return false;
    }

    // True once either king stands on the hill (the game is over).
    public static boolean isGameWon(Board board) {
        return hasKingOnHill(board, true) || hasKingOnHill(board, false);
    }

    public static String winMessage(boolean whiteWins) {
        return "King up the hill, " + (whiteWins ? "White" : "Black") + " wins !";
    }

    // Normal legal moves; none once a king has reached the hill.
    public static List<Move> generateLegalMoves(Board board) {
        if (isGameWon(board)) {
            return Collections.emptyList();
        }
        return MoveGenerator.generateLegalMoves(board);
    }

    public static Result getResult(Board board) {
        if (hasKingOnHill(board, true)) return Result.WHITE_KING_ON_HILL;
        if (hasKingOnHill(board, false)) return Result.BLACK_KING_ON_HILL;

        if (!MoveGenerator.generateLegalMoves(board).isEmpty()) {
            return Result.ONGOING;
        }

        boolean whiteToMove = board.isWhiteToMove();
        if (ThreeCheck.isKingInCheck(board, whiteToMove)) {
            return whiteToMove ? Result.WHITE_CHECKMATED : Result.BLACK_CHECKMATED;
        }
        return Result.STALEMATE;
    }

    // Small evaluation bonus (white's point of view, centipawns) for a king close to the hill.
    public static int kingProximityBonus(Board board) {
        return proximity(board, true) - proximity(board, false);
    }

    private static int proximity(Board board, boolean white) {
        char king = white ? 'K' : 'k';
        for (int sq = 0; sq < 64; sq++) {
            if (board.getPiece(sq) == king) {
                int f = sq % 8, r = sq / 8;
                // distance to the nearest hill square (Chebyshev), 0 = on the hill
                int df = Math.max(0, Math.max(3 - f, f - 4));
                int dr = Math.max(0, Math.max(3 - r, r - 4));
                int dist = Math.max(df, dr);
                return (4 - dist) * 25;
            }
        }
        return 0;
    }
}