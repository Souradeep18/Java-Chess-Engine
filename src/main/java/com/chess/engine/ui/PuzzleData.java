package com.chess.engine.ui;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/*
  Where the puzzles come from. Supports both gzipped (.gz) and plain text (.txt) puzzle files.
 */
public class PuzzleData {

    private static final String FILE = "C:\\WorkArea\\engine\\src\\main\\java\\com\\chess\\engine\\puzzles.txt\\puzzles500k.txt";

    private PuzzleData() { }

    static String[] lines() {
        List<String> out = new ArrayList<>();
        try (InputStream rawIn = open()) {
            if (rawIn != null) {
                InputStream in = rawIn.markSupported() ? rawIn : new BufferedInputStream(rawIn, 1 << 16);
                in.mark(2);
                int magic = (in.read() & 0xff) | ((in.read() & 0xff) << 8);
                in.reset();

                InputStream actualIn = (magic == 0x8b1f) ? new GZIPInputStream(in, 1 << 16) : in;

                try (BufferedReader r = new BufferedReader(new InputStreamReader(actualIn, StandardCharsets.UTF_8), 1 << 16)) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (!line.isEmpty()) out.add(line);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Could not read " + FILE + ": " + e);
            out.clear();
        }
        if (out.size() < 1000) {
            if (out.isEmpty()) System.err.println(FILE + " not found or insufficient lines - using the small built-in puzzle set");
            return PuzzleFallbackData.lines();
        }
        return out.toArray(new String[0]);
    }

    private static InputStream open() throws Exception {
        InputStream in = PuzzleData.class.getResourceAsStream(FILE);
        if (in == null) in = PuzzleData.class.getResourceAsStream("/" + FILE);
        if (in == null) {
            File f = new File(FILE);
            if (!f.isFile()) f = new File(System.getProperty("user.home"), ".javachess" + File.separator + FILE);
            if (f.isFile()) in = new java.io.FileInputStream(f);
        }
        return in;
    }
}