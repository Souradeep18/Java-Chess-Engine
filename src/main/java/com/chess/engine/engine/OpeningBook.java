package com.chess.engine.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/*
 Opening book for standard chess, built at start-up from SAN lines (both colours).
 The lines are replayed with the engine's own move generator, so every stored position is
 legal; a line is cut at its first move that cannot be parsed (see validate()). Positions are
 keyed by the position itself, so transpositions between lines are found automatically.

 Extra lines can be added without recompiling in a text file "opening_book.txt" (working directory,
 or on the classpath). Accepted row formats, mixed freely:
     e4 e5 Nf3 Nc6 Bb5 a6                          plain SAN, move numbers optional
     Ruy Lopez, Berlin | 1. e4 e5 2. Nf3 Nc6 ...    "name | moves"
     C65<TAB>Ruy Lopez: Berlin<TAB>1. e4 e5 ...     lichess-org/chess-openings TSV (a.tsv ... e.tsv)
 A '#' that starts a row or follows a blank starts a comment. Dropping the five lichess TSV files
 (CC0, ~3,400 lines, ECO A00-E99) into opening_book.txt gives exhaustive coverage of every opening.
 */
public final class OpeningBook {

    private static final class Entry {
        final int from;
        final int to;
        final char promo;
        int weight = 1;

        Entry(int from, int to, char promo) {
            this.from = from;
            this.to = to;
            this.promo = promo;
        }
    }

    private static final Map<String, List<Entry>> BOOK = new HashMap<>();
    private static final Random RNG = new Random();
    private static boolean built = false;
    private static final List<String> WARNINGS = new ArrayList<>();

    private OpeningBook() {
    }

    public static boolean isStandard(Board b) {
        return !(b.isAtomic() || b.isThreeCheck() || b.isAntiChess() || b.isKingOfTheHill()
                || b.isRacingKings() || b.isHorde() || b.isChess960());
    }

    /** A book move for the position (chosen at random, weighted by how many lines use it), or null. */
    public static Move probe(Board board) {
        ensureBuilt();

        List<Entry> entries = BOOK.get(key(board));
        if (entries == null) {
            return null;
        }

        List<Move> legal = board.generateLegalMoves();
        List<Move> moves = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;

        for (Entry e : entries) {
            for (Move m : legal) {
                if (m.from == e.from && m.to == e.to && promoOf(m) == e.promo) {
                    moves.add(m);
                    weights.add(e.weight);
                    total += e.weight;
                    break;
                }
            }
        }

        if (moves.isEmpty()) {
            return null;
        }

        int pick = RNG.nextInt(total);
        for (int i = 0; i < moves.size(); i++) {
            pick -= weights.get(i);
            if (pick < 0) {
                return moves.get(i);
            }
        }
        return moves.get(0);
    }

    // ------------------------------------------------------------------

    /** Lines whose tail was dropped because of an illegal / mistyped move ("label: 'token' at ply n"). */
    public static synchronized List<String> validate() {
        ensureBuilt();
        return new ArrayList<>(WARNINGS);
    }

    /** Number of distinct positions stored. */
    public static synchronized int positionCount() {
        ensureBuilt();
        return BOOK.size();
    }

    private static synchronized void ensureBuilt() {
        if (built) {
            return;
        }
        built = true;

        for (String line : LINES) {
            addLine(line);
        }
        for (String line : LINES_EXTRA) {
            addLine(line);
        }
        loadExternalFile();
    }

