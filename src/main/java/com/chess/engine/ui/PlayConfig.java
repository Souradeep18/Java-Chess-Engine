package com.chess.engine.ui;

/* What the player chose in the "Play against the computer" dialog. */
public class PlayConfig {
    final int level;            // 1..8
    final int side;             // 0 = white, 1 = black, 2 = random
    final boolean unlimited;
    final long baseMs;
    final long incrementMs;
    final PlayVariant variant;

    PlayConfig(int level, int side, boolean unlimited, long baseMs, long incrementMs) {
        this(level, side, unlimited, baseMs, incrementMs, PlayVariant.STANDARD);
    }

    PlayConfig(int level, int side, boolean unlimited, long baseMs, long incrementMs, PlayVariant variant) {
        this.variant = variant;
        this.level = level;
        this.side = side;
        this.unlimited = unlimited;
        this.baseMs = baseMs;
        this.incrementMs = incrementMs;
    }

    String timeText() {
        if (unlimited) return "Unlimited";
        long m = baseMs / 60000;
        long s = (baseMs % 60000) / 1000;
        String base = s == 0 ? String.valueOf(m) : (m == 0 ? "0:" + (s < 10 ? "0" : "") + s : m + ":" + (s < 10 ? "0" : "") + s);
        return base + "+" + incrementMs / 1000;
    }
}