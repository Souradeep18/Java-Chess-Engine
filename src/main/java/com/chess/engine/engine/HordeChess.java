package com.chess.engine.engine;

import java.util.List;

/**
 * Horde chess rules.
 *
 * 
 *   White has no king: 36 pawns (all of ranks 1-4 plus b5, c5, f5, g5). Black has the normal pieces.
 *   White wins by checkmating the black king; Black wins by capturing every white piece.
 *   White pawns on the first and second rank may move two squares. Every other pawn
 *       moves one square and captures diagonally or en passant.
 *   An en passant capture is only possible after a double step from the second rank (or, for Black,
 *       the usual seventh rank); a double step from the first rank does not offer one.
 */
public final class HordeChess {

    public static final String VARIANT_STANDARD = "variant-standard";
    public static final String VARIANT_HORDE = "variant-horde chess";

    public static final String WHITE_WINS_STATUS = "White wins by Checkmate!";
    public static final String BLACK_WINS_STATUS = "Black wins!";

    public enum Result { 
    	ONGOING, 
    	WHITE_WINS, 
    	BLACK_WINS, 
    	STALEMATE 
    	}

    private HordeChess() {
    }

    /** Puts the standard Horde starting position on the board (White to move, no white castling). */
    public static void setupStartingPosition(Board board) {
        board.setHorde(true);

        for (int sq = 0; sq < 64; sq++) {
            board.setPiece(sq, '.');
        }

        // White: ranks 1-4 full of pawns, plus b5, c5, f5, g5  (32 + 4 = 36 pawns)
        for (int rank = 0; rank < 4; rank++) {
            for (int file = 0; file < 8; file++) {
                board.setPiece(rank * 8 + file, 'P');
            }
        }
        for (int file : new int[]{1, 2, 5, 6}) {
            board.setPiece(4 * 8 + file, 'P');
        }

        // Black: normal pieces
        String back = "rnbqkbnr";
        for (int file = 0; file < 8; file++) {
            board.setPiece(7 * 8 + file, back.charAt(file));
            board.setPiece(6 * 8 + file, 'p');
        }

        board.setWhiteToMove(true);
        board.setWhiteKingSideCastle(false);
        board.setWhiteQueenSideCastle(false);
        board.setBlackKingSideCastle(true);
        board.setBlackQueenSideCastle(true);
        board.setEnPassantSquare(-1);
    }

    /**
     * White pawns on the first rank may also move two squares (pawns on the second rank
     * already can in standard chess).
     */
    public static boolean canDoubleStepFromFirstRank(Board board, boolean whitePawn, int rank) {
        return board.isHorde() && whitePawn && rank == 0;
    }

    /** A double step from the first rank does not give the opponent an en passant capture. */
    public static boolean doubleStepSetsEnPassant(Board board, int fromSquare) {
        return !(board.isHorde() && fromSquare / 8 == 0);
    }

    public static boolean whiteHasNoPieces(Board board) {
        for (int sq = 0; sq < 64; sq++) {
            char p = board.getPiece(sq);
            if (p != '.' && Character.isUpperCase(p)) {
                return false;
            }
        }
        return true;
    }

    /** Result of a Horde position (ONGOING for non-Horde boards). */
    public static Result getResult(Board board) {
        if (!board.isHorde()) {
            return Result.ONGOING;
        }

        if (whiteHasNoPieces(board)) {
            return Result.BLACK_WINS;
        }

        List<Move> legal = MoveGenerator.generateLegalMoves(board);
        if (legal.isEmpty()) {
            if (!board.isWhiteToMove() && MoveGenerator.isKingInCheck(board, false)) {
                return Result.WHITE_WINS;
            }
            return Result.STALEMATE;
        }

        return Result.ONGOING;
    }

    /**
     * Board editor rule check.
     *
     * @return a message describing why the piece cannot be put on that square, or null if it can
     */
    public static String placementError(Board board, int square, char piece) {
        int rank = square / 8;

        if (board.isHorde() && piece == 'K') {
            return "White has no king in Horde chess.";
        }

        if (Character.toLowerCase(piece) == 'p') {
            boolean whitePawnOnFirstRankInHorde = board.isHorde() && piece == 'P' && rank == 0;

            if (rank == 7 || (rank == 0 && !whitePawnOnFirstRankInHorde)) {
                return board.isHorde() && piece == 'P'
                        ? "Pawns cannot be placed on the 8th rank."
                        : "Pawns cannot be placed on the 1st or 8th rank.";
            }
        }
        return null;
    }
}