    private static void loadExternalFile() {
        try {
            Path path = Paths.get("opening_book.txt");
            if (Files.isRegularFile(path)) {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    addLine(line);
                }
                return;     // the working-directory file wins; do not load a classpath copy of it as well
            }

            try (InputStream in = OpeningBook.class.getResourceAsStream("/opening_book.txt")) {
                if (in != null) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = r.readLine()) != null) {
                        addLine(line);
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // a bad file must never break the engine
        }
    }

    /** Reduces a row of any accepted format to its move text ("" for blank / comment / header rows). */
    private static String extractMoves(String row) {
        String s = row.replaceAll("^\\s*#.*$", "").replaceAll("\\s#.*$", "").trim();
        if (s.isEmpty()) {
            return "";
        }
        if (s.indexOf('\t') >= 0) {
            String[] cols = s.split("\t");
            if (cols[0].trim().equalsIgnoreCase("eco")) {
                return "";
            }
            return cols[cols.length - 1];
        }
        int bar = s.lastIndexOf('|');
        return bar >= 0 ? s.substring(bar + 1) : s;
    }

    private static void addLine(String row) {
        String line = extractMoves(row);
        if (line.isEmpty()) {
            return;
        }

        try {
            Board board = new Board();
            int ply = 0;

            for (String raw : line.trim().split("\\s+")) {
                String token = raw.replaceAll("^\\d+\\.+", "");   // strip "12." / "12..."
                if (token.isEmpty() || token.equals("*") || token.contains("/")
                        || token.equals("1-0") || token.equals("0-1")) {
                    continue;
                }

                List<Move> legal = board.generateLegalMoves();
                Move move = parseSan(board, token, legal);
                if (move == null) {
                    // typo or illegal move: keep what we have, but say so
                    WARNINGS.add(abbreviate(line) + ": '" + token + "' at ply " + (ply + 1));
                    return;
                }

                record(key(board), move);
                ply++;

                Board next = board.copy();
                MoveGenerator.makeMove(next, move);
                board = next;
            }
        } catch (RuntimeException ex) {
            WARNINGS.add(abbreviate(line) + ": " + ex);
        }
    }

    private static String abbreviate(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }

    private static void record(String key, Move m) {
        List<Entry> list = BOOK.computeIfAbsent(key, k -> new ArrayList<>());
        char promo = promoOf(m);
        for (Entry e : list) {
            if (e.from == m.from && e.to == m.to && e.promo == promo) {
                e.weight++;
                return;
            }
        }
        list.add(new Entry(m.from, m.to, promo));
    }

    private static char promoOf(Move m) {
        char p = m.promotion;
        if (p == '\0' || p == ' ' || p == '.') {
            return '\0';
        }
        return Character.toUpperCase(p);
    }

    /** Pieces, side to move and castling rights (en passant is left out on purpose). */
    private static String key(Board b) {
        StringBuilder sb = new StringBuilder(72);
        for (int sq = 0; sq < 64; sq++) {
            sb.append(b.getPiece(sq));
        }
        sb.append(b.isWhiteToMove() ? 'w' : 'b');
        sb.append(b.canWhiteCastleKingSide() ? 'K' : '-');
        sb.append(b.canWhiteCastleQueenSide() ? 'Q' : '-');
        sb.append(b.canBlackCastleKingSide() ? 'k' : '-');
        sb.append(b.canBlackCastleQueenSide() ? 'q' : '-');
        return sb.toString();
    }

    private static Move parseSan(Board b, String raw, List<Move> legal) {
        String san = raw.replace("+", "").replace("#", "").replace("!", "").replace("?", "");
        if (san.isEmpty()) {
            return null;
        }

        if (san.equals("O-O") || san.equals("0-0")) {
            for (Move m : legal) if (m.castleKingSide) return m;
            return null;
        }
        if (san.equals("O-O-O") || san.equals("0-0-0")) {
            for (Move m : legal) if (m.castleQueenSide) return m;
            return null;
        }

        char piece = 'P';
        int idx = 0;
        if ("NBRQK".indexOf(san.charAt(0)) >= 0) {
            piece = san.charAt(0);
            idx = 1;
        }

        char promo = '\0';
        int eq = san.indexOf('=');
        if (eq >= 0 && eq + 1 < san.length()) {
            promo = Character.toUpperCase(san.charAt(eq + 1));
            san = san.substring(0, eq);
        }

        if (promo == '\0' && san.length() >= 3 && "QRBN".indexOf(san.charAt(san.length() - 1)) >= 0
                && Character.isDigit(san.charAt(san.length() - 2))) {
            promo = san.charAt(san.length() - 1);                 // "e8Q"
            san = san.substring(0, san.length() - 1);
        }

        san = san.replace("x", "");
        if (san.length() < idx + 2) {
            return null;
        }

        String rest = san.substring(idx);
        int toFile = rest.charAt(rest.length() - 2) - 'a';
        int toRank = rest.charAt(rest.length() - 1) - '1';
        if (toFile < 0 || toFile > 7 || toRank < 0 || toRank > 7) {
            return null;
        }
        int to = toRank * 8 + toFile;

        int disFile = -1, disRank = -1;
        for (char ch : rest.substring(0, rest.length() - 2).toCharArray()) {
            if (ch >= 'a' && ch <= 'h') disFile = ch - 'a';
            else if (ch >= '1' && ch <= '8') disRank = ch - '1';
        }

        Move found = null;
        for (Move m : legal) {
            if (m.to != to) continue;
            if (m.castleKingSide || m.castleQueenSide) continue;
            if (Character.toUpperCase(b.getPiece(m.from)) != piece) continue;
            if (disFile >= 0 && m.from % 8 != disFile) continue;
            if (disRank >= 0 && m.from / 8 != disRank) continue;
            if (piece == 'P' && disFile < 0 && m.from % 8 != toFile) continue; // plain pawn push

            char mp = promoOf(m);
            if (promo != '\0') {
                if (mp != promo) continue;
            } else if (mp != '\0') {
                continue;
            }

            if (found != null) {
                return null; // ambiguous
            }
            found = m;
        }
        return found;
    }

    //  Built-in repertoire: both colours, main lines and the important side lines.
    private static final String[] LINES = {

        // ===== RUY LOPEZ =====
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 d6 c3 O-O h3 Nb8 d4 Nbd7 Nbd2 Bb7 Bc2 Re8 Nf1 Bf8 Ng3 g6",
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 d6 c3 O-O h3 Bb7 d4 Re8 Nbd2 Bf8 a4 h6 Bc2 exd4 cxd4 Nb4 Bb1 c5 d5 Nd7",
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 d6 c3 O-O h3 Na5 Bc2 c5 d4 Qc7 Nbd2 cxd4 cxd4 Nc6 Nb3 a5 Be3 a4 Nbd2 Bd7",
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 O-O c3 d5 exd5 Nxd5 Nxe5 Nxe5 Rxe5 c6 d4 Bd6 Re1 Qh4 g3 Qh3 Be3 Bg4 Qd3 Rae8 Nd2 Re6",
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Nxe4 d4 b5 Bb3 d5 dxe5 Be6 c3 Bc5 Nbd2 O-O Bc2 Bf5 Nb3 Bg4",
        "e4 e5 Nf3 Nc6 Bb5 Nf6 O-O Nxe4 d4 Nd6 Bxc6 dxc6 dxe5 Nf5 Qxd8+ Kxd8 Nc3 Ke8 h3 Bd7 b3 h6 Bb2",
        "e4 e5 Nf3 Nc6 Bb5 Nf6 d3 Bc5 c3 O-O O-O Re8 Re1 a6 Ba4 b5 Bb3 d5",
        "e4 e5 Nf3 Nc6 Bb5 a6 Bxc6 dxc6 O-O f6 d4 exd4 Nxd4 c5 Nb3 Qxd1 Rxd1 Bg4 f3 Be6",
        "e4 e5 Nf3 Nc6 Nc3 Nf6 Bb5 Bb4 O-O O-O d3 Bxc3 bxc3 d6 Bg5 Qe7 Re1 Nd8 d4",

        // ===== ITALIAN / GIUOCO PIANO / EVANS / TWO KNIGHTS =====
        "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6 d4 exd4 cxd4 Bb4+ Nc3 Nxe4 O-O Bxc3 d5 Bf6 Re1 Ne7 Rxe4 d6 Bg5 Bxg5 Nxg5 O-O",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6 d3 d6 O-O O-O Re1 a6 Bb3 Ba7 h3 h6 Nbd2 Re8 Nf1 Be6",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 d3 Nf6 O-O d6 c3 a6 Bb3 Ba7 Re1 O-O Nbd2 h6 h3 Re8 Nf1 Be6",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 b4 Bxb4 c3 Ba5 d4 exd4 O-O d6 cxd4 Bb6 Nc3 Na5 Bg5 f6",
        "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Na5 Bb5+ c6 dxc6 bxc6 Be2 h6 Nf3 e4 Ne5 Bd6 d4 exd3 Nxd3 Qc7 h3 O-O",

        // ===== SCOTCH =====
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6 Nxc6 bxc6 e5 Qe7 Qe2 Nd5 c4 Ba6 b3 g5 Bb2 Bg7",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3 Qf6 c3 Nge7 Bc4 Ne5 Be2 Qg6",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Nxc6 Qf6 Qd2 dxc6",
        "e4 e5 Nf3 Nc6 d4 exd4 Bc4 Nf6 e5 d5 Bb5 Ne4 Nxd4 Bd7 Bxc6 bxc6 O-O Be7",

        // ===== KING'S GAMBIT =====
        "e4 e5 f4 exf4 Nf3 g5 h4 g4 Ne5 Nf6 Bc4 d5 exd5 Bd6 d4 Nh5",
        "e4 e5 f4 exf4 Nf3 Be7 Bc4 Nf6 e5 Ng4 O-O O-O d4 d6 Bxf4",
        "e4 e5 f4 Bc5 Nf3 d6 c3 Nf6 d4 Bb6",
        "e4 e5 f4 d5 exd5 e4 d3 Nf6 dxe4 Nxe4 Nf3 Bc5 Qe2 Qe7",

        // ===== PONZIANI =====
        "e4 e5 Nf3 Nc6 c3 Nf6 d4 Nxe4 d5 Ne7 Nxe5 Ng6 Qd4 Nxe5 Qxe4 d6 f4",
        "e4 e5 Nf3 Nc6 c3 d5 Qa4 f6",

        // ===== FRENCH =====
        "e4 e6 d4 d5 e5 c5 c3 Nc6 Nf3 Qb6 a3 c4 Nbd2 Na5 Be2 Bd7 O-O Rc8",
        "e4 e6 d4 d5 e5 c5 c3 Nc6 Nf3 Qb6 Bd3 cxd4 cxd4 Bd7 O-O Nxd4 Nxd4 Qxd4 Nc3 Qxe5 Re1 Qb8",
        "e4 e6 d4 d5 Nc3 Bb4 e5 c5 a3 Bxc3+ bxc3 Ne7 Qg4 O-O Bd3 Nbc6 Qh5 Ng6 Nf3 Qc7",
        "e4 e6 d4 d5 Nc3 Bb4 e5 c5 a3 Bxc3+ bxc3 Ne7 Qg4 Qc7 Qxg7 Rg8 Qxh7 cxd4 Ne2 Nbc6 f4 Bd7",
        "e4 e6 d4 d5 Nd2 Nf6 e5 Nfd7 Bd3 c5 c3 Nc6 Ne2 cxd4 cxd4 f6 exf6 Nxf6 Nf3 Bd6 O-O O-O",
        "e4 e6 d4 d5 Nd2 c5 exd5 exd5 Ngf3 Nc6 Bb5 Bd6 dxc5 Bxc5 O-O Nge7 Nb3 Bd6 Nbd4 O-O",
        "e4 e6 d4 d5 Nc3 dxe4 Nxe4 Nd7 Nf3 Ngf6 Nxf6+ Nxf6 Bd3 c5 dxc5 Bxc5 Qe2 Qc7 O-O O-O Bg5 Bd7",
        "e4 e6 d4 d5 Nc3 Nf6 Bg5 Be7 e5 Nfd7 Bxe7 Qxe7 f4 O-O Nf3 c5 Qd2 Nc6 O-O-O",
        "e4 e6 d4 d5 Nc3 Nf6 Bg5 Bb4 e5 h6 Bd2 Bxc3 bxc3 Ne4 Qg4 Kf8",
        "e4 e6 d4 d5 exd5 exd5 Nf3 Nf6 Bd3 Bd6 O-O O-O Bg5 Bg4 Nbd2 Nbd7",

        // ===== CARO-KANN =====
        "e4 c6 d4 d5 e5 Bf5 Nf3 e6 Be2 c5 Be3 cxd4 Nxd4 Ne7 Nd2 Nbc6",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5 Ng3 Bg6 h4 h6 Nf3 Nd7 h5 Bh7 Bd3 Bxd3 Qxd3 e6 Bf4 Ngf6 O-O-O Be7 Kb1 O-O",
        "e4 c6 d4 d5 exd5 cxd5 c4 Nf6 Nc3 e6 Nf3 Bb4 cxd5 Nxd5 Bd2 Nc6 Bd3 Be7 O-O O-O",
        "e4 c6 d4 d5 Nd2 dxe4 Nxe4 Nd7 Nf3 Ngf6 Nxf6+ Nxf6 Ne5 Bf5 Bd3 Bxd3 Qxd3 e6",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nd7 Nf3 Ngf6 Nxf6+ Nxf6 Ne5",

        // ===== SICILIAN =====
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Be3 e5 Nb3 Be6 f3 Be7 Qd2 O-O O-O-O Nbd7 g4 b5 g5 b4 Ne2 Ne8 f4 a5",
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Bg5 e6 f4 Be7 Qf3 Qc7 O-O-O Nbd7 g4 b5 Bxf6 Nxf6 g5 Nd7 f5 Ne5",
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Bg5 e6 f4 Qb6 Qd2 Qxb2 Rb1 Qa3 e5 h6 Bh4 dxe5 fxe5 Nfd7",
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Be2 e5 Nb3 Be7 O-O O-O Be3 Be6 Qd2 Nbd7",
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Bc4 e6 Bb3 b5 O-O Be7 Qf3 Qc7",
        //Najdorf-(Fischer-Solzen Attack)
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 a6 Bc4 e6",
        //Najdorf-(Fischer-Solzen Attack-aggressive line)
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 a6 Bc4 e6 Bb3 b5 O-O Be7 Qf3 Qc7 Qg4 O-O Bh6 Ne8",
        //Fischer-Solzen main line
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 a6 Bc4 e6 a4 Be7 Qe2",
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6 Be3 Bg7 f3 O-O Qd2 Nc6 Bc4 Bd7 O-O-O Rc8 Bb3 Ne5 h4 h5 Bg5 Rc5 f4 Nc4 Bxc4 Rxc4 Bxf6 Bxf6 Nd5",
        "e4 c5 Nf3 Nc6 d4 cxd4 Nxd4 g6 c4 Bg7 Be3 Nf6 Nc3 O-O Be2 d6 O-O Bd7",
        "e4 c5 Nf3 e6 d4 cxd4 Nxd4 Nf6 Nc3 d6 Be2 Be7 O-O O-O f4 Nc6 Be3 Bd7 Qe1 Nxd4 Bxd4",
        "e4 c5 Nf3 e6 d4 cxd4 Nxd4 Nf6 Nc3 d6 g4 h6 h3 Nc6 Rg1 h5 gxh5 Nxh5 Bg5",
        "e4 c5 Nf3 Nc6 d4 cxd4 Nxd4 Nf6 Nc3 e5 Ndb5 d6 Bg5 a6 Na3 b5 Bxf6 gxf6 Nd5 f5 Bd3 Be6 Qh5 Bg7",
        "e4 c5 Nf3 e6 d4 cxd4 Nxd4 Nc6 Nc3 Qc7 Be3 a6 Be2 Nf6 O-O Be7",
        "e4 c5 Nf3 e6 d4 cxd4 Nxd4 a6 Bd3 Nc6 Nxc6 bxc6 O-O d5 c4 Nf6 cxd5 cxd5",
        "e4 c5 Nc3 Nc6 g3 g6 Bg2 Bg7 d3 d6 Be3 e6 Qd2 Nge7 Nge2 O-O",
        "e4 c5 c3 Nf6 e5 Nd5 d4 cxd4 Nf3 Nc6 cxd4 d6 Bc4 Nb6 Bb5 dxe5 Nxe5 Bd7",
        "e4 c5 c3 d5 exd5 Qxd5 d4 Nf6 Nf3 e6 Be2 cxd4 cxd4 Nc6 Nc3 Qd6 O-O Be7",
        "e4 c5 d4 cxd4 c3 dxc3 Nxc3 Nc6 Nf3 d6 Bc4 e6 O-O Nf6 Qe2 Be7 Rd1 e5",
        "e4 c5 Nf3 Nc6 Bb5 g6 Bxc6 dxc6 d3 Bg7 h3 Nf6 Nc3 Qc7",
        "e4 c5 Nf3 Nc6 Bb5 e6 O-O Nge7 Re1 a6 Bf1 d5 exd5 exd5 d4",//Anti-sicilian (Rossolimo Attack)

        // ===== RETI / ENGLISH-STYLE =====
        "Nf3 d5 c4 e6 g3 Nf6 Bg2 Be7 O-O O-O b3 c5 Bb2 Nc6 e3 b6 Nc3 Bb7",
        "Nf3 d5 c4 c6 b3 Nf6 g3 Bf5 Bg2 e6 O-O Nbd7 Bb2 Bd6",
        "Nf3 d5 g3 c5 Bg2 Nc6 O-O e5 d3 Nf6 Nbd2 Be7 e4 O-O",
        "Nf3 Nf6 c4 g6 g3 Bg7 Bg2 O-O O-O d6 d4 Nc6",

        // ===== BENKO =====
        "d4 Nf6 c4 c5 d5 b5 cxb5 a6 bxa6 Bxa6 Nc3 d6 e4 Bxf1 Kxf1 g6 g3 Bg7 Kg2 O-O Nf3 Nbd7 Re1 Qa5",

        // ===== BENONI =====
        "d4 Nf6 c4 c5 d5 e6 Nc3 exd5 cxd5 d6 e4 g6 Nf3 Bg7 Be2 O-O O-O Re8 Nd2 Na6 f3 Nc7 a4 b6 Nc4 Ba6",
        "d4 Nf6 c4 c5 d5 e6 Nc3 exd5 cxd5 d6 e4 g6 f4 Bg7 Bb5+ Nfd7 a4 O-O Nf3 Na6 O-O Nb4",

        // ===== KING'S INDIAN =====
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3 O-O Be2 e5 O-O Nc6 d5 Ne7 Ne1 Nd7 Nd3 f5 Bd2 Nf6 f3 f4 c5 g5 Rc1 Ng6 cxd6 cxd6 Nb5 Rf7",
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 f3 O-O Be3 e5 d5 Nh5 Qd2 f5 O-O-O Na6",
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Be2 O-O Bg5 c5 d5 e6 Qd2 exd5 exd5 Re8 Nf3 Bg4",
        "d4 Nf6 c4 g6 Nc3 Bg7 Nf3 O-O g3 d6 Bg2 Nbd7 O-O e5 e4 c6 h3 Qb6",

        // ===== QUEEN'S INDIAN =====
        "d4 Nf6 c4 e6 Nf3 b6 g3 Ba6 b3 Bb4+ Bd2 Be7 Bg2 c6 Bc3 d5 Ne5 Nfd7 Nxd7 Nxd7 Nd2 O-O",
        "d4 Nf6 c4 e6 Nf3 b6 a3 Bb7 Nc3 d5 cxd5 Nxd5 e3 Be7 Bb5+ c6 Bd3 Nxc3 bxc3 c5 O-O Nc6",
        "d4 Nf6 c4 e6 Nf3 b6 g3 Bb7 Bg2 Be7 O-O O-O Nc3 Ne4 Qc2 Nxc3 Qxc3 c5 Rd1 d6",

        // ===== NIMZO-INDIAN =====
        "d4 Nf6 c4 e6 Nc3 Bb4 e3 O-O Bd3 d5 Nf3 c5 O-O Nc6 a3 Bxc3 bxc3 dxc4 Bxc4 Qc7 Bd3 e5 Qc2 Re8",
        "d4 Nf6 c4 e6 Nc3 Bb4 Qc2 O-O a3 Bxc3+ Qxc3 b6 Bg5 Bb7 e3 d6 f3 Nbd7 Ne2 h6 Bh4 c5",
        "d4 Nf6 c4 e6 Nc3 Bb4 Nf3 c5 e3 Nc6 Bd3 Bxc3+ bxc3 d6 e4 e5 d5 Ne7 Nh4 h6 f4 Ng6",
        "d4 Nf6 c4 e6 Nc3 Bb4 a3 Bxc3+ bxc3 c5 e3 Nc6 Bd3 O-O Ne2 b6 e4 Ne8 O-O Ba6",

        // ===== BOGO-INDIAN =====
        "d4 Nf6 c4 e6 Nf3 Bb4+ Bd2 Qe7 g3 Nc6 Nc3 Bxc3 Bxc3 Ne4 Rc1 O-O Bg2 d6",

        // ===== LONDON =====
        "d4 d5 Bf4 Nf6 e3 c5 c3 Nc6 Nd2 e6 Ngf3 Bd6 Bg3 O-O Bd3 b6 Ne5 Bb7",
        "d4 Nf6 Nf3 d5 Bf4 e6 e3 Bd6 Bg3 O-O Nbd2 c5 c3 Nc6 Bd3 Qe7",
        "d4 d5 Bf4 c5 e3 Nc6 c3 Qb6 Qb3 c4 Qc2 Bf5 Qc1 e6 Nd2 Nf6 Ngf3 Be7",
        "d4 Nf6 Nf3 g6 Bf4 Bg7 e3 O-O h3 d6 Be2 Nbd7 O-O c5 c3",

        // ===== COLLE =====
        "d4 d5 Nf3 Nf6 e3 e6 Bd3 c5 b3 Nc6 O-O Bd6 Bb2 O-O Nbd2 Qe7 Ne5",
        "d4 d5 Nf3 Nf6 e3 e6 Bd3 Nbd7 b3 b6 Bb2 Bb7 O-O Bd6 Nbd2 O-O",

        // ===== QUEEN'S GAMBIT ACCEPTED =====
        "d4 d5 c4 dxc4 Nf3 Nf6 e3 e6 Bxc4 c5 O-O a6 a4 Nc6 Qe2 cxd4 Rd1 Be7 exd4 O-O Nc3 Nd5",
        "d4 d5 c4 dxc4 Nf3 Nf6 e3 e6 Bxc4 c5 O-O a6 Qe2 b5 Bb3 Bb7 Rd1 Nbd7 Nc3 Qb6",
        "d4 d5 c4 dxc4 e3 e5 Bxc4 exd4 exd4 Nf6 Nc3 Be7 Nf3 O-O O-O Nc6",

        // ===== QUEEN'S GAMBIT DECLINED / SLAV / SEMI-SLAV =====
        "d4 d5 c4 e6 Nc3 Nf6 Bg5 Be7 e3 O-O Nf3 h6 Bh4 b6 cxd5 Nxd5 Bxe7 Qxe7 Nxd5 exd5 Rc1 Be6 Qa4 c5 Qa3 Rc8 Bb5 a6 dxc5 bxc5 O-O Ra7 Be2 Nd7 Nd4 Qf8",
        "d4 d5 c4 e6 Nc3 Nf6 cxd5 exd5 Bg5 c6 Qc2 Be7 e3 Nbd7 Bd3 O-O Nge2 Re8 O-O Nf8",
        "d4 d5 c4 e6 Nc3 Nf6 Bg5 Be7 e3 O-O Nf3 Nbd7 Rc1 c6 Bd3 dxc4 Bxc4 Nd5 Bxe7 Qxe7 O-O Nxc3 Rxc3 e5",
        "d4 d5 c4 e6 Nc3 Nf6 Nf3 c6 e3 Nbd7 Bd3 dxc4 Bxc4 b5 Bd3 a6 e4 c5 e5 cxd4",
        "d4 d5 c4 c6 Nf3 Nf6 Nc3 e6 Bg5 dxc4 e4 b5 e5 h6 Bh4 g5 Nxg5 hxg5 Bxg5 Nbd7",
        "d4 d5 c4 c6 Nf3 Nf6 Nc3 dxc4 a4 Bf5 e3 e6 Bxc4 Bb4 O-O Nbd7 Qe2 Bg6 e4 O-O"
    };

    // ------------------------------------------------------------------
    //  Additional lines (all validated against the move generator; see validate()).
    // ------------------------------------------------------------------
    private static final String[] LINES_EXTRA = {

        // Ruy Lopez, Closed, Chigorin
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 d6 c3 O-O h3 Na5 Bc2 c5 d4 Qc7 Nbd2 cxd4 cxd4 Nc6",
        // Ruy Lopez, Closed, Breyer
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 d6 c3 O-O h3 Nb8 d4 Nbd7 Nbd2 Bb7 Bc2 Re8 Nf1 Bf8 Ng3 g6",
        // Ruy Lopez, Closed, Zaitsev
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 d6 c3 O-O h3 Bb7 d4 Re8 Nbd2 Bf8 a4 h6 Bc2 exd4 cxd4 Nb4",
        // Ruy Lopez, Marshall Attack
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 O-O c3 d5 exd5 Nxd5 Nxe5 Nxe5 Rxe5 c6 d4 Bd6 Re1 Qh4 g3 Qh3 Be3 Bg4 Qd3 Rae8",
        // Ruy Lopez, Marshall, Anti
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 O-O h3 Bb7 d3 d6 a3 Na5 Ba2 c5 Nbd2 Nc6",
        // Ruy Lopez, Open
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Nxe4 d4 b5 Bb3 d5 dxe5 Be6 c3 Bc5 Nbd2 O-O Bc2 Bf5 Nb3 Bg4",
        // Ruy Lopez, Open, Be3
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Nxe4 d4 b5 Bb3 d5 dxe5 Be6 Be3 Nc5 c3 Nxb3 axb3 Be7 Nbd2 O-O",
        // Ruy Lopez, Berlin
        "e4 e5 Nf3 Nc6 Bb5 Nf6 O-O Nxe4 d4 Nd6 Bxc6 dxc6 dxe5 Nf5 Qxd8+ Kxd8 Nc3 Ke8 h3 h5 Ne2 Ne7 Nf4",
        // Ruy Lopez, Berlin, Be7
        "e4 e5 Nf3 Nc6 Bb5 Nf6 O-O Nxe4 d4 Nd6 Bxc6 dxc6 dxe5 Nf5 Qxd8+ Kxd8 Nc3 Ke8 h3 Be7 Ne2 h5",
        // Ruy Lopez, Berlin, 4.d3
        "e4 e5 Nf3 Nc6 Bb5 Nf6 d3 Bc5 c3 O-O O-O Re8 Re1 Bb6 Nbd2 d6 Nf1 Ne7",
        // Ruy Lopez, Exchange
        "e4 e5 Nf3 Nc6 Bb5 a6 Bxc6 dxc6 O-O f6 d4 exd4 Nxd4 c5 Nb3 Qxd1 Rxd1 Bg4 f3 Be6 Nc3 Bd6",
        // Ruy Lopez, Exchange, 5.Nxe5
        "e4 e5 Nf3 Nc6 Bb5 a6 Bxc6 dxc6 Nxe5 Qd4 Nf3 Qxe4+ Qe2 Qxe2+",
        // Ruy Lopez, Schliemann
        "e4 e5 Nf3 Nc6 Bb5 f5 Nc3 fxe4 Nxe4 d5 Nxe5 dxe4 Nxc6 Qg5 Qe2 Nf6",
        // Ruy Lopez, Archangelsk
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O b5 Bb3 Bb7 Re1 Bc5 c3 d6 d4 Bb6 Bg5 h6 Bh4 Qd7",
        // Ruy Lopez, Anti-Marshall
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 d3 b5 Bb3 d6 a4 Bg4 Be3 Na5 Bxf7+ Kxf7 Ng5+ Ke8",
        // Ruy Lopez, Steinitz Deferred
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 d6 c3 Bd7 d4 Nge7 Bb3 h6 Nbd2 Ng6 O-O Be7",
        // Italian, Evans Gambit
        "e4 e5 Nf3 Nc6 Bc4 Bc5 b4 Bxb4 c3 Ba5 d4 exd4 O-O d6 cxd4 Bb6 Nc3 Na5 Bg5 f6 Bf4 Ne7",
        // Italian, Evans Gambit, 7...Nge7
        "e4 e5 Nf3 Nc6 Bc4 Bc5 b4 Bxb4 c3 Ba5 d4 exd4 O-O Nge7",
        // Italian, Giuoco Pianissimo
        "e4 e5 Nf3 Nc6 Bc4 Bc5 d3 Nf6 O-O d6 c3 a6 Bb3 Ba7 Re1 O-O Nbd2 Be6 Bxe6 fxe6 Qe2 Qe8",
        // Italian, Giuoco Pianissimo, Main
        "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6 d3 d6 O-O a6 Re1 Ba7 h3 h6 Nbd2 O-O Nf1 Re8 Ng3 Be6",
        // Italian, Giuoco Piano, Main
        "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6 d4 exd4 cxd4 Bb4+ Nc3 Nxe4 O-O Bxc3 d5 Bf6 Re1 Ne7 Rxe4 d6 Bg5 Bxg5 Nxg5 h6 Qe2 hxg5 Re1 Be6",
        // Italian, Giuoco Piano, 7.Bd2
        "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6 d4 exd4 cxd4 Bb4+ Bd2 Bxd2+ Nbxd2 d5 exd5 Nxd5 Qb3 Nce7 O-O O-O",
        // Italian, Two Knights, 4.Ng5
        "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Na5 Bb5+ c6 dxc6 bxc6 Be2 h6 Nf3 e4 Ne5 Bd6 d4 exd3 Nxd3 Qc7",
        // Italian, Two Knights, Fried Liver
        "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Nxd5 Nxf7 Kxf7 Qf3+ Ke6 Nc3 Nb4 Qe4 c6 a3 Na6 d4 Nac7",
        //Italian, Two Knights, Fried Liver, Traxler Counterattack(tricky line)
        "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 Bc5 Nxf7 Bxf2+ Kf1 Qe7 Nxh8 d5 exd5 Ne4",
        //Italian, Two Knights, Fried Liver, Traxler Counterattack(main line)
        "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 Bc5 Nxf7 Bxf2+ Kxf2 Nxe4+ Ke4 Qh5",
        //Italian, Two Knights, Fried Liver, Traxler Counterattack(main line for white advantage)
        "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 Bc5 Bxf7+ Ke7 Bc4 Rf8 Nc3 h6 Nf3 d6 O-O Qe7 Be2 a5",
        // Italian, Two Knights, 5...Nd4
        "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Nd4 c3 b5 Bf1 Nxd5 Ne4 Qh4 Ng3 Bb7 d3 h6",
        // Italian, Two Knights, 4.d3
        "e4 e5 Nf3 Nc6 Bc4 Nf6 d3 Be7 O-O O-O Re1 d6 c3 a6 Bb3 Na5 Bc2 c5 Nbd2 Nc6",
        // Italian, Hungarian
        "e4 e5 Nf3 Nc6 Bc4 Be7 d4 exd4 Nxd4 Nf6 Nc3 O-O O-O d5 exd5 Nxd5",
        // Scotch, Classical
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3 Qf6 c3 Nge7 Bc4 Ne5 Be2 Qg6 O-O",
        // Scotch, Classical, 5.Nb3
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Nb3 Bb6 a4 a6 Nc3 Nf6 Qe2 d6 a5 Ba7 Be3 O-O",
        // Scotch, Mieses
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6 Nxc6 bxc6 e5 Qe7 Qe2 Nd5 c4 Ba6 b3 g6 Bb2 Bg7 Nd2 O-O g3 Rfe8",
        // Scotch, Mieses, 7.Qe2
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6 Nxc6 bxc6 e5 Qe7 Qe2 Nd5 c4 Nb6 Nc3 Qe6 b3 a5 Bb2 a4",
        // Scotch, Four Knights
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6 Nc3 Bb4 Nxc6 bxc6 Bd3 d5 exd5 cxd5 O-O O-O Bg5 c6 Qf3 Be7 Rae1 Re8",
        // Scotch, Four Knights, 5.Nc3
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6 Nc3 Bb4 Nxc6 bxc6 Bd3 d5 exd5 cxd5 O-O O-O Bg5 Be6 Na4 Bd6",
        // Scotch Gambit
        "e4 e5 Nf3 Nc6 d4 exd4 Bc4 Nf6 e5 d5 Bb5 Ne4 Nxd4 Bd7 Bxc6 bxc6 O-O Be7 f3 Ng5 f4 Ne6",
        // Scotch Gambit, 4...Bc5
        "e4 e5 Nf3 Nc6 d4 exd4 Bc4 Bc5 c3 Nf6 e5 d5 Bb5 Ne4 cxd4 Bb6 Nc3 O-O",
        // Scotch, Schmidt
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6 Nxc6 bxc6 Nc3 Bb4 Bd3 d5 exd5 cxd5 O-O O-O Bg5 c6",
        // KG Accepted, Kieseritzky
        "e4 e5 f4 exf4 Nf3 g5 h4 g4 Ne5 Nf6 Bc4 d5 exd5 Bd6 d4 Nh5 O-O Qe7 Nc3 Nd7",
        // KG Accepted, Kieseritzky, Berlin
        "e4 e5 f4 exf4 Nf3 g5 h4 g4 Ne5 Nf6 Nxg4 Nxe4 d3 Ng5 Bxf4 Nxf3+ Qxf3 Qe7",
        // KG Accepted, Classical
        "e4 e5 f4 exf4 Nf3 g5 Bc4 Bg7 O-O d6 d4 h6 c3 Nc6 Qb3 Qe7 Nbd2 Nf6",
        // KG Accepted, Muzio
        "e4 e5 f4 exf4 Nf3 g5 Bc4 g4 O-O gxf3 Qxf3 Qf6 e5 Qxe5 d3 Bh6 Nc3 Ne7 Bd2 Nbc6",
        // KG Accepted, Fischer
        "e4 e5 f4 exf4 Nf3 d6 d4 g5 h4 g4 Ng1 Bg7 Nc3 h5 Bd3 Nc6 Nge2 Qf6",
        // KG Accepted, Modern
        "e4 e5 f4 exf4 Nf3 d5 exd5 Nf6 Bb5+ c6 dxc6 bxc6 Bc4 Bd6 O-O O-O d4 Nxd5",
        // KG Accepted, Cunningham
        "e4 e5 f4 exf4 Nf3 Be7 Bc4 Nf6 e5 Ne4 Bxf7+ Kxf7 Nc3 Nxc3 dxc3 Nc6",
        // KG Declined, Falkbeer
        "e4 e5 f4 d5 exd5 e4 d3 Nf6 dxe4 Nxe4 Nf3 Bc5 Qe2 Qe7 Nc3 Nxc3",
        // KG Declined, Falkbeer, 4.d3
        "e4 e5 f4 d5 exd5 e4 d3 Nf6 dxe4 Nxe4 Qe2 Qe7 Nd2 Nxd2 Bxd2 Nd7",
        // KG Declined, Classical
        "e4 e5 f4 Bc5 Nf3 d6 c3 Nf6 d4 exd4 cxd4 Bb4+ Nc3 Nxe4 Bd3 Nxc3",
        // KG Declined, 2...Nf6
        "e4 e5 f4 Nf6 fxe5 Nxe4 Nf3 Be7 Qe2 Nc5 d4 Ne6 Nc3 O-O",
        // Ponziani, 3...d5
        "e4 e5 Nf3 Nc6 c3 d5 Qa4 f6 exd5 Qxd5 d4 Bg4 Be2 O-O-O Na3 exd4 Nb5 Bc5",
        // Ponziani, 3...d5 4.Qa4 Bd7
        "e4 e5 Nf3 Nc6 c3 d5 Qa4 Bd7 exd5 Nd4 cxd4 Bb5 Qxb5+",
        // Ponziani, 3...Nf6
        "e4 e5 Nf3 Nc6 c3 Nf6 d4 Nxe4 d5 Ne7 Nxe5 Ng6 Nxg6 hxg6 Bd3 Nf6 O-O Bc5",
        // Ponziani, Steinitz
        "e4 e5 Nf3 Nc6 c3 Nf6 d4 exd4 e5 Nd5 cxd4 d6 Bc4 Nb6 Bb5 dxe5 Nxe5 Bd7",
        // Ponziani, 3...d5 4.Qa4 Nf6
        "e4 e5 Nf3 Nc6 c3 d5 Qa4 Nf6 Nxe5 Bd6 Nxc6 bxc6 d3 O-O Be2 Nd7 Nd2 Bc5",
        // French, Classical
        "e4 e6 d4 d5 Nc3 Nf6 Bg5 Be7 e5 Nfd7 Bxe7 Qxe7 f4 O-O Nf3 c5 Qd2 Nc6 O-O-O a6 dxc5 Nxc5 Kb1 b5",
        // French, Steinitz
        "e4 e6 d4 d5 Nc3 Nf6 e5 Nfd7 f4 c5 Nf3 Nc6 Be3 cxd4 Nxd4 Bc5 Qd2 O-O O-O-O a6",
        // French, McCutcheon
        "e4 e6 d4 d5 Nc3 Nf6 Bg5 Bb4 e5 h6 Bd2 Bxc3 bxc3 Ne4 Qg4 Kf8 Bd3 Nxd2 Kxd2 c5",
        // French, Burn
        "e4 e6 d4 d5 Nc3 Nf6 Bg5 dxe4 Nxe4 Be7 Bxf6 gxf6 Nf3 f5 Nc3 Nc6 Bb5 Bd7 Qe2",
        // French, Winawer, Poisoned Pawn
        "e4 e6 d4 d5 Nc3 Bb4 e5 c5 a3 Bxc3+ bxc3 Ne7 Qg4 Qc7 Qxg7 Rg8 Qxh7 cxd4 Ne2 Nbc6 f4 Bd7 Qd3 dxc3 Qxc3 Qa5",
        // French, Winawer, 7.Qg4 O-O
        "e4 e6 d4 d5 Nc3 Bb4 e5 c5 a3 Bxc3+ bxc3 Ne7 Qg4 O-O Bd3 Nbc6 Qh5 Ng6 Nf3 Qc7 Bd2 c4",
        // French, Winawer, 7.a4
        "e4 e6 d4 d5 Nc3 Bb4 e5 c5 a3 Bxc3+ bxc3 Qc7 Qg4 f5 Qg3 cxd4 Ne2 Nc6 f4 dxc3",
        // French, Winawer, 4.exd5
        "e4 e6 d4 d5 Nc3 Bb4 exd5 exd5 Bd3 Nc6 Ne2 Nge7 O-O Bg4 f3 Bh5",
        // French, Tarrasch, Open
        "e4 e6 d4 d5 Nd2 c5 exd5 exd5 Ngf3 Nc6 Bb5 Bd6 dxc5 Bxc5 O-O Nge7 Nb3 Bd6 Nbd4 O-O Bg5 Qd7",
        // French, Tarrasch, Closed
        "e4 e6 d4 d5 Nd2 Nf6 e5 Nfd7 Bd3 c5 c3 Nc6 Ne2 cxd4 cxd4 f6 exf6 Nxf6 O-O Bd6 Nf3 O-O",
        // French, Tarrasch, 3...Nf6
        "e4 e6 d4 d5 Nd2 Nf6 e5 Nfd7 c3 c5 Bd3 Nc6 Ne2 cxd4 cxd4 f6 exf6 Nxf6 Nf3 Bd6",
        // French, Advance
        "e4 e6 d4 d5 e5 c5 c3 Nc6 Nf3 Qb6 a3 c4 Nbd2 Na5 b4 cxb3 Nxb3 Nxb3 Qxb3 Bd7 Be2 Ne7",
        // French, Advance, 5...Bd7
        "e4 e6 d4 d5 e5 c5 c3 Nc6 Nf3 Bd7 Be2 Nge7 O-O Nf5 Na3 cxd4 cxd4 Qb6 Nc2",
        // French, Advance, 4...Qb6
        "e4 e6 d4 d5 e5 c5 c3 Qb6 Nf3 Nc6 Bd3 cxd4 cxd4 Bd7 O-O Nxd4 Nxd4 Qxd4 Nc3 Bc5",
        // French, Exchange
        "e4 e6 d4 d5 exd5 exd5 Nf3 Bd6 Bd3 Nf6 O-O O-O Bg5 Bg4 Nbd2 Nbd7 c3 c6 Qc2 Qc7",
        // French, King's Indian Attack
        "e4 e6 d3 d5 Nd2 Nf6 Ngf3 c5 g3 Nc6 Bg2 Be7 O-O O-O Re1 b5 e5 Nd7 Nf1 a5",
        // Caro-Kann, Classical
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5 Ng3 Bg6 h4 h6 Nf3 Nd7 h5 Bh7 Bd3 Bxd3 Qxd3 e6 Bf4 Ngf6 O-O-O Be7 Kb1 O-O Ne4 Nxe4 Qxe4 Nf6",
        // Caro-Kann, Classical, 6.Nf3
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5 Ng3 Bg6 Bc4 e6 N1e2 Nf6 Nf4 Bd6 Nxg6 hxg6 O-O Nbd7",
        // Caro-Kann, Bronstein-Larsen
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nd7 Ng5 Ngf6 Bd3 e6 N1f3 Bd6 Qe2 h6 Ne4 Nxe4 Qxe4 Qc7 O-O",
        // Caro-Kann, Smyslov
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Nxf6+ exf6 Bc4 Bd6 Qe2+ Qe7 Bd2 Nd7 O-O-O Nb6 Bb3 Be6",
        // Caro-Kann, Advance
        "e4 c6 d4 d5 e5 Bf5 Nf3 e6 Be2 c5 Be3 cxd4 Nxd4 Nc6 Nd2 Nge7",
        // Caro-Kann, Advance, Short
        "e4 c6 d4 d5 e5 Bf5 Nf3 e6 Be2 Nd7 O-O Ne7 Nbd2 h6 Nb3",
        // Caro-Kann, Advance, 4.h4
        "e4 c6 d4 d5 e5 Bf5 h4 h6 g4 Bd7 Ne2 e6 Nf4 c5 c3 Nc6 Be3",
        // Caro-Kann, Advance, 4.Nc3
        "e4 c6 d4 d5 e5 Bf5 Nc3 e6 g4 Bg6 Nge2 c5 h4 h5 Nf4 Bh7 Bb5+ Nc6 Be3 cxd4 Bxd4 Bb4",
        // Caro-Kann, Exchange / Panov
        "e4 c6 d4 d5 exd5 cxd5 c4 Nf6 Nc3 e6 Nf3 Be7 cxd5 Nxd5 Bd3 Nc6 O-O O-O Qe2 Nf6 Bg5 b6",
        // Caro-Kann, Panov, 5...g6
        "e4 c6 d4 d5 exd5 cxd5 c4 Nf6 Nc3 g6 Qb3 Bg7 cxd5 O-O Nf3 Nbd7 Be2 Nxd5",
        // Caro-Kann, Exchange
        "e4 c6 d4 d5 exd5 cxd5 Bd3 Nc6 c3 Nf6 Bf4 Bg4 Qb3 Qd7 Nd2 e6 Ngf3 Bd6",
        // Caro-Kann, Fantasy
        "e4 c6 d4 d5 f3 dxe4 fxe4 e5 Nf3 exd4 Bc4 Nf6 Nxd4 Bc5 Be3 O-O Qd2 Na6",
        // Caro-Kann, Two Knights
        "e4 c6 Nc3 d5 Nf3 Bg4 h3 Bxf3 Qxf3 e6 d3 Nf6 g3 Nbd7 Bg2 Bc5 O-O O-O",
        // Sicilian, Najdorf, English Attack
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Be3 e5 Nb3 Be6 f3 Be7 Qd2 O-O O-O-O Nbd7 g4 b5 g5 b4 Ne2 Ne8 f4 a5",
        // Sicilian, Najdorf, 6.Bg5
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Bg5 e6 f4 Be7 Qf3 Qc7 O-O-O Nbd7 g4 b5 Bxf6 Nxf6 g5 Nd7 f5 Ne5",
        // Sicilian, Najdorf, 6.Bg5 Qb6
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Bg5 e6 f4 Qb6 Qd2 Qxb2 Rb1 Qa3 e5 h6 Bh4 dxe5 fxe5 Nfd7",
        // Sicilian, Najdorf, 6.Bc4
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Bc4 e6 Bb3 b5 O-O Be7 Qf3 Qc7 Qg3 O-O Bh6 Ne8",
        // Sicilian, Najdorf, 6.h3
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 h3 e5 Nde2 h5 Ng3 g6 Bg5 Nbd7 Bc4 Nb6",
        // Sicilian, Najdorf, 6.Be2
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Be2 e5 Nb3 Be7 O-O O-O Be3 Be6 Qd2 Nbd7 a4 Rc8",
        // Sicilian, Najdorf, 6.f3
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 f3 e5 Nb3 Be6 Be3 Nbd7 Qd2 b5 g4 h6 O-O-O Nb6",
        // Sicilian, Najdorf, 6.Bg5 e6 7.f4 Nbd7
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Bg5 e6 f4 Be7 Qf3 Qc7 O-O-O Nbd7 g4 b5 Bxf6 Nxf6 g5 Nd7 f5 Ne5 Qh3",
        // Sicilian, Dragon, Yugoslav
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6 Be3 Bg7 f3 O-O Qd2 Nc6 Bc4 Bd7 O-O-O Rc8 Bb3 Ne5 h4 h5 Bg5 Rc5 f4 Nc4 Bxc4 Rxc4 Kb1 Qa5",
        // Sicilian, Dragon, Soltis
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6 Be3 Bg7 f3 O-O Qd2 Nc6 O-O-O d5 exd5 Nxd5 Nxc6 bxc6 Bd4 e5 Bc5 Re8",
        // Sicilian, Dragon, Classical
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6 Be2 Bg7 Be3 Nc6 O-O O-O Nb3 Be6 f4 Qc8 Kh1 Rd8",
        // Sicilian, Dragon, Levenfish
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6 f4 Bg7 e5 dxe5 fxe5 Ng4 Bb5+ Bd7 e6 fxe6",
        // Sicilian, Scheveningen, Classical
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 e6 Be2 a6 O-O Be7 f4 O-O Kh1 Qc7 a4 Nc6 Be3 Re8",
        // Sicilian, Scheveningen, English Attack
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 e6 Be3 a6 f3 b5 Qd2 Nbd7 g4 h6 O-O-O Bb7 h4 b4 Nb3 Rc8",
        // Sicilian, Scheveningen, Keres
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 e6 g4 h6 h3 Nc6 Rg1 h5 gxh5 Nxh5 Bg5 Qb6",
        // Sicilian, Sveshnikov
        "e4 c5 Nf3 Nc6 d4 cxd4 Nxd4 Nf6 Nc3 e5 Ndb5 d6 Bg5 a6 Na3 b5 Nd5 Be7 Bxf6 Bxf6 c3 O-O Nc2 Bg5 a4 bxa4 Rxa4 a5 Bc4 Rb8",
        // Sicilian, Sveshnikov, 9.Bxf6
        "e4 c5 Nf3 Nc6 d4 cxd4 Nxd4 Nf6 Nc3 e5 Ndb5 d6 Bg5 a6 Na3 b5 Bxf6 gxf6 Nd5 f5 Bd3 Be6",
        // Sicilian, Taimanov
        "e4 c5 Nf3 e6 d4 cxd4 Nxd4 Nc6 Nc3 Qc7 Be3 a6 Qd2 Nf6 O-O-O Bb4 f3 Ne5 Nb3 b5 Bd4 Nc6",
        // Sicilian, Taimanov, 5.Nb5
        "e4 c5 Nf3 e6 d4 cxd4 Nxd4 Nc6 Nb5 d6 c4 Nf6 N1c3 a6 Na3 Be7 Be2 O-O O-O b6",
        // Sicilian, Kan, Bd3
        "e4 c5 Nf3 e6 d4 cxd4 Nxd4 a6 Bd3 Bc5 Nb3 Ba7 Qe2 Nc6 Nc3 Nge7 Bf4 O-O O-O-O",
        // Sicilian, Kan, Maroczy
        "e4 c5 Nf3 e6 d4 cxd4 Nxd4 a6 c4 Nf6 Nc3 Bb4 Bd3 Nc6 Nxc6 dxc6",
        // Sicilian, Kan, 5.Nc3
        "e4 c5 Nf3 e6 d4 cxd4 Nxd4 a6 Nc3 Qc7 Be2 Nf6 O-O Nc6 Be3 Bb4 Na4 O-O",
        // Sicilian, Alapin
        "e4 c5 c3 Nf6 e5 Nd5 d4 cxd4 Nf3 Nc6 cxd4 d6 Bc4 Nb6 Bb5 dxe5 Nxe5 Bd7 Nxd7 Qxd7",
        // Sicilian, Alapin, 2...d5
        "e4 c5 c3 d5 exd5 Qxd5 d4 Nf6 Nf3 Bg4 Be2 e6 O-O Nc6 Be3 cxd4 cxd4 Bb4 Nbd2",
        // Sicilian, Closed
        "e4 c5 Nc3 Nc6 g3 g6 Bg2 Bg7 d3 d6 Be3 e6 Qd2 Nge7 Nge2 O-O O-O Nd4",
        // Sicilian, Smith-Morra
        "e4 c5 d4 cxd4 c3 dxc3 Nxc3 Nc6 Nf3 d6 Bc4 e6 O-O Nf6 Qe2 Be7 Rd1 e5 h3 O-O",
        // Sicilian, Smith-Morra, 5...e6
        "e4 c5 d4 cxd4 c3 dxc3 Nxc3 Nc6 Nf3 e6 Bc4 a6 O-O Nge7 Qe2 Ng6 Rd1 Be7",
        // Sicilian, Rossolimo
        "e4 c5 Nf3 Nc6 Bb5 g6 Bxc6 dxc6 d3 Bg7 h3 Nf6 Nc3 Qc7 Be3 b6 Qd2 e5 Bh6 O-O",
        // Sicilian, Rossolimo, 3...e6
        "e4 c5 Nf3 Nc6 Bb5 e6 O-O Nge7 Re1 a6 Bf1 d5 exd5 exd5 d4",
        // Sicilian, Rossolimo, 3...d6
        "e4 c5 Nf3 Nc6 Bb5 d6 O-O Bd7 Re1 Nf6 c3 a6 Bf1 e5 d4 cxd4 cxd4 exd4 Nxd4",
        // Reti, Classical
        "Nf3 d5 c4 e6 g3 Nf6 Bg2 Be7 O-O O-O b3 c5 Bb2 Nc6 e3 b6 Nc3 Bb7 Ne5 Nxe5",
        // Reti, 2...d4
        "Nf3 d5 c4 d4 e3 Nc6 exd4 Nxd4 Nxd4 Qxd4 Nc3 e5 d3 Nf6 Be3 Qd8 Be2 Bb4",
        // Reti, 2...dxc4
        "Nf3 d5 c4 dxc4 e3 Nf6 Bxc4 e6 O-O c5 b3 Nc6 Bb2 a6 a4 Be7 d4 cxd4 exd4 O-O",
        // Reti, 2...c6
        "Nf3 d5 c4 c6 b3 Bf5 g3 Nf6 Bg2 e6 O-O Nbd7 Bb2 Be7 d3 O-O Nbd2 h6 e4 Bh7",
        // Reti, King's Indian Attack
        "Nf3 d5 g3 Nf6 Bg2 e6 O-O Be7 d3 O-O Nbd2 c5 e4 Nc6 Re1 b5 e5 Nd7 Nf1 b4",
        // Reti, King's Indian Reversed
        "Nf3 Nf6 c4 g6 g3 Bg7 Bg2 O-O O-O d6 Nc3 Nc6 d3 a6 Rb1 Rb8 a3 b5 cxb5 axb5 b4 Bd7",
        // Reti, 1...Nf6 2.c4 e6
        "Nf3 Nf6 c4 e6 g3 d5 Bg2 Be7 O-O O-O b3 b6 Bb2 Bb7 e3 Nbd7 Nc3 c5",
        // Benko Gambit, Accepted
        "d4 Nf6 c4 c5 d5 b5 cxb5 a6 bxa6 Bxa6 Nc3 g6 e4 Bxf1 Kxf1 d6 Nf3 Bg7 g3 O-O Kg2 Nbd7 Re1 Qa5 Qc2 Rfb8",
        // Benko Gambit, Fianchetto
        "d4 Nf6 c4 c5 d5 b5 cxb5 a6 bxa6 Bxa6 Nc3 g6 Nf3 d6 g3 Bg7 Bg2 O-O O-O Nbd7 Re1 Qa5 h3 Rfb8",
        // Benko Gambit, Fully Accepted
        "d4 Nf6 c4 c5 d5 b5 cxb5 a6 bxa6 g6 Nc3 Bxa6 e4 Bxf1 Kxf1 d6 g3 Bg7 Kg2 O-O Nf3 Nbd7 Re1 Nb6",
        // Benko Gambit, 5.e3
        "d4 Nf6 c4 c5 d5 b5 cxb5 a6 e3 Bb7 Nc3 axb5 Bxb5 g6 Nge2",
        // Benko Gambit, 4.Nf3
        "d4 Nf6 c4 c5 d5 b5 Nf3 g6 Nc3 bxc4 e4 d6 Bxc4 Bg7 O-O O-O Re1 Nbd7",
        // Benko Gambit, Declined
        "d4 Nf6 c4 c5 d5 b5 Nf3 e6 Bg5 exd5 cxd5 h6 Bxf6 Qxf6 Nc3 Bb7 e4 Bxd5",
        // Benoni, Classical
        "d4 Nf6 c4 c5 d5 e6 Nc3 exd5 cxd5 d6 e4 g6 Nf3 Bg7 Be2 O-O O-O Re8 Nd2 Na6 f3 Nc7 a4 b6 Nc4 Ba6 Bg5 h6",
        // Benoni, Classical, 9.Nd2
        "d4 Nf6 c4 c5 d5 e6 Nc3 exd5 cxd5 d6 e4 g6 Nf3 Bg7 Be2 O-O Nd2 Nbd7 O-O Re8 Qc2 Nb6 a4 Nh5",
        // Benoni, Modern
        "d4 Nf6 c4 c5 d5 e6 Nc3 exd5 cxd5 d6 e4 g6 Nf3 Bg7 h3 O-O Bd3 b5 Nxb5 Nxe4 Bxe4 Qa5+ Nc3 Qxb5",
        // Benoni, Taimanov
        "d4 Nf6 c4 c5 d5 e6 Nc3 exd5 cxd5 d6 e4 g6 f4 Bg7 Bb5+ Nfd7 a4 O-O Nf3 Na6 O-O Nb4 Re1 Re8",
        // Benoni, Fianchetto
        "d4 Nf6 c4 c5 d5 e6 Nc3 exd5 cxd5 d6 Nf3 g6 g3 Bg7 Bg2 O-O O-O Na6 Bf4 Nc7 a4 b6 Re1 Re8",
        // Benoni, Czech
        "d4 Nf6 c4 c5 d5 e5 Nc3 d6 e4 Be7 Nf3 O-O Be2 Nbd7 O-O Ne8 Ne1 g6",
        // Benoni, Four Pawns
        "d4 Nf6 c4 c5 d5 e6 Nc3 exd5 cxd5 d6 e4 g6 f4 Bg7 Nf3 O-O Be2 Re8 O-O Nbd7 Qc2 Ne4 Nxe4 Rxe4",
        // KID, Classical, Mar del Plata
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3 O-O Be2 e5 O-O Nc6 d5 Ne7 Ne1 Nd7 Be3 f5 f3 f4 Bf2 g5 a4 Ng6 Nd3 Nf6 c5 Rf7",
        // KID, Classical, 7...Nbd7
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3 O-O Be2 e5 O-O Nbd7 Be3 Ng4 Bg5 Qe8 dxe5 dxe5 Nd5 Qd8",
        // KID, Classical, 7...exd4
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3 O-O Be2 e5 O-O exd4 Nxd4 Re8 f3 c6 Kh1 d5 cxd5 cxd5 e5 Nfd7",
        // KID, Classical, 9.Nd2
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3 O-O Be2 e5 O-O Nc6 d5 Ne7 Nd2 a5 a3 Nd7 b3 f5 f4 Kh8",
        // KID, Saemisch
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 f3 O-O Be3 Nc6 Nge2 a6 Qd2 Rb8 Nc1 e5 d5 Nd4 Bxd4 exd4 Nb1 c5",
        // KID, Saemisch, 6...e5
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 f3 O-O Be3 e5 d5 Nh5 Qd2 Qh4+ g3 Nxg3 Qf2 Nxf1 Qxh4 Nxe3 Ke2 Nxc4",
        // KID, Four Pawns
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 f4 O-O Nf3 c5 d5 e6 Be2 exd5 cxd5 Bg4 O-O Nbd7 h3 Bxf3 Bxf3 Ne5",
        // KID, Averbakh
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Be2 O-O Bg5 c5 d5 e6 Qd2 exd5 exd5 Re8 Nf3 Bg4 O-O Nbd7 h3 Bxf3",
        // KID, Averbakh, 6...Na6
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Be2 O-O Bg5 Na6 Qd2 e5 d5 Nc5 f3 a5 Nh3 h6",
        // KID, Fianchetto
        "d4 Nf6 c4 g6 Nf3 Bg7 g3 O-O Bg2 d6 O-O Nbd7 Nc3 e5 e4 c6 h3 Qb6 Re1 Re8 Be3 Qc7",
        // KID, Fianchetto, 6...Nc6
        "d4 Nf6 c4 g6 g3 Bg7 Bg2 O-O Nc3 d6 Nf3 Nc6 O-O a6 h3 Rb8 e4 b5 cxb5 axb5",
        // KID, Petrosian
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3 O-O Be2 e5 d5 a5 Bg5 h6 Bh4 Na6 Nd2 Qe8 O-O Nh7",
        // QID, Fianchetto
        "d4 Nf6 c4 e6 Nf3 b6 g3 Ba6 b3 Bb4+ Bd2 Be7 Bg2 c6 Bc3 d5 Ne5 Nfd7 Nxd7 Nxd7 Nd2 O-O O-O Rc8 e4 dxe4 Nxe4",
        // QID, Fianchetto, 4...Bb7
        "d4 Nf6 c4 e6 Nf3 b6 g3 Bb7 Bg2 Be7 O-O O-O Nc3 Ne4 Qc2 Nxc3 Qxc3 Bf6 Rd1 c5 dxc5 bxc5",
        // QID, Petrosian
        "d4 Nf6 c4 e6 Nf3 b6 a3 Bb7 Nc3 d5 cxd5 Nxd5 e3 Be7 Bb5+ c6 Bd3 Nxc3 bxc3 c5 O-O Nc6 e4 cxd4",
        // QID, 4.e3
        "d4 Nf6 c4 e6 Nf3 b6 e3 Bb7 Bd3 d5 O-O Bd6 b3 O-O Bb2 Nbd7 Nbd2 Qe7 Qe2 a6",
        // QID, Kasparov
        "d4 Nf6 c4 e6 Nf3 b6 a3 Ba6 Qc2 Bb7 Nc3 c5 e4 cxd4 Nxd4 Bc5 Nb3 Be7 Bf4 d6",
        // QID, Capablanca
        "d4 Nf6 c4 e6 Nf3 b6 a3 Bb7 Nc3 d5 Bg5 Be7 e3 h6 Bh4 O-O Be2 Ne4 Bxe7 Qxe7 Nxe4 dxe4 Nd2",
        // Nimzo, Rubinstein
        "d4 Nf6 c4 e6 Nc3 Bb4 e3 O-O Bd3 d5 Nf3 c5 O-O Nc6 a3 Bxc3 bxc3 dxc4 Bxc4 Qc7 Bd3 e5 Qc2 Re8 Rd1 e4",
        // Nimzo, Rubinstein, 7...cxd4
        "d4 Nf6 c4 e6 Nc3 Bb4 e3 O-O Bd3 d5 Nf3 c5 O-O cxd4 exd4 dxc4 Bxc4 b6 Bg5 Bb7 Re1 Nbd7",
        // Nimzo, Rubinstein, Hubner
        "d4 Nf6 c4 e6 Nc3 Bb4 e3 c5 Bd3 Nc6 Nf3 Bxc3+ bxc3 d6 e4 e5 d5 Ne7 Nh4 h6 f4 Ng6",
        // Nimzo, Classical
        "d4 Nf6 c4 e6 Nc3 Bb4 Qc2 O-O a3 Bxc3+ Qxc3 b6 Bg5 Bb7 e3 d6 f3 Nbd7 Ne2 c5 Rd1 Qe8",
        // Nimzo, Classical, 4...d5
        "d4 Nf6 c4 e6 Nc3 Bb4 Qc2 d5 a3 Bxc3+ Qxc3 dxc4 Qxc4 O-O Nf3 b6 Bg5 Bb7 e3 Nbd7",
        // Nimzo, Classical, 4...c5
        "d4 Nf6 c4 e6 Nc3 Bb4 Qc2 c5 dxc5 Na6 a3 Bxc5 Nf3 Qb6 Bg5 Bxf2+",
        // Nimzo, Saemisch
        "d4 Nf6 c4 e6 Nc3 Bb4 a3 Bxc3+ bxc3 c5 e3 Nc6 Bd3 O-O Ne2 b6 e4 Ne8 O-O Ba6 f4 d6",
        // Nimzo, Saemisch, 4.f3
        "d4 Nf6 c4 e6 Nc3 Bb4 f3 d5 a3 Bxc3+ bxc3 c5 cxd5 Nxd5 dxc5 f5 e4 fxe4 fxe4 Nf6",
        // Nimzo, Leningrad
        "d4 Nf6 c4 e6 Nc3 Bb4 Bg5 h6 Bh4 c5 d5 d6 e3 exd5 cxd5 Bxc3+ bxc3 Qe7 Bd3 Nbd7",
        // Nimzo, Kasparov
        "d4 Nf6 c4 e6 Nc3 Bb4 Nf3 c5 g3 cxd4 Nxd4 O-O Bg2 d5 cxd5 Nxd5 O-O Nxc3 bxc3 Bxc3 Rb1 Bxd4 Qxd4 Qxd4",
        // Nimzo, 4.Nf3
        "d4 Nf6 c4 e6 Nc3 Bb4 Nf3 b6 Bg5 Bb7 e3 h6 Bh4 g5 Bg3 Ne4 Nd2 Nxg3 hxg3",
        // Bogo-Indian
        "d4 Nf6 c4 e6 Nf3 Bb4+ Bd2 Qe7 g3 Nc6 Nc3 Bxc3 Bxc3 Ne4 Rc1 O-O Bg2 Nxc3 Rxc3 d6 O-O e5 Qc2 Bg4",
        // Bogo-Indian, 4...a5
        "d4 Nf6 c4 e6 Nf3 Bb4+ Bd2 a5 g3 O-O Bg2 d6 O-O Nbd7 Qc2 Qe7 Rd1 Bxd2 Nbxd2 e5",
        // Bogo-Indian, 4...Bxd2
        "d4 Nf6 c4 e6 Nf3 Bb4+ Bd2 Bxd2+ Qxd2 O-O Nc3 d6 e4 Nbd7 Bd3 e5 O-O Re8 Rae1 c6",
        // Bogo-Indian, 4.Nbd2
        "d4 Nf6 c4 e6 Nf3 Bb4+ Nbd2 O-O a3 Be7 e4 d5 e5 Ne4 Bd3 Nxd2 Bxd2 c5",
        // Bogo-Indian, 4.Nbd2 d5
        "d4 Nf6 c4 e6 Nf3 Bb4+ Nbd2 d5 a3 Bxd2+ Qxd2 O-O e3 b6 cxd5 exd5 b4 Bb7",
        // London, Classic
        "d4 d5 Bf4 Nf6 e3 e6 Nf3 c5 c3 Nc6 Nbd2 Bd6 Bg3 O-O Bd3 Qe7 Qe2 b6 e4 dxe4 Nxe4 Nxe4 Bxe4 Bb7",
        // London, 5.c3 Qb6
        "d4 d5 Bf4 Nf6 e3 e6 Nf3 c5 c3 Qb6 Qb3 c4 Qc2 Bd7 Nbd2 Nc6 Be2 Be7 O-O O-O",
        // London, 1.d4 Nf6
        "d4 Nf6 Nf3 d5 Bf4 e6 e3 c5 c3 Nc6 Nbd2 Bd6 Bg3 O-O Bd3 b6 Ne5 Bb7 f4 Nd7",
        // London, 3...c5
        "d4 Nf6 Nf3 d5 Bf4 c5 e3 Nc6 Nbd2 cxd4 exd4 Bf5 c3 e6 Qb3 Qd7 Bb5 Bd6",
        // London, 4...Nc6
        "d4 d5 Nf3 Nf6 Bf4 c5 e3 Nc6 c3 Qb6 Qb3 c4 Qc2 Bf5 Qc1 e6 Nbd2 Bd6 Bg3 O-O",
        // London, vs KID
        "d4 Nf6 Bf4 g6 e3 Bg7 Nf3 O-O Be2 d6 h3 Nbd7 O-O b6 c3 Bb7 Nbd2 c5 Qc2 Re8",
        // London, vs Bf5
        "d4 Nf6 Nf3 d5 Bf4 Bf5 e3 e6 Nbd2 Nbd7 c3 c6 Qb3 Qb6 Qxb6 axb6 h3 h6",
        // Colle-Zukertort
        "d4 d5 Nf3 Nf6 e3 e6 Bd3 c5 b3 Nc6 O-O Bd6 Bb2 O-O Nbd2 Qe7 dxc5 Bxc5 Qe2 a5 c4 dxc4",
        // Colle, Classical
        "d4 d5 Nf3 Nf6 e3 e6 Bd3 Nc6 c3 Bd6 Nbd2 O-O O-O e5 dxe5 Nxe5 Nxe5 Bxe5 f4 Bd6 e4 dxe4",
        // Colle, Classical, 6...Qe7
        "d4 d5 Nf3 Nf6 e3 e6 Bd3 c5 c3 Nc6 Nbd2 Bd6 O-O O-O dxc5 Bxc5 e4 dxe4 Nxe4 Be7",
        // Colle, 4...Be7
        "d4 d5 Nf3 Nf6 e3 e6 Bd3 Be7 O-O O-O Nbd2 c5 c3 Nc6 Qe2 b6 e4 Bb7",
        // Colle, vs Slav setup
        "d4 d5 Nf3 Nf6 e3 c6 Bd3 Bg4 Nbd2 e6 c3 Nbd7 Qc2 Bd6 e4 dxe4 Nxe4 Nxe4 Bxe4",
        // QGA, Classical
        "d4 d5 c4 dxc4 Nf3 Nf6 e3 e6 Bxc4 c5 O-O a6 a4 Nc6 Qe2 cxd4 Rd1 Be7 exd4 O-O Nc3 Nd5 Ne4 N5b4",
        // QGA, Classical, 7.Bb3
        "d4 d5 c4 dxc4 Nf3 Nf6 e3 e6 Bxc4 c5 O-O a6 Bb3 cxd4 exd4 Nc6 Nc3 Be7 Re1 O-O Bg5 b5",
        // QGA, 3.e3
        "d4 d5 c4 dxc4 e3 e5 Bxc4 exd4 exd4 Nf6 Nc3 Be7 Nf3 O-O O-O Nc6 Be3 Bg4",
        // QGA, 3.e4
        "d4 d5 c4 dxc4 e4 e5 Nf3 exd4 Bxc4 Nc6 O-O Be6 Bxe6 fxe6 Qb3 Qd7 Qxb7 Rb8 Qa6 Bd6",
        // QGA, Alekhine
        "d4 d5 c4 dxc4 Nf3 a6 e3 Bg4 Bxc4 e6 h3 Bh5 Nc3 Nc6 Qb3 Bxf3 gxf3 Nf6 Bd2 Nd5",
        // QGA, 3.Nf3 Nf6 4.Nc3
        "d4 d5 c4 dxc4 Nf3 Nf6 Nc3 a6 e4 b5 e5 Nd5 a4 Nxc3 bxc3 Bb7 axb5 axb5 Rxa8 Bxa8",
        // QGA, 3.Nf3 Nf6 4.e3 Bg4
        "d4 d5 c4 dxc4 Nf3 Nf6 e3 Bg4 Bxc4 e6 Qb3 Nbd7 Nc3 Bxf3 gxf3 Nb6 Be2 Qd7",
        // QGD, Orthodox, Tartakower
        "d4 d5 c4 e6 Nc3 Nf6 Bg5 Be7 e3 O-O Nf3 h6 Bh4 b6 cxd5 Nxd5 Bxe7 Qxe7 Nxd5 exd5 Rc1 Be6 Qa4 c5 Qa3 Rc8 Bb5 a6 dxc5 bxc5 O-O Ra7 Be2 Nd7",
        // QGD, Orthodox, Lasker
        "d4 d5 c4 e6 Nc3 Nf6 Bg5 Be7 e3 O-O Nf3 h6 Bh4 Ne4 Bxe7 Qxe7 Rc1 c6 Be2 Nxc3 Rxc3 dxc4 Bxc4 Nd7 O-O b6",
        // QGD, Orthodox, Capablanca
        "d4 d5 c4 e6 Nc3 Nf6 Bg5 Be7 e3 O-O Nf3 Nbd7 Rc1 c6 Bd3 dxc4 Bxc4 Nd5 Bxe7 Qxe7 O-O Nxc3 Rxc3 e5",
        // QGD, Orthodox, 7.Qc2
        "d4 d5 c4 e6 Nc3 Nf6 Bg5 Be7 e3 O-O Nf3 Nbd7 Qc2 c5 cxd5 exd5 dxc5 Nxc5 Be2 Be6",
        // QGD, Exchange
        "d4 d5 c4 e6 Nc3 Nf6 cxd5 exd5 Bg5 c6 e3 Be7 Bd3 Nbd7 Qc2 O-O Nge2 Re8 O-O Nf8 f3 Ng6",
        // QGD, Exchange, 4.Bg5
        "d4 d5 c4 e6 Nc3 Be7 cxd5 exd5 Bf4 c6 e3 Bf5 Qf3 Bg6 Bd3 Bxd3",
        // QGD, Tarrasch
        "d4 d5 c4 e6 Nc3 c5 cxd5 exd5 Nf3 Nc6 g3 Nf6 Bg2 Be7 O-O O-O Bg5 cxd4 Nxd4 h6 Be3 Re8 Qb3 Qb6",
        // QGD, Tarrasch, 9.dxc5
        "d4 d5 c4 e6 Nc3 c5 cxd5 exd5 Nf3 Nc6 g3 Nf6 Bg2 Be7 O-O O-O dxc5 Bxc5 Bg5 d4 Na4 Be7",
        // QGD, Semi-Tarrasch
        "d4 d5 c4 e6 Nc3 Nf6 Nf3 c5 cxd5 Nxd5 e4 Nxc3 bxc3 cxd4 cxd4 Bb4+ Bd2 Bxd2+ Qxd2 O-O Bc4 Nc6",
        // QGD, Ragozin
        "d4 d5 c4 e6 Nc3 Nf6 Nf3 Bb4 Bg5 h6 Bh4 dxc4 e4 g5 Bg3 b5 Be2 Bb7 O-O Nbd7",
        // QGD, Vienna
        "d4 d5 c4 e6 Nc3 Nf6 Nf3 Bb4 Bg5 dxc4 e4 c5 Bxc4 cxd4 Nxd4 Bxc3+ bxc3 Qa5 Bb5+ Nbd7",
        // QGD, Cambridge Springs
        "d4 d5 c4 e6 Nc3 Nf6 Bg5 Nbd7 e3 c6 Nf3 Qa5 Nd2 Bb4 Qc2 O-O Be2 e5 O-O exd4",
        // QGD, Slav-ish 3.Nf3
        "d4 d5 c4 e6 Nf3 Nf6 Nc3 Be7 Bf4 O-O e3 c5 dxc5 Bxc5 Qc2 Nc6 Rd1 Qa5 a3 Bxa3",
        // QGD, Catalan-style
        "d4 Nf6 c4 e6 g3 d5 Bg2 Be7 Nf3 O-O O-O dxc4 Qc2 a6 a4 Bd7 Qxc4 Bc6 Bg5 Nbd7"
    };
}