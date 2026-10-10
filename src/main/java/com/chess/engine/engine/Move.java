package com.chess.engine.engine;

import java.util.Objects;

public class Move {

    public final int from;
    public final int to;

    public final char promotion; // Store promotion piece choice ('Q', 'R', 'B', 'N', or '\0')

    public final boolean castleKingSide;
    public final boolean castleQueenSide;

    public final boolean enPassant;

    // --- Search Engine Compatibility Fields & Aliases ---
    public int fromSquare;
    public int toSquare;
    public boolean isCapture;
    public char promotionPiece;
    public boolean isCastle;
    public boolean isEnPassant;

    // Standard Move Constructor
    public Move(int from, int to) {
        this(from, to, '\0', false, false, false, false);
    }

    // Capture Move Constructor
    public Move(int from, int to, boolean isCapture) {
        this(from, to, '\0', false, false, false, isCapture);
    }

    // Promotion Move Constructor
    public Move(int from, int to, char promotion) {
        this(from, to, promotion, false, false, false, false);
    }

    // Full Constructor
    public Move(int from, int to, char promotion, boolean castleKingSide, boolean castleQueenSide, boolean enPassant) {
        this(from, to, promotion, castleKingSide, castleQueenSide, enPassant, false);
    }

    // Primary Internal Constructor initializing all optimization aliases
    public Move(int from, int to, char promotion, boolean castleKingSide, boolean castleQueenSide, boolean enPassant, boolean isCapture) {
        this.from = from;
        this.to = to;
        this.promotion = promotion;
        this.castleKingSide = castleKingSide;
        this.castleQueenSide = castleQueenSide;
        this.enPassant = enPassant;

        // Map engine aliases
        this.fromSquare = from;
        this.toSquare = to;
        this.promotionPiece = (promotion == '\0') ? '.' : promotion;
        this.isCastle = castleKingSide || castleQueenSide;
        this.isEnPassant = enPassant;
        this.isCapture = isCapture;
    }

    // --- Alias Getters & Utility Methods ---
    public char getPromotionPiece() {
        return promotion;
    }

    public int getFromSquare() {
        return from;
    }

    public int getToSquare() {
        return to;
    }

    public boolean isCapture() {
        return isCapture;
    }

    public void setCapture(boolean capture) {
        this.isCapture = capture;
    }

    /**
     * Essential for Transposition Table key matching, Killer Move comparisons, 
     * and History Heuristic table lookups in ChessEngine.java.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Move move = (Move) o;
        return from == move.from &&
               to == move.to &&
               Character.toUpperCase(promotion) == Character.toUpperCase(move.promotion) &&
               isCastle == move.isCastle &&
               isEnPassant == move.isEnPassant;
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, Character.toUpperCase(promotion), isCastle, isEnPassant);
    }

    @Override
    public String toString() {
        return squareName(from) + squareName(to)
                + (promotion != '\0' && promotion != '.' ? "=" + Character.toUpperCase(promotion) : "");
    }

    public static String squareName(int square) {
        int file = square % 8;
        int rank = square / 8; // 0..7 row index

        char fileChar = (char) ('a' + file);
        int rankNumber = rank + 1; // Row 0 -> Rank 1, Row 1 -> Rank 2

        return "" + fileChar + rankNumber;
    }
}