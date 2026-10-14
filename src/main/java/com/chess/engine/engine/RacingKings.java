package com.chess.engine.engine;

import java.util.List;

/*
  Racing Kings: no pawns, and a check can never be given or left on the board - a move that would
  put EITHER king in check is illegal. The first king to reach the 8th rank wins. White moves first,
  so if White gets there first Black gets one answering move: if Black's king also reaches the
  8th rank the game is a draw, otherwise White wins. Castling does not exist.
 */
public final class RacingKings {

    public static final String VARIANT_RACING_KINGS = "variant-racing-kings";

    public enum Result {
        ONGOING,
        WHITE_WINS,          // White reached the 8th rank first (Black could not follow)
        BLACK_WINS,          // Black reached the 8th rank first
        DRAW_BOTH_REACHED,   // White reached it, Black answered by reaching it as well
        STALEMATE            // the side to move has no legal move
    }

    private RacingKings() {
    }

    // Standard starting position: 8/8/8/8/8/8/krbnNBRK/qrbnNBRQ, White to move.
    public static void setupStartingPosition(Board board) {
        board.setRacingKings(true);
        for (int sq = 0; sq < 64; sq++) {
            board.setPiece(sq, '.');
        }

        String rank1 = "qrbnNBRQ";
        String rank2 = "krbnNBRK";
        for (int file = 0; file < 8; file++) {
            board.setPiece(file, rank1.charAt(file));
            board.setPiece(8 + file, rank2.charAt(file));
        }

        board.setWhiteToMove(true);
        board.setEnPassantSquare(-1);
    }

    public static boolean kingOnEighth(Board board, boolean white) {
        char king = white ? 'K' : 'k';
        for (int sq = 56; sq < 64; sq++) {
            if (board.getPiece(sq) == king) {
                return true;
            }
        }
        return false;
    }

    /*
      Result that follows from the king positions alone: 1 = White has won, -1 = Black has won,
      2 = drawn, 0 = not decided. (White on the 8th rank with Black to move is NOT decided:
      Black still has its answering move.)
     */
    public static int outcome(Board board) {
        boolean w = kingOnEighth(board, true);
        boolean b = kingOnEighth(board, false);

        if (w && b) return 2;
        if (b) return -1;
        if (w && board.isWhiteToMove()) return 1;
        return 0;
    }

    public static boolean isDecided(Board board) {
        return outcome(board) != 0;
    }

    // Both kings must stay out of check; returns the message for the editor dialog, or null.
    public static String checkError(Board board) {
        if (board.isInCheck(true)) return "White king cannot be in check";
        if (board.isInCheck(false)) return "Black king cannot be in check";
        return null;
    }

    public static Result getResult(Board board) {
        switch (outcome(board)) {
            case 1: return Result.WHITE_WINS;
            case -1: return Result.BLACK_WINS;
            case 2: return Result.DRAW_BOTH_REACHED;
            default: break;
        }

        List<Move> legal = MoveGenerator.generateLegalMoves(board);

        // White is on the 8th rank and Black is to move: only a king step to the 8th rank saves Black
        if (!board.isWhiteToMove() && kingOnEighth(board, true)) {
            for (Move m : legal) {
                if (board.getPiece(m.from) == 'k' && m.to >= 56) {
                    return Result.ONGOING;
                }
            }
            return Result.WHITE_WINS;
        }

        return legal.isEmpty() ? Result.STALEMATE : Result.ONGOING;
    }

    // Evaluation from White's point of view, in centipawns: how far each king has come, plus material.
    public static int evaluate(Board board) {
        int score = 0;
        int whiteRank = 0, blackRank = 0;
        int whiteMat = 0, blackMat = 0;

        for (int sq = 0; sq < 64; sq++) {
            char p = board.getPiece(sq);
            if (p == '.') continue;
            int rank = sq / 8;
            if (p == 'K') {
                whiteRank = rank;
            } else if (p == 'k') {
                blackRank = rank;
            } else {
                int v = value(p);
                if (Character.isUpperCase(p)) whiteMat += v; else blackMat += v;
            }
        }

        score += kingValue(whiteRank) - kingValue(blackRank);
        score += (whiteMat - blackMat) / 4;
        score += board.isWhiteToMove() ? 25 : -25; // the side to move is a step ahead in the race
        return score;
    }

    private static int kingValue(int rank) {
        return rank * rank * 30;
    }

    private static int value(char p) {
        switch (Character.toUpperCase(p)) {
            case 'Q': return 900;
            case 'R': return 500;
            case 'B': return 330;
            case 'N': return 320;
            default: return 0;
        }
    }
}