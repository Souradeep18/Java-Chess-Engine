package com.chess.engine.engine;

/*
  Exact king + pawn against king knowledge.
 
  The table is generated once (about 100 ms) by retrograde analysis, the same way a tablebase is built:
  every legal position of "White king + White pawn v Black king" is classified as a win for the pawn's side
  or a draw. After that the engine never has to guess in the most common pawn endings (opposition, key
  squares, the rule of the square, rook pawns, stalemate) - it simply knows.
 
  Squares are numbered rank * 8 + file (a1 = 0, h8 = 63), like the rest of the engine.
 */
final class KpkBitbase {

    private static final byte INVALID = 0;
    private static final byte UNKNOWN = 1;
    private static final byte DRAW = 2;
    private static final byte WIN = 4;

    // index: [stm][defendingKing][attackingKing][pawnFile 0..3][pawnRank 1..6 -> 0..5]
    private static final byte[] TABLE = new byte[2 * 64 * 64 * 4 * 6];
    private static boolean ready = false;

    private KpkBitbase() {
    }

    private static int index(int stm, int bk, int wk, int pawn) {
        int file = pawn & 7;
        int rank = pawn >> 3;
        return ((((stm * 64 + bk) * 64 + wk) * 4 + file) * 6) + (rank - 1);
    }

    private static int dist(int a, int b) {
        return Math.max(Math.abs((a & 7) - (b & 7)), Math.abs((a >> 3) - (b >> 3)));
    }

    private static boolean pawnAttacks(int pawn, int sq) {
        // white pawn attacks the two squares diagonally in front of it
        int r = pawn >> 3, f = pawn & 7;
        return (sq >> 3) == r + 1 && Math.abs((sq & 7) - f) == 1;
    }

    private static synchronized void build() {
        if (ready) {
            return;
        }

        // 1. classify the obvious positions
        for (int stm = 0; stm < 2; stm++) {
            for (int bk = 0; bk < 64; bk++) {
                for (int wk = 0; wk < 64; wk++) {
                    for (int file = 0; file < 4; file++) {
                        for (int rank = 1; rank <= 6; rank++) {
                            int pawn = rank * 8 + file;
                            TABLE[index(stm, bk, wk, pawn)] = initial(stm, bk, wk, pawn);
                        }
                    }
                }
            }
        }

        // 2. propagate until nothing changes
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int stm = 0; stm < 2; stm++) {
                for (int bk = 0; bk < 64; bk++) {
                    for (int wk = 0; wk < 64; wk++) {
                        for (int file = 0; file < 4; file++) {
                            for (int rank = 1; rank <= 6; rank++) {
                                int pawn = rank * 8 + file;
                                int i = index(stm, bk, wk, pawn);
                                if (TABLE[i] != UNKNOWN) {
                                    continue;
                                }
                                byte r = resolve(stm, bk, wk, pawn);
                                if (r != UNKNOWN) {
                                    TABLE[i] = r;
                                    changed = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        ready = true;
    }

    private static byte initial(int stm, int bk, int wk, int pawn) {
        // two pieces on one square, kings touching, or a king that could be captured
        if (dist(wk, bk) <= 1 || wk == pawn || bk == pawn || (stm == 0 && pawnAttacks(pawn, bk))) {
            return INVALID;
        }

        int ahead = pawn + 8;

        // pawn on the 7th that can promote without being captured
        if (stm == 0 && (pawn >> 3) == 6 && wk != ahead && (dist(bk, ahead) > 1 || dist(wk, ahead) == 1)) {
            return WIN;
        }

        if (stm == 1) {
            // stalemate, or the black king simply takes an unprotected pawn
            boolean hasMove = false;
            boolean capturesPawn = false;
            for (int to = 0; to < 64; to++) {
                if (to == bk || dist(to, bk) != 1) continue;
                if (dist(to, wk) <= 1) continue;
                if (pawnAttacks(pawn, to)) continue;
                hasMove = true;
                if (to == pawn) capturesPawn = true;
            }
            if (!hasMove || capturesPawn) {
                return DRAW;
            }
        }

        return UNKNOWN;
    }

    private static byte resolve(int stm, int bk, int wk, int pawn) {
        int r = 0;

        if (stm == 0) {
            // white: king moves, pawn push(es)
            for (int to = 0; to < 64; to++) {
                if (to == wk || dist(to, wk) != 1) continue;
                r |= TABLE[index(1, bk, to, pawn)];
            }
            int rank = pawn >> 3;
            if (rank < 6) {
                r |= TABLE[index(1, bk, wk, pawn + 8)];
            }
            if (rank == 1 && pawn + 8 != wk && pawn + 8 != bk) {
                r |= TABLE[index(1, bk, wk, pawn + 16)];
            }
            if ((r & WIN) != 0) return WIN;
            if ((r & UNKNOWN) != 0) return UNKNOWN;
            return DRAW;
        }

        // black: king moves only
        for (int to = 0; to < 64; to++) {
            if (to == bk || dist(to, bk) != 1) continue;
            r |= TABLE[index(0, to, wk, pawn)];
        }
        if ((r & DRAW) != 0) return DRAW;
        if ((r & UNKNOWN) != 0) return UNKNOWN;
        return WIN;
    }

    /**
     @param attKing        square of the king that owns the pawn
     @param attPawn        square of the pawn
     @param defKing        square of the lone king
     @param attackerToMove whose turn it is
     @param attackerIsWhite true when the pawn moves towards rank 8
     @return true when the side with the pawn wins with best play
     */
    static boolean isWin(int attKing, int attPawn, int defKing, boolean attackerToMove, boolean attackerIsWhite) {
        build();

        if (!attackerIsWhite) {          // mirror vertically: the black pawn becomes a white one
            attKing ^= 56;
            attPawn ^= 56;
            defKing ^= 56;
        }
        if ((attPawn & 7) > 3) {         // mirror horizontally: only files a-d are stored
            attKing ^= 7;
            attPawn ^= 7;
            defKing ^= 7;
        }

        int rank = attPawn >> 3;
        if (rank < 1 || rank > 6) {
            return true;                 // not a real KPK position (e.g. already promoted): do not claim a draw
        }

        byte r = TABLE[index(attackerToMove ? 0 : 1, defKing, attKing, attPawn)];
        return r == WIN || r == INVALID;
    }
}