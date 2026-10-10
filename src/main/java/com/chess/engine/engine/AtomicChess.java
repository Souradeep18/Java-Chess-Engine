package com.chess.engine.engine;

import java.util.ArrayList;
import java.util.List;

/*
Everything is normal chess until a capture happens. A capture is an explosion: the captured
   piece, the capturing piece and every piece on the eight surrounding squares are removed.
     Pawns on the surrounding squares are immune (only the captured / capturing pawn go).
     A king can never capture (it would blow itself up), and a capture that blows up the capturer's
     own king is illegal.
 A player whose king is blown off loses at once . A capture that blows off the enemy king
     is always legal, even when the capturer is in check.
Kings may stand next to each other: a king cannot capture, and any capture of a king next to the
     enemy king would blow up the capturer's own king, so neither king is in check while they touch.
 Checkmate is normal checkmate: in check with no legal reply. A capture of the checking piece that
     would blow up your own king (e.g. a queen giving check from next to your king) is not available.
*/

public final class AtomicChess {

    public static final String VARIANT_ATOMIC = "variant-atomic chess";

    public enum Result {
        ONGOING,
        WHITE_KING_EXPLODED,   // Black wins
        BLACK_KING_EXPLODED,   // White wins
        WHITE_CHECKMATED,      // Black wins
        BLACK_CHECKMATED,      // White wins
        STALEMATE,
        BARE_KINGS
    }

    private AtomicChess() {
    }

    // ------------------------------------------------------------------ explosions

    /**
     * Blows up the capture that just landed on {@code center}: removes the piece standing there (the
     * capturer) and every non-pawn piece on the eight neighbouring squares. Castling rights that
     * depended on a removed king or rook are cleared.
     */
    public static List<int[]> explode(Board board, int center) {
        List<int[]> removed = new ArrayList<>();

        char atCenter = board.getPiece(center);
        if (atCenter != '.') {
            removed.add(new int[]{center, atCenter});
            board.setPiece(center, '.');
        }

        int centerFile = center % 8;
        int centerRank = center / 8;

        for (int dr = -1; dr <= 1; dr++) {
            for (int df = -1; df <= 1; df++) {
                if (dr == 0 && df == 0) {
                    continue;
                }

                int r = centerRank + dr;
                int f = centerFile + df;
                if (r < 0 || r > 7 || f < 0 || f > 7) {
                    continue;
                }

                int sq = r * 8 + f;
                char piece = board.getPiece(sq);

                if (piece != '.' && Character.toUpperCase(piece) != 'P') {
                    removed.add(new int[]{sq, piece});
                    board.setPiece(sq, '.');
                }
            }
        }

        if (!removed.isEmpty()) {
            fixCastlingRights(board);
        }
        return removed;
    }

    /** Puts back the pieces removed by {@link #explode(Board, int)}. */
    public static void restore(Board board, List<int[]> removed) {
        for (int[] entry : removed) {
            board.setPiece(entry[0], (char) entry[1]);
        }
    }

    private static void fixCastlingRights(Board board) {
        if (board.getPiece(4) != 'K') {
            board.setWhiteKingSideCastle(false);
            board.setWhiteQueenSideCastle(false);
        }
        if (board.getPiece(60) != 'k') {
            board.setBlackKingSideCastle(false);
            board.setBlackQueenSideCastle(false);
        }
        if (board.getPiece(0) != 'R') board.setWhiteQueenSideCastle(false);
        if (board.getPiece(7) != 'R') board.setWhiteKingSideCastle(false);
        if (board.getPiece(56) != 'r') board.setBlackQueenSideCastle(false);
        if (board.getPiece(63) != 'r') board.setBlackKingSideCastle(false);
    }

    // ------------------------------------------------------------------ kings and checks

    public static int findKing(Board board, boolean white) {
        char king = white ? 'K' : 'k';
        for (int sq = 0; sq < 64; sq++) {
            if (board.getPiece(sq) == king) {
                return sq;
            }
        }
        return -1;
    }

    /** True if that side's king has been blown off (or is missing). */
    public static boolean hasLostKing(Board board, boolean white) {
        return findKing(board, white) == -1;
    }

