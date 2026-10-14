package com.chess.engine.engine;

import java.util.List;

/*
  Chess960 (Fischer Random Chess). The pawns stand on their usual squares, the pieces on the 1st and 8th
  rank are shuffled (bishops on opposite colours, king between the rooks) in one of 960 ways, numbered
  0..959 with the standard (Scharnagl) numbering - 518 is the normal chess position.

  Castling: the king ends on g1/c1 (g8/c8) and the rook on f1/d1 (f8/d8) exactly as in normal chess. It is
  played by moving the KING ONTO ITS ROOK (move.from = king square, move.to = rook square). Every square
  between the king's and the rook's start and end squares must be empty (apart from the king and that rook),
  and the king may not start on, cross or land on an attacked square.
 */
public final class Chess960 {

    public static final String VARIANT_CHESS960 = "variant-chess960";

    public static final int MIN_POSITION = 0;
    public static final int MAX_POSITION = 959;
    public static final int DEFAULT_POSITION = 518;   // the normal chess starting position

    // the 10 ways to put two knights on the 5 squares that are still free
    private static final int[][] KNIGHTS = {
            {0, 1}, {0, 2}, {0, 3}, {0, 4}, {1, 2}, {1, 3}, {1, 4}, {2, 3}, {2, 4}, {3, 4}};

    private Chess960() {
    }

    public static boolean isValidPosition(int id) {
        return id >= MIN_POSITION && id <= MAX_POSITION;
    }

    // White's back rank (a..h) of starting position number id, e.g. 518 -> "RNBQKBNR".
    public static String backRank(int id) {
        if (!isValidPosition(id)) {
            throw new IllegalArgumentException("Chess960 position must be 0..959: " + id);
        }

        char[] rank = new char[8];
        java.util.Arrays.fill(rank, '.');

        int n = id;
        rank[1 + 2 * (n % 4)] = 'B';      // bishop on a light square (b, d, f, h)
        n /= 4;
        rank[2 * (n % 4)] = 'B';          // bishop on a dark square (a, c, e, g)
        n /= 4;

        placeOnFree(rank, n % 6, 'Q');    // queen on one of the 6 free squares
        n /= 6;

        int[] knights = KNIGHTS[n];       // knights on two of the 5 free squares
        int[] free = freeSquares(rank);
        rank[free[knights[0]]] = 'N';
        rank[free[knights[1]]] = 'N';

        free = freeSquares(rank);         // the last three squares: rook, king, rook
        rank[free[0]] = 'R';
        rank[free[1]] = 'K';
        rank[free[2]] = 'R';

        return new String(rank);
    }

    private static void placeOnFree(char[] rank, int index, char piece) {
        rank[freeSquares(rank)[index]] = piece;
    }

    private static int[] freeSquares(char[] rank) {
        int count = 0;
        for (char c : rank) if (c == '.') count++;
        int[] free = new int[count];
        int k = 0;
        for (int i = 0; i < 8; i++) if (rank[i] == '.') free[k++] = i;
        return free;
    }

    // Puts position number id (pawns, pieces, all castling rights) on the board. Variant flag is set too.
    public static void setupStartingPosition(Board board, int id) {
        board.setupStartingPosition();   // resets every variant flag and the castling rook files

        String white = backRank(id);
        for (int file = 0; file < 8; file++) {
            board.setPiece(file, white.charAt(file));
            board.setPiece(8 + file, 'P');
            board.setPiece(48 + file, 'p');
            board.setPiece(56 + file, Character.toLowerCase(white.charAt(file)));
        }

        board.setChess960(true);
        board.setChess960Id(id);
        configureCastling(board);
        board.setWhiteToMove(true);
        board.setEnPassantSquare(-1);
    }

