package com.chess.engine.ui;

/* The eight playing strengths. Level 8 is the full engine; the percentages are nominal. */
public class EngineLevel {

    static final int COUNT = 8;
    private static final String[] NAME = {
            "Beginner", "Casual", "Learner", "Club player", "Intermediate", "Advanced", "Expert", "Maximum"};
    private static final int[] PERCENT = {10, 20, 30, 45, 62, 72, 85, 100};
    private static final String[] BLURB = {
            "Plays quickly and often misses plans. Easy to beat, but it won't give pieces away.",
            "Sees simple tactics but plays without much of a plan.",
            "Defends against one-move threats; loses to patient play.",
            "Solid against simple tricks. Outplay it in the middlegame.",
            "Around 60-65% of full strength: calculates well, you need real tactics or a clear positional edge.",
            "Strong tactician with few slips. Complex lines are needed to beat it.",
            "Very accurate; wins long games on small mistakes.",
            "The full engine: deep calculation, no weakening."};

    private EngineLevel() { }

    static String name(int level) { return NAME[clamp(level) - 1]; }
    static int percent(int level) { return PERCENT[clamp(level) - 1]; }
    static String blurb(int level) { return BLURB[clamp(level) - 1]; }

    static String title(int level) {
        return "Level " + clamp(level) + " - " + name(level);
    }

    private static int clamp(int level) { return Math.max(1, Math.min(COUNT, level)); }
}