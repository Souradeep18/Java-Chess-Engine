package com.chess.engine.ui;

import com.chess.engine.engine.Board;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/* One tactical puzzle: a position, the opponent's last move, the solution line and a difficulty rating. */
public class Puzzle {

    final int id;
    final String fen;             // position with the solver to move (the opponent's last move is already played)
    final String lastMoveUci;     // the move that led here, shown highlighted
    final String[] solution;      // UCI moves: solver, opponent, solver, ...
    final int rating;
    final Set<String> themes;

    Puzzle(int id, String fen, String lastMoveUci, String[] solution, int rating, Set<String> themes) {
        this.id = id;
        this.fen = fen;
        this.lastMoveUci = lastMoveUci;
        this.solution = solution;
        this.rating = rating;
        this.themes = Collections.unmodifiableSet(themes);
    }

    /** Parses "fen|last|m1 m2 m3|rating|theme,theme". */
    static Puzzle parse(int id, String line) {
        String[] f = line.split("\\|");
        Set<String> themes = new HashSet<>(Arrays.asList(f[4].split(",")));
        return new Puzzle(id, f[0], f[1], f[2].split(" "), Integer.parseInt(f[3]), themes);
    }

    Board board() {
        return Board.fromFEN(fen);
    }

    boolean whiteToSolve() {
        return fen.split(" ")[1].equals("w");
    }

    /* Number of moves the solver has to find. */
    int solverMoves() {
        return (solution.length + 1) / 2;
    }
}