    // True if the 32 pieces stand exactly as in starting position number id.
    public static boolean matchesStart(Board board, int id) {
        if (!isValidPosition(id)) return false;
        String white = backRank(id);
        for (int file = 0; file < 8; file++) {
            if (board.getPiece(file) != white.charAt(file)) return false;
            if (board.getPiece(8 + file) != 'P') return false;
            if (board.getPiece(48 + file) != 'p') return false;
            if (board.getPiece(56 + file) != Character.toLowerCase(white.charAt(file))) return false;
        }
        for (int sq = 16; sq < 48; sq++) {
            if (board.getPiece(sq) != '.') return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ castling set-up

    // File of the rook that can castle on the given side (the outermost rook on that side of the king), or -1.
    public static int castlingRookFile(Board board, boolean white, boolean kingSide) {
        int rank = white ? 0 : 7;
        char king = white ? 'K' : 'k';
        char rook = white ? 'R' : 'r';

        int kingFile = -1;
        for (int f = 0; f < 8; f++) {
            if (board.getPiece(rank * 8 + f) == king) {
                kingFile = f;
                break;
            }
        }
        if (kingFile < 0) return -1;

        if (kingSide) {
            for (int f = 7; f > kingFile; f--) {
                if (board.getPiece(rank * 8 + f) == rook) return f;
            }
        } else {
            for (int f = 0; f < kingFile; f++) {
                if (board.getPiece(rank * 8 + f) == rook) return f;
            }
        }
        return -1;
    }

    // Stores the rook files for the position on the board (call after the pieces changed).
    public static void configureCastling(Board board) {
        for (int c = 0; c < 4; c++) {
            boolean white = c < 2;
            boolean kingSide = (c % 2 == 0);
            int file = castlingRookFile(board, white, kingSide);
            board.setCastleRookFile(white, kingSide, file >= 0 ? file : (kingSide ? 7 : 0));
        }
    }

    // ------------------------------------------------------------------ move generation

    // Adds the castling moves of the king on kingSq (king-onto-rook encoding).
    public static void addCastlingMoves(Board b, int kingSq, List<Move> moves) {
        char piece = b.getPiece(kingSq);
        if (piece != 'K' && piece != 'k') return;

        boolean white = piece == 'K';
        int rank = kingSq / 8;
        if (rank != (white ? 0 : 7)) return;

        for (int side = 0; side < 2; side++) {
            boolean kingSide = side == 0;
            boolean allowed = white
                    ? (kingSide ? b.canWhiteCastleKingSide() : b.canWhiteCastleQueenSide())
                    : (kingSide ? b.canBlackCastleKingSide() : b.canBlackCastleQueenSide());
            if (!allowed) continue;

            int kingFile = kingSq % 8;
            int rookFile = b.getCastleRookFile(white, kingSide);
            if (kingSide ? rookFile <= kingFile : rookFile >= kingFile) continue;

            int rookSq = rank * 8 + rookFile;
            if (b.getPiece(rookSq) != (white ? 'R' : 'r')) continue;

            int kingDest = kingSide ? 6 : 2;
            int rookDest = kingSide ? 5 : 3;

            int lo = Math.min(Math.min(kingFile, kingDest), Math.min(rookFile, rookDest));
            int hi = Math.max(Math.max(kingFile, kingDest), Math.max(rookFile, rookDest));

            boolean free = true;
            for (int f = lo; f <= hi && free; f++) {
                int sq = rank * 8 + f;
                if (sq != kingSq && sq != rookSq && b.getPiece(sq) != '.') {
                    free = false;
                }
            }
            if (!free) continue;

            // the king may not start on, cross or land on an attacked square
            boolean safe = true;
            for (int f = Math.min(kingFile, kingDest); f <= Math.max(kingFile, kingDest) && safe; f++) {
                if (Board.isSquareAttacked(b, rank * 8 + f, !white)) {
                    safe = false;
                }
            }
            if (!safe) continue;

            moves.add(new Move(kingSq, rookSq, '\0', kingSide, !kingSide, false, false));
        }
    }
}