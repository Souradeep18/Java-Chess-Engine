package com.chess.engine.ui;

import com.chess.engine.engine.AtomicChess;
import com.chess.engine.engine.Board;
import com.chess.engine.engine.Move;
import com.chess.engine.engine.MoveGenerator;

import java.util.List;

/* Move helpers shared by the Play, Puzzle and Learn screens (UCI parsing, SAN, move lookup). */
public class ChessUtil {

    private ChessUtil() { }

    static String squareName(int sq) {
        return "" + (char) ('a' + (sq & 7)) + (char) ('1' + (sq >> 3));
    }

    static int squareIndex(String name) {
        return (name.charAt(1) - '1') * 8 + (name.charAt(0) - 'a');
    }

    static String uci(Move m) {
        String s = squareName(m.from) + squareName(m.to);
        if (m.promotion != '\0' && m.promotion != ' ') s += Character.toLowerCase(m.promotion);
        return s;
    }

    // The legal move matching a UCI string such as "e2e4" or "e7e8q", or null.
    static Move fromUci(Board board, String uci) {
        if (uci == null || uci.length() < 4) return null;
        int from = squareIndex(uci.substring(0, 2));
        int to = squareIndex(uci.substring(2, 4));
        char promo = uci.length() > 4 ? Character.toLowerCase(uci.charAt(4)) : '\0';
        Move fallback = null;
        for (Move m : MoveGenerator.generateLegalMoves(board)) {
            if (m.from != from || m.to != to) continue;
            char mp = (m.promotion == '\0' || m.promotion == ' ') ? '\0' : Character.toLowerCase(m.promotion);
            if (mp == promo) return m;
            if (promo == '\0' && mp == 'q') fallback = m;
        }
        return fallback;
    }

    /** The legal move that turns {@code before} into {@code after}, or null. */
    static Move findMoveBetween(Board before, Board after) {
        String target = placementAndSide(after.toFEN());
        for (Move m : MoveGenerator.generateLegalMoves(before)) {
            Board copy = before.copy();
            MoveGenerator.makeMove(copy, m);
            if (placementAndSide(copy.toFEN()).equals(target)) return m;
        }
        return null;
    }

    private static String placementAndSide(String fen) {
        String[] f = fen.split(" ");
        return f[0] + " " + f[1];
    }

    // Standard algebraic notation of a legal move in the given position.
    static String san(Board board, Move m) {
        char piece = board.getPiece(m.from);
        char type = Character.toUpperCase(piece);
        StringBuilder sb = new StringBuilder();
        boolean capture = board.getPiece(m.to) != '.';

        if (type == 'K' && m.isCastle) {
            sb.append(m.to > m.from ? "O-O" : "O-O-O");
        } else if (type == 'P') {
            if ((m.from & 7) != (m.to & 7)) {
                capture = true;
                sb.append((char) ('a' + (m.from & 7))).append('x');
            }
            sb.append(squareName(m.to));
            if (m.promotion != '\0' && m.promotion != ' ') sb.append('=').append(Character.toUpperCase(m.promotion));
        } else {
            sb.append(type);
            boolean sameFile = false, sameRank = false, ambiguous = false;
            for (Move o : MoveGenerator.generateLegalMoves(board)) {
                if (o.from == m.from || o.to != m.to) continue;
                if (Character.toUpperCase(board.getPiece(o.from)) != type
                        || Character.isUpperCase(board.getPiece(o.from)) != Character.isUpperCase(piece)) continue;
                ambiguous = true;
                if ((o.from & 7) == (m.from & 7)) sameFile = true;
                if ((o.from >> 3) == (m.from >> 3)) sameRank = true;
            }
            if (ambiguous) {
                if (!sameFile) sb.append((char) ('a' + (m.from & 7)));
                else if (!sameRank) sb.append((char) ('1' + (m.from >> 3)));
                else sb.append(squareName(m.from));
            }
            if (capture) sb.append('x');
            sb.append(squareName(m.to));
        }

        Board after = board.copy();
        MoveGenerator.makeMove(after, m);
        if (inCheck(after, after.isWhiteToMove())) {
            List<Move> replies = MoveGenerator.generateLegalMoves(after);
            sb.append(replies.isEmpty() ? '#' : '+');
        }
        return sb.toString();
    }

    // Checks detection that knows the variants: no check in anti-chess / racing kings, atomic and horde special cases.
    static boolean inCheck(Board b, boolean white) {
        if (b.isAntiChess() || b.isRacingKings()) return false;
        if (b.isAtomic()) return AtomicChess.isKingInCheck(b, white);
        if (b.isHorde() && white) return false;      // White has no king in Horde
        return b.isInCheck(white);
    }

    static boolean isCheckmate(Board b) {
        return inCheck(b, b.isWhiteToMove()) && MoveGenerator.generateLegalMoves(b).isEmpty();
    }

    static boolean isStalemate(Board b) {
        return !inCheck(b, b.isWhiteToMove()) && MoveGenerator.generateLegalMoves(b).isEmpty();
    }

    /** True when neither side can possibly mate (K vs K, K+minor vs K, same-colour bishops). */
    static boolean insufficientMaterial(Board b) {
        int knights = 0, bishopsLight = 0, bishopsDark = 0, other = 0;
        for (int s = 0; s < 64; s++) {
            char c = Character.toUpperCase(b.getPiece(s));
            if (c == '.' || c == 'K') continue;
            if (c == 'N') knights++;
            else if (c == 'B') {
                if ((((s & 7) + (s >> 3)) & 1) == 0) bishopsDark++; else bishopsLight++;
            } else other++;
        }
        if (other > 0) return false;
        int minors = knights + bishopsLight + bishopsDark;
        if (minors <= 1) return true;
        return knights == 0 && (bishopsLight == 0 || bishopsDark == 0);
    }

    static int pieceValue(char p) {
        switch (Character.toUpperCase(p)) {
            case 'P': return 1;
            case 'N': case 'B': return 3;
            case 'R': return 5;
            case 'Q': return 9;
            default: return 0;
        }
    }

    static String sideName(boolean white) {
        return white ? "white" : "black";
    }
}