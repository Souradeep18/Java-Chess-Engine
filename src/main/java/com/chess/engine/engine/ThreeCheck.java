package com.chess.engine.engine;

import java.util.ArrayList;
import java.util.List;

public final class ThreeCheck {

    public static final String VARIANT_THREE_CHECK = "variant-three-check";

    public enum Result {
        ONGOING,
        WHITE_GAVE_THREE_CHECKS, // White wins
        BLACK_GAVE_THREE_CHECKS, // Black wins
        WHITE_CHECKMATED,        // Black wins
        BLACK_CHECKMATED,        // White wins
        STALEMATE
    }

    private ThreeCheck() {
    }

    // ------------------------------------------------------------------ check tracking

    /**
     * Checks if the given side is currently in check on the board.
     */
    public static boolean isKingInCheck(Board board, boolean white) {
        int kingSq = findKing(board, white);
        if (kingSq == -1) {
            return false;
        }
        return Board.isSquareAttacked(board, kingSq, !white);
    }

    public static int findKing(Board board, boolean white) {
        char king = white ? 'K' : 'k';
        for (int sq = 0; sq < 64; sq++) {
            if (board.getPiece(sq) == king) {
                return sq;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ move generation & results

    public static List<Move> generateLegalMoves(Board board) {
        List<Move> legal = new ArrayList<>();
        boolean moverWhite = board.isWhiteToMove();

        for (Move move : MoveGenerator.generatePseudoLegalMoves(board)) {
            Board copy = board.copy();
            MoveGenerator.makeMove(copy, move);

            if (!isKingInCheck(copy, moverWhite)) {
                legal.add(move);
            }
        }
        return legal;
    }
    
    public static boolean isThirdCheck(int currentChecksForMover) {
        return currentChecksForMover + 1 >= 3;
    }

    public static Result getResult(Board board, int whiteChecksGiven, int blackChecksGiven) {
        if (!board.isThreeCheck()) {
            return Result.ONGOING;
        }

        if (whiteChecksGiven >= 3) {
            return Result.WHITE_GAVE_THREE_CHECKS;
        }
        if (blackChecksGiven >= 3) {
            return Result.BLACK_GAVE_THREE_CHECKS;
        }

        if (generateLegalMoves(board).isEmpty()) {
            if (isKingInCheck(board, board.isWhiteToMove())) {
                return board.isWhiteToMove() ? Result.WHITE_CHECKMATED : Result.BLACK_CHECKMATED;
            }
            return Result.STALEMATE;
        }

        return Result.ONGOING;
    }
}