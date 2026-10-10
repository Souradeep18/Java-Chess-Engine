package com.chess.engine.analysis;

import java.util.List;
import java.util.ArrayList;

import com.chess.engine.engine.Board;
import com.chess.engine.engine.ChessEngine;
import com.chess.engine.engine.Evaluation;
import com.chess.engine.engine.Move;
import com.chess.engine.engine.MoveGenerator;

public class GameAnlyser {
	
	private final ChessEngine engine ;

    public GameAnlyser(int depth) {
        engine = new ChessEngine();
    }
    
    public List<String> analyzeGame(Board startingPosition,List<Move> moves) {
    	List<String> results = new ArrayList<>();

        Board board = startingPosition.copy();

        int moveNumber = 1;

        for (Move playedMove : moves) {

            int before =Evaluation.evaluate(board);

            Move bestMove =
                    engine.findBestMove(board, before);

            if (bestMove == null) {
                break;
            }
            
            Board playedBoard = board.copy();

            MoveGenerator.makeMove(playedBoard,playedMove);
            
            int after =
                    Evaluation.evaluate(playedBoard);

            int loss;

            if (board.isWhiteToMove()) {
                loss = before - after;
            } 
            else {
                loss = after - before;
            }

            String classification;
            
            if (loss <= 20) {
                classification = "GOOD";
            } 
            else if (loss <= 80) {
                classification = "INACCURACY";
            } 
            else if (loss <= 250) {
                classification = "MISTAKE";
            } 
            else {
                classification = "BLUNDER";
            }
            
            results.add(moveNumber +". " +playedMove +" -> " +classification +" (loss: " +loss +")");

            board = playedBoard;

            if (!board.isWhiteToMove()) {
                moveNumber++;
            }
        }

        return results;
    }
    
}


