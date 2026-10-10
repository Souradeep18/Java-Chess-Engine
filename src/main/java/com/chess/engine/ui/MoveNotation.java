package com.chess.engine.ui;

import com.chess.engine.engine.Board; 
import com.chess.engine.engine.Move;
import com.chess.engine.engine.MoveGenerator;

public class MoveNotation {

    private MoveNotation() {
    }

    public static String notation(Board before, Move move) {

        //Castling
        if (move.castleKingSide) {
            return addCheckSuffix(
                    before,
                    move,
                    "O-O"
            );
        }

        if (move.castleQueenSide) {
            return addCheckSuffix(
                    before,
                    move,
                    "O-O-O"
            );
        }

        char piece = before.getPiece(move.from);

        boolean capture =
                isCapture(before, move);

        String destination =
                Move.squareName(move.to);

        StringBuilder result =
                new StringBuilder();

       //Pawn
        if (Character.toLowerCase(piece) == 'p') {

            if (capture) {
                char fromFile =
                        Move.squareName(move.from).charAt(0);

                result.append(fromFile);
                result.append("x");
            }

            result.append(destination);

        }

       //other than pawn
        else {

            result.append(pieceSymbol(piece));

            if (capture) {
                result.append("x");
            }

            result.append(destination);
        }

        //Promotion
        if (move.promotion != '\0') {

            result.append("=");

            result.append(
                    pieceSymbol(move.promotion)
            );
        }

        //Check or checkmate
        return addCheckSuffix(
                before,
                move,
                result.toString()
        );
    }

    private static boolean isCapture(
            Board board,
            Move move) {

        if (move.enPassant) {
            return true;
        }

        return board.getPiece(move.to) != '.';
    }

    private static String addCheckSuffix(
            Board before,
            Move move,
            String notation) {

        Board after = before.copy();

        MoveGenerator.makeMove(
                after,
                move
        );

        boolean opponentIsWhite =
                after.isWhiteToMove();

        boolean inCheck =
                MoveGenerator.isKingInCheck(
                        after,
                        opponentIsWhite
                );

        if (!inCheck) {
            return notation;
        }

        boolean hasLegalMoves =
                !MoveGenerator
                        .generateLegalMoves(after)
                        .isEmpty();

        if (hasLegalMoves) {
            return notation + "+";
        }

        return notation + "#";
    }

    public static String pieceSymbol(char piece) {

        return switch (piece) {

            // White
            case 'K' -> "♔";
            case 'Q' -> "♕";
            case 'R' -> "♖";
            case 'B' -> "♗";
            case 'N' -> "♘";
            case 'P' -> "♙";

            // Black
            case 'k' -> "♚";
            case 'q' -> "♛";
            case 'r' -> "♜";
            case 'b' -> "♝";
            case 'n' -> "♞";
            case 'p' -> "♟";

            default -> "";
        };
    }
}