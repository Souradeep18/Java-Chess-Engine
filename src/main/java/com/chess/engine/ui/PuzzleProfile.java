package com.chess.engine.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/* Remembers the puzzle points and the best Puzzle Rush scores between runs (~/.javachess/profile.properties). */
public class PuzzleProfile {

    static final int START_POINTS = 1000;
    static final int MIN_POINTS = 100;

    private static final Properties PROPS = new Properties();
    private static boolean loaded;

    private PuzzleProfile() { }

    private static File file() {
        return new File(new File(System.getProperty("user.home"), ".javachess"), "profile.properties");
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        File f = file();
        if (f.isFile()) {
            try (FileInputStream in = new FileInputStream(f)) {
                PROPS.load(in);
            } catch (IOException ignored) {
                // start with defaults
            }
        }
    }

    private static synchronized void save() {
        File f = file();
        try {
            File dir = f.getParentFile();
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            try (FileOutputStream out = new FileOutputStream(f)) {
                PROPS.store(out, "Java Chess Engine profile");
            }
        } catch (IOException ignored) {
            // not being able to save is not fatal
        }
    }

    static synchronized int getPoints() {
        load();
        try {
            return Math.max(MIN_POINTS, Integer.parseInt(PROPS.getProperty("puzzlePoints", String.valueOf(START_POINTS))));
        } catch (NumberFormatException e) {
            return START_POINTS;
        }
    }

    static synchronized void setPoints(int points) {
        load();
        PROPS.setProperty("puzzlePoints", String.valueOf(Math.max(MIN_POINTS, points)));
        save();
    }

    static synchronized int getBest(String mode) {
        load();
        try {
            return Integer.parseInt(PROPS.getProperty("rushBest." + mode, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /* Stores the score when it beats the record; returns true for a new record. */
    static synchronized boolean submitBest(String mode, int score) {
        load();
        if (score > getBest(mode)) {
            PROPS.setProperty("rushBest." + mode, String.valueOf(score));
            save();
            return true;
        }
        return false;
    }

    static synchronized int getInt(String key, int def) {
        load();
        try {
            return Integer.parseInt(PROPS.getProperty(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static synchronized void putInt(String key, int value) {
        load();
        PROPS.setProperty(key, String.valueOf(value));
        save();
    }
}