package com.chess.engine.analysis;

import com.chess.engine.engine.Board;
import com.chess.engine.engine.Move;
import com.chess.engine.engine.MoveGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
  A game read from PGN text: the tag pairs, the starting position and the moves of the main line.
  Comments, variations, annotation symbols and move numbers are skipped.
 */
public final class PgnGame {

    private static final Pattern TAG = Pattern.compile("\\[\\s*(\\w+)\\s+\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\]");

    private final Map<String, String> tags;
    private final Board startBoard;
    private final List<Move> moves;
    private final List<String> sans;
    private final String result;

    private PgnGame(Map<String, String> tags, Board startBoard, List<Move> moves, List<String> sans, String result) {
        this.tags = tags;
        this.startBoard = startBoard;
        this.moves = moves;
        this.sans = sans;
        this.result = result;
    }

    public Map<String, String> getTags() {
        return Collections.unmodifiableMap(tags);
    }

    public String getTag(String name, String fallback) {
        String v = tags.get(name);
        return v == null || v.trim().isEmpty() || v.equals("?") ? fallback : v;
    }

    /** The position the game starts from (a copy, so callers may change it). */
    public Board getStartBoard() {
        return startBoard.copy();
    }

    public List<Move> getMoves() {
        return Collections.unmodifiableList(moves);
    }

    /** The moves exactly as they were written in the PGN (without check signs and annotations). */
    public List<String> getSans() {
        return Collections.unmodifiableList(sans);
    }

    public String getResult() {
        return result;
    }

    public String getWhiteName() {
        return getTag("White", "White");
    }

    public String getBlackName() {
        return getTag("Black", "Black");
    }

    // ------------------------------------------------------------------------------------------
    //  parsing
    // ------------------------------------------------------------------------------------------

