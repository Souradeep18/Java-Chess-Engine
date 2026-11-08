package com.chess.engine.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/* All built-in puzzles, indexed by theme, with rating-targeted selection. */
public class PuzzleLibrary {

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
    }

    private static List<Puzzle> all;

    private PuzzleLibrary() { }

    static synchronized List<Puzzle> all() {
        if (all == null) {
            List<Puzzle> list = new ArrayList<>();
            int id = 1;
            for (String line : PuzzleData.lines()) {
                if (line.isEmpty()) continue;
                try {
                    list.add(Puzzle.parse(id++, line));
                } catch (RuntimeException ignored) {
                    // skip a malformed line
                }
            }
            all = Collections.unmodifiableList(list);
        }
        return all;
    }

    static String label(String theme) {
        String l = LABELS.get(theme);
        return l != null ? l : theme;
    }

    /** Themes that have at least {@code minCount} puzzles, in display order ("mixed" first). */
    static List<String> themes(int minCount) {
        List<String> out = new ArrayList<>();
        for (String key : LABELS.keySet()) {
            if (count(key) >= minCount) out.add(key);
        }
        return out;
    }

    static int count(String theme) {
        if (MIXED.equals(theme)) return all().size();
        int n = 0;
        for (Puzzle p : all()) if (p.themes.contains(theme)) n++;
        return n;
    }

    static int minRating() {
        int m = Integer.MAX_VALUE;
        for (Puzzle p : all()) m = Math.min(m, p.rating);
        return m == Integer.MAX_VALUE ? 0 : m;
    }

    static int maxRating() {
        int m = 0;
        for (Puzzle p : all()) m = Math.max(m, p.rating);
        return m;
    }

    /**
     * A random puzzle of the theme whose rating is close to {@code target}; puzzles in {@code seen} are
     * avoided while others are available. The window widens until enough candidates exist.
     */
    static Puzzle pick(String theme, int target, Set<Integer> seen, Random rnd) {
        List<Puzzle> pool = new ArrayList<>();
        for (Puzzle p : all()) {
            if (MIXED.equals(theme) || p.themes.contains(theme)) pool.add(p);
        }
        if (pool.isEmpty()) pool = new ArrayList<>(all());
        if (pool.isEmpty()) return null;

        for (int window = 80; ; window += 80) {
            List<Puzzle> near = new ArrayList<>();
            for (Puzzle p : pool) {
                if (Math.abs(p.rating - target) <= window && !seen.contains(p.id)) near.add(p);
            }
            if (near.size() >= 6 || window > 2400) {
                if (near.isEmpty()) {
                    seen.clear();       // everything has been seen: start over
                    return pick(theme, target, seen, rnd);
                }
                return near.get(rnd.nextInt(near.size()));
            }
        }
    }
}