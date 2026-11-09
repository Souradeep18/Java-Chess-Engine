package com.chess.engine.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/* All built-in puzzles, indexed by theme, with rating-targeted selection. */
final class PuzzleLibrary {

    static final String MIXED = "mixed";

    /* Theme key -> label shown in the theme chooser (in display order). */
    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put(MIXED, "Mixed (all themes)");
        LABELS.put("mate", "Checkmate (any)");
        LABELS.put("mateIn1", "Mate in 1");
        LABELS.put("mateIn2", "Mate in 2");
        LABELS.put("mateIn3", "Mate in 3 or more");
        LABELS.put("backRankMate", "Back-rank mate");
        LABELS.put("smotheredMate", "Smothered mate");
        LABELS.put("fork", "Fork");
        LABELS.put("pin", "Pin");
        LABELS.put("skewer", "Skewer");
        LABELS.put("discoveredAttack", "Discovered attack");
        LABELS.put("hangingPiece", "Hanging piece");
        LABELS.put("sacrifice", "Sacrifice");
        LABELS.put("promotion", "Promotion");
        LABELS.put("quietMove", "Quiet move");
        LABELS.put("advantage", "Win material");
        LABELS.put("opening", "Opening");
        LABELS.put("middlegame", "Middlegame");
        LABELS.put("endgame", "Endgame");
        LABELS.put("crushing", "Crushing (win big)");
        LABELS.put("defensiveMove", "Defensive move");
        LABELS.put("attraction", "Attraction");
        LABELS.put("deflection", "Deflection");
        LABELS.put("clearance", "Clearance");
        LABELS.put("interference", "Interference");
        LABELS.put("intermezzo", "Intermezzo (zwischenzug)");
        LABELS.put("xRayAttack", "X-ray attack");
        LABELS.put("zugzwang", "Zugzwang");
        LABELS.put("trappedPiece", "Trapped piece");
        LABELS.put("capturingDefender", "Capture the defender");
        LABELS.put("doubleCheck", "Double check");
        LABELS.put("exposedKing", "Exposed king");
        LABELS.put("kingsideAttack", "Kingside attack");
        LABELS.put("queensideAttack", "Queenside attack");
        LABELS.put("advancedPawn", "Advanced pawn");
        LABELS.put("enPassant", "En passant");
        LABELS.put("castling", "Castling");
        LABELS.put("underPromotion", "Under-promotion");
        LABELS.put("anastasiaMate", "Anastasia's mate");
        LABELS.put("arabianMate", "Arabian mate");
        LABELS.put("bodenMate", "Boden's mate");
        LABELS.put("hookMate", "Hook mate");
        LABELS.put("dovetailMate", "Dovetail mate");
        LABELS.put("doubleBishopMate", "Double bishop mate");
        LABELS.put("pawnEndgame", "Pawn endgame");
        LABELS.put("rookEndgame", "Rook endgame");
        LABELS.put("bishopEndgame", "Bishop endgame");
        LABELS.put("knightEndgame", "Knight endgame");
        LABELS.put("queenEndgame", "Queen endgame");
        LABELS.put("queenRookEndgame", "Queen and rook endgame");
    }

    // Compact storage: with several hundred thousand puzzles only the raw text lines, the ratings and
    // per-theme id lists (sorted by rating) are kept; Puzzle objects are built on demand.
    private static String[] lines;
    private static int[] ratings;
    private static Map<String, int[]> byTheme;     // theme -> puzzle ids sorted by rating
    private static int[] everything;               // all ids sorted by rating
    private static boolean loaded;

    private PuzzleLibrary() { }

    /* Starts loading the puzzles in the background so the first puzzle screen opens at once. */
    static void preload() {
        Thread t = new Thread(PuzzleLibrary::ensureLoaded, "puzzle-loader");
        t.setDaemon(true);
        t.start();
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        String[] src = PuzzleData.lines();
        List<String> ok = new ArrayList<>(src.length);
        int[] r = new int[src.length];
        int n = 0;
        Map<String, IntList> idx = new HashMap<>();
        for (String line : src) {
            int a = line.indexOf('|');
            int b = a < 0 ? -1 : line.indexOf('|', a + 1);
            int c = b < 0 ? -1 : line.indexOf('|', b + 1);
            int d = c < 0 ? -1 : line.indexOf('|', c + 1);
            if (d < 0) continue;
            int rating;
            try {
                rating = Integer.parseInt(line.substring(c + 1, d));
            } catch (NumberFormatException e) {
                continue;
            }
            ok.add(line);
            r[n] = rating;
            int from = d + 1;
            while (from <= line.length()) {
                int comma = line.indexOf(',', from);
                String t = comma < 0 ? line.substring(from) : line.substring(from, comma);
                if (!t.isEmpty()) idx.computeIfAbsent(t, k -> new IntList()).add(n);
                if (comma < 0) break;
                from = comma + 1;
            }
            n++;
        }
        lines = ok.toArray(new String[0]);
        ratings = java.util.Arrays.copyOf(r, n);
        Map<String, int[]> map = new HashMap<>();
        for (Map.Entry<String, IntList> e : idx.entrySet()) map.put(e.getKey(), sortedByRating(e.getValue().toArray()));
        byTheme = map;
        int[] allIds = new int[n];
        for (int i = 0; i < n; i++) allIds[i] = i;
        everything = sortedByRating(allIds);
        loaded = true;
    }

    private static int[] sortedByRating(int[] ids) {
        long[] keys = new long[ids.length];
        for (int i = 0; i < ids.length; i++) keys[i] = ((long) ratings[ids[i]] << 32) | ids[i];
        java.util.Arrays.sort(keys);
        int[] out = new int[ids.length];
        for (int i = 0; i < out.length; i++) out[i] = (int) (keys[i] & 0xffffffffL);
        return out;
    }

    private static final class IntList {
        int[] a = new int[8];
        int n;
        void add(int v) {
            if (n == a.length) a = java.util.Arrays.copyOf(a, n * 2);
            a[n++] = v;
        }
        int[] toArray() { return java.util.Arrays.copyOf(a, n); }
    }

    private static int[] pool(String theme) {
        ensureLoaded();
        if (MIXED.equals(theme)) return everything;
        int[] l = byTheme.get(theme);
        return l != null ? l : new int[0];
    }

    /* Themes that have at least {@code minCount} puzzles, in display order ("mixed" first). */
    static List<String> themes(int minCount) {
        List<String> out = new ArrayList<>();
        for (String key : LABELS.keySet()) {
            if (count(key) >= minCount) out.add(key);
        }
        return out;
    }

    static String label(String theme) {
        String l = LABELS.get(theme);
        return l != null ? l : theme;
    }

    static int count(String theme) {
        return pool(theme).length;
    }

    static int size() {
        ensureLoaded();
        return lines.length;
    }

    static int minRating() {
        int[] p = pool(MIXED);
        return p.length == 0 ? 0 : ratings[p[0]];
    }

    static int maxRating() {
        int[] p = pool(MIXED);
        return p.length == 0 ? 0 : ratings[p[p.length - 1]];
    }

    private static Puzzle build(int id) {
        return Puzzle.parse(id, lines[id]);
    }

    /** First index in the rating-sorted pool whose rating is >= value. */
    private static int lowerBound(int[] pool, int value) {
        int lo = 0, hi = pool.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (ratings[pool[mid]] < value) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    /*
      A random puzzle of the theme whose rating is close to {@code target}; puzzles in {@code seen} are
      avoided while others are available. The window widens until enough candidates exist.
     */
    static Puzzle pick(String theme, int target, Set<Integer> seen, Random rnd) {
        int[] pool = pool(theme);
        if (pool.length == 0) pool = pool(MIXED);
        if (pool.length == 0) return null;
        for (int window = 80; ; window += 80) {
            int lo = lowerBound(pool, target - window);
            int hi = lowerBound(pool, target + window + 1);
            int n = hi - lo;
            if (n >= 6 || window > 2400) {
                if (n > 0) {
                    for (int tries = 0; tries < 60; tries++) {
                        int id = pool[lo + rnd.nextInt(n)];
                        if (!seen.contains(id)) return build(id);
                    }
                    for (int i = lo; i < hi; i++) {          // small window: look at every candidate
                        if (!seen.contains(pool[i])) return build(pool[i]);
                    }
                    if (window <= 2400) continue;            // all of this window was seen: widen it
                }
                seen.clear();                                // everything has been seen: start over
                return pick(theme, target, seen, rnd);
            }
        }
    }
}