    /** @throws IllegalArgumentException with a readable message when the text is not a playable game */
    public static PgnGame parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Paste the game in PGN format first.");
        }

        String pgn = text.replace("\r\n", "\n").replace('\r', '\n');

        // only the first game of the text
        Map<String, String> tags = new LinkedHashMap<>();
        boolean seenMoves = false;
        StringBuilder body = new StringBuilder();

        for (String line : pgn.split("\n")) {
            String t = line.trim();
            if (t.startsWith("%")) {
                continue;                                   // escape mechanism: ignored
            }
            if (t.startsWith("[")) {
                Matcher m = TAG.matcher(t);
                boolean any = false;
                if (seenMoves) {
                    break;                                  // the tags of a second game
                }
                while (m.find()) {
                    any = true;
                    tags.put(m.group(1), m.group(2).replace("\\\"", "\"").replace("\\\\", "\\"));
                }
                if (any) {
                    continue;
                }
            }
            if (!t.isEmpty()) {
                seenMoves = true;
            }
            body.append(line).append('\n');
        }

        String movetext = stripAnnotations(body.toString());

        Board board;
        String fen = tags.get("FEN");
        if (fen != null && !fen.trim().isEmpty()) {
            try {
                board = Board.fromFEN(fen.trim());
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("The FEN tag of the PGN is not valid.");
            }
        } else {
            board = new Board();
        }
        Board start = board.copy();

        List<Move> moves = new ArrayList<>();
        List<String> sans = new ArrayList<>();
        String result = tags.containsKey("Result") ? tags.get("Result") : "*";

        for (String raw : movetext.trim().split("\\s+")) {
            if (raw.isEmpty()) {
                continue;
            }
            if (raw.equals("1-0") || raw.equals("0-1") || raw.equals("1/2-1/2") || raw.equals("*")) {
                result = raw;
                break;                                      // end of the game
            }

            String san = raw.replaceAll("[!?]+", "");
            if (san.isEmpty() || san.equalsIgnoreCase("e.p.")) {
                continue;
            }

            int moveNumber = moves.size() / 2 + 1;
            Move move;
            try {
                move = matchSan(board, san);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(ex.getMessage() + " (move " + moveNumber
                        + (moves.size() % 2 == 0 ? "." : "...") + " " + san + ")");
            }

            sans.add(san.replace("+", "").replace("#", ""));
            moves.add(move);
            MoveGenerator.makeMove(board, move);
        }

        if (moves.isEmpty()) {
            throw new IllegalArgumentException("No moves were found in the text.");
        }

        return new PgnGame(tags, start, moves, sans, result);
    }

    /** Removes comments, variations, NAGs and move numbers, leaving only the move tokens and the result. */
    private static String stripAnnotations(String text) {
        StringBuilder out = new StringBuilder();
        int braces = 0;
        int parens = 0;
        boolean lineComment = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (lineComment) {
                if (c == '\n') {
                    lineComment = false;
                    out.append(' ');
                }
                continue;
            }
            if (braces > 0) {
                if (c == '}') braces--;
                continue;
            }
            if (c == '{') {
                braces++;
                continue;
            }
            if (c == ';') {
                lineComment = true;
                continue;
            }
            if (c == '(') {
                parens++;
                continue;
            }
            if (c == ')') {
                if (parens > 0) parens--;
                out.append(' ');
                continue;
            }
            if (parens > 0) {
                continue;
            }
            out.append(c);
        }

        String s = out.toString();
        s = s.replaceAll("\\$\\d+", " ");                                // numeric annotation glyphs
        s = s.replaceAll("(?<![A-Za-z0-9\\-/])\\d+\\s*\\.+", " ");     // 12.  and  12...
        s = s.replace("0-0-0", "O-O-O").replace("0-0", "O-O");          // castling written with zeros
        return s;
    }

    private static Move matchSan(Board board, String token) {
        String s = token.replace("+", "").replace("#", "").trim();

        List<Move> legal = MoveGenerator.generateLegalMoves(board);

        if (s.equals("O-O-O") || s.equals("o-o-o")) {
            for (Move m : legal) {
                if (m.castleQueenSide) return m;
            }
            throw new IllegalArgumentException("Illegal castling");
        }
        if (s.equals("O-O") || s.equals("o-o")) {
            for (Move m : legal) {
                if (m.castleKingSide) return m;
            }
            throw new IllegalArgumentException("Illegal castling");
        }

        char piece = 'P';
        int start = 0;
        if ("KQRBN".indexOf(s.charAt(0)) >= 0) {
            piece = s.charAt(0);
            start = 1;
        }

        char promotion = 0;
        int eq = s.indexOf('=');
        if (eq >= 0 && eq + 1 < s.length()) {
            promotion = Character.toUpperCase(s.charAt(eq + 1));
            s = s.substring(0, eq);
        } else if (piece == 'P' && s.length() >= 3 && "QRBNqrbn".indexOf(s.charAt(s.length() - 1)) >= 0
                && Character.isDigit(s.charAt(s.length() - 2))) {
            promotion = Character.toUpperCase(s.charAt(s.length() - 1));
            s = s.substring(0, s.length() - 1);
        }

        if (s.length() < start + 2) {
            throw new IllegalArgumentException("Cannot read the move");
        }

        char destFile = s.charAt(s.length() - 2);
        char destRank = s.charAt(s.length() - 1);
        if (destFile < 'a' || destFile > 'h' || destRank < '1' || destRank > '8') {
            throw new IllegalArgumentException("Cannot read the move");
        }
        int to = (destRank - '1') * 8 + (destFile - 'a');

        String disambiguation = s.substring(start, s.length() - 2).replace("x", "").replace("-", "").replace(":", "");

        Move found = null;
        int count = 0;

        for (Move m : legal) {
            if (m.to != to || m.castleKingSide || m.castleQueenSide) continue;
            if (Character.toUpperCase(board.getPiece(m.from)) != piece) continue;

            boolean hasPromotion = m.promotion != '\0' && m.promotion != ' ';
            if (promotion != 0) {
                if (!hasPromotion || Character.toUpperCase(m.promotion) != promotion) continue;
            } else if (hasPromotion) {
                // a promotion written without its piece is not accepted
                continue;
            }

            if (piece == 'P' && disambiguation.isEmpty() && (m.from & 7) != (to & 7)) continue;

            boolean matches = true;
            for (int i = 0; i < disambiguation.length(); i++) {
                char d = disambiguation.charAt(i);
                if (d >= 'a' && d <= 'h') {
                    if ((m.from & 7) != d - 'a') matches = false;
                } else if (d >= '1' && d <= '8') {
                    if ((m.from >> 3) != d - '1') matches = false;
                }
            }
            if (!matches) continue;

            found = m;
            count++;
        }

        if (count == 0) {
            throw new IllegalArgumentException("Illegal move");
        }
        if (count > 1) {
            throw new IllegalArgumentException("Ambiguous move");
        }
        return found;
    }
}