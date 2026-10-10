package com.chess.engine.engine;

import java.util.ArrayList;
import java.util.List;

public final class AntiChess {

    public static final String VARIANT_ANTI_CHESS = "variant-anti-chess";

    public enum Result {
        ONGOING,
        WHITE_NO_PIECES,    // White wins
        BLACK_NO_PIECES,    // Black wins
        WHITE_STALEMATED,   // White wins (no legal moves left)
        BLACK_STALEMATED    // Black wins (no legal moves left)
    }

    private AntiChess() {
    }

    // ------------------------------------------------------------------ piece counts & evaluation

    public static boolean hasNoPieces(Board board, boolean white) {
        for (int sq = 0; sq < 64; sq++) {
            char p = board.getPiece(sq);
            if (p != '.' && (white ? Character.isUpperCase(p) : Character.isLowerCase(p))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Anti-Chess evaluation function: Having FEWER pieces gives a higher positional score.
     */
    public static int evaluate(Board board) {
        int whiteMaterial = 0;
        int blackMaterial = 0;

        for (int sq = 0; sq < 64; sq++) {
            char p = board.getPiece(sq);
            if (p == '.') continue;

            int val = getPieceValue(p);
            if (Character.isUpperCase(p)) {
                whiteMaterial += val;
            } else {
                blackMaterial += val;
            }
        }

        int score = blackMaterial - whiteMaterial; // Less material = better score
        return board.isWhiteToMove() ? score : -score;
    }

    public static int getPieceValue(char p) {
        switch (Character.toUpperCase(p)) {
            case 'P': return 100;
            case 'N': return 300;
            case 'B': return 300;
            case 'R': return 500;
            case 'Q': return 900;
            case 'K': return 1000;
            default:  return 0;
        }
    }

    // ------------------------------------------------------------------ move generation & mandatory captures

    /**
     * Filters pseudo-legal or legal moves to enforce mandatory captures.
     */
    public static List<Move> filterMandatoryCaptures(Board board, List<Move> moves) {
        List<Move> captures = new ArrayList<>();
        for (Move move : moves) {
            if (move.isCapture || move.enPassant || board.getPiece(move.to) != '.') {
                captures.add(move);
            }
        }
        return captures.isEmpty() ? moves : captures;
    }

    public static List<Move> generateLegalMoves(Board board) {
        List<Move> pseudo = MoveGenerator.generatePseudoLegalMoves(board);
        List<Move> legal = new ArrayList<>();

        // In Anti-Chess, there are no checks or king safety constraints
        for (Move move : pseudo) {
            legal.add(move);
        }

        return filterMandatoryCaptures(board, legal);
    }

    public static Result getResult(Board board) {
        if (!board.isAntiChess()) {
            return Result.ONGOING;
        }

        if (hasNoPieces(board, true)) {
            return Result.WHITE_NO_PIECES;
        }
        if (hasNoPieces(board, false)) {
            return Result.BLACK_NO_PIECES;
        }

        if (generateLegalMoves(board).isEmpty()) {
            return board.isWhiteToMove() ? Result.WHITE_STALEMATED : Result.BLACK_STALEMATED;
        }

        return Result.ONGOING;
    }
}