    public static boolean kingsAdjacent(Board board) {
        int w = findKing(board, true);
        int b = findKing(board, false);
        if (w == -1 || b == -1) {
            return false;
        }
        return Math.abs(w % 8 - b % 8) <= 1 && Math.abs(w / 8 - b / 8) <= 1;
    }

    /**
     * Is the king in check? Kings never attack (they cannot capture), and while the two kings touch
     * neither is in check.
     */
    public static boolean isKingInCheck(Board board, boolean white) {
        int king = findKing(board, white);
        if (king == -1) {
            return false;
        }
        if (kingsAdjacent(board)) {
            return false;
        }
        return isSquareAttacked(board, king, !white);
    }

    /** Is the square attacked by a pawn, knight, bishop, rook or queen of that colour? (Kings do not attack.) */
    public static boolean isSquareAttacked(Board board, int square, boolean byWhite) {
        int file = square % 8;
        int rank = square / 8;

        // pawns: a white pawn attacks diagonally upwards, so it stands one rank below the target
        int pawnRank = byWhite ? rank - 1 : rank + 1;
        char pawn = byWhite ? 'P' : 'p';
        if (pawnRank >= 0 && pawnRank <= 7) {
            if (file > 0 && board.getPiece(pawnRank * 8 + file - 1) == pawn) return true;
            if (file < 7 && board.getPiece(pawnRank * 8 + file + 1) == pawn) return true;
        }

        // knights
        char knight = byWhite ? 'N' : 'n';
        int[][] jumps = {{1, 2}, {2, 1}, {-1, 2}, {-2, 1}, {1, -2}, {2, -1}, {-1, -2}, {-2, -1}};
        for (int[] j : jumps) {
            int f = file + j[0];
            int r = rank + j[1];
            if (f >= 0 && f < 8 && r >= 0 && r < 8 && board.getPiece(r * 8 + f) == knight) {
                return true;
            }
        }

        // sliding pieces
        char rook = byWhite ? 'R' : 'r';
        char bishop = byWhite ? 'B' : 'b';
        char queen = byWhite ? 'Q' : 'q';
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

        for (int[] d : dirs) {
            boolean diagonal = d[0] != 0 && d[1] != 0;
            int f = file + d[0];
            int r = rank + d[1];

            while (f >= 0 && f < 8 && r >= 0 && r < 8) {
                char p = board.getPiece(r * 8 + f);
                if (p != '.') {
                    if (p == queen || (diagonal ? p == bishop : p == rook)) {
                        return true;
                    }
                    break;
                }
                f += d[0];
                r += d[1];
            }
        }
        return false;
    }

    /**
     * Is the position after a move legal for the side that made it?
     * Own king must survive; if the enemy king was blown off the move is always legal (it wins);
     * otherwise the own king must not be in check.
     */
    public static boolean isMoverSafe(Board after, boolean moverWhite) {
        if (hasLostKing(after, moverWhite)) {
            return false;
        }
        if (hasLostKing(after, !moverWhite)) {
            return true;
        }
        return !isKingInCheck(after, moverWhite);
    }

    // ------------------------------------------------------------------ move generation / results (UI side)

    public static List<Move> generateLegalMoves(Board board) {
        List<Move> legal = new ArrayList<>();

        // the game is over once a king has been blown off
        if (hasLostKing(board, true) || hasLostKing(board, false)) {
            return legal;
        }

        boolean moverWhite = board.isWhiteToMove();

        for (Move move : MoveGenerator.generatePseudoLegalMoves(board)) {
            Board copy = board.copy();
            MoveGenerator.makeMove(copy, move);   // applies the explosion for captures

            if (isMoverSafe(copy, moverWhite)) {
                legal.add(move);
            }
        }
        return legal;
    }

    private static boolean onlyKingsRemain(Board board) {
        for (int sq = 0; sq < 64; sq++) {
            char p = board.getPiece(sq);
            if (p != '.' && p != 'K' && p != 'k') {
                return false;
            }
        }
        return true;
    }

    public static Result getResult(Board board) {
        if (!board.isAtomic()) {
            return Result.ONGOING;
        }

        if (hasLostKing(board, true)) {
            return Result.WHITE_KING_EXPLODED;
        }
        if (hasLostKing(board, false)) {
            return Result.BLACK_KING_EXPLODED;
        }

        if (onlyKingsRemain(board)) {
            return Result.BARE_KINGS;
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