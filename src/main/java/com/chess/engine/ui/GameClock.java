package com.chess.engine.ui;

//A two-sided chess clock with increment. Side 0 = white, 1 = black.
public class GameClock {

    private final long[] remaining = new long[2];
    private final long increment;
    private final boolean unlimited;
    private int running = -1;
    private long lastNano;

    GameClock(boolean unlimited, long baseMs, long incrementMs) {
        this.unlimited = unlimited;
        this.increment = incrementMs;
        remaining[0] = baseMs;
        remaining[1] = baseMs;
    }

    boolean isUnlimited() { return unlimited; }

    /* Milliseconds left for the side (including the time used so far on its running move). */
    synchronized long remaining(int side) {
        if (unlimited) return Long.MAX_VALUE / 4;
        long r = remaining[side];
        if (running == side) r -= (System.nanoTime() - lastNano) / 1_000_000L;
        return Math.max(0, r);
    }

    synchronized void start(int side) {
        stop();
        running = side;
        lastNano = System.nanoTime();
    }

    /** Stops the running clock, charging the elapsed time. */
    synchronized void stop() {
        if (running >= 0 && !unlimited) {
            remaining[running] -= (System.nanoTime() - lastNano) / 1_000_000L;
            if (remaining[running] < 0) remaining[running] = 0;
        }
        running = -1;
    }

    /* The side that just moved gets the increment; the other side's clock starts. */
    synchronized void moved(int mover) {
        if (running >= 0 && !unlimited) {
            remaining[running] -= (System.nanoTime() - lastNano) / 1_000_000L;
            if (remaining[running] > 0) remaining[running] += increment;
            if (remaining[running] < 0) remaining[running] = 0;
        }
        running = 1 - mover;
        lastNano = System.nanoTime();
    }

    synchronized boolean flagged(int side) {
        return !unlimited && remaining(side) <= 0;
    }

    static String format(long ms) {
        if (ms >= Long.MAX_VALUE / 8) return "∞";
        long totalSeconds = ms / 1000;
        if (ms < 10_000) {
            return totalSeconds + "." + (ms % 1000) / 100;
        }
        long m = totalSeconds / 60, s = totalSeconds % 60;
        if (m >= 60) return (m / 60) + ":" + (m % 60 < 10 ? "0" : "") + (m % 60) + ":" + (s < 10 ? "0" : "") + s;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }
}