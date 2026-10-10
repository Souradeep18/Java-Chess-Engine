package com.chess.engine.engine;

public class Evaluation {
	
	public static int evaluate(Board board) {

        int score = 0;

        for (int i = 0; i < 64; i++) {

            char piece = board.getPiece(i);

            if (piece == '.') {
                continue;
            }
            
            int value = switch (Character.toLowerCase(piece)) {

            case 'p' -> 100;
            case 'n' -> 320;
            case 'b' -> 330;
            case 'r' -> 500;
            case 'q' -> 900;
            case 'k' -> 20000;

            default -> 0;
        };

        if (Character.isUpperCase(piece)) {
            score += value;
        } else {
            score -= value;
        }
    }

          return score;
    }


}
