package com.chess.engine.engine;

import java.util.List;
import java.util.ArrayList;

public class MoveGenerator {
	
	public static List<Move> generateLegalMoves(Board board) {

		if (board.isKingOfTheHill() && KingOfTheHill.isGameWon(board)) {
			return new ArrayList<>(); // a king is on the hill: the game is over
		}

		if (board.isRacingKings() && RacingKings.isDecided(board)) {
			return new ArrayList<>(); // a king has reached the 8th rank: the game is over
		}

		if (board.isAtomic()) {
			return AtomicChess.generateLegalMoves(board);
		}

		if (board.isAntiChess()) {
			return AntiChess.generateLegalMoves(board);
		}

        List<Move> pseudo = generatePseudoLegalMoves(board);
        List<Move> legal = new ArrayList<>();

        for (Move move : pseudo) {

            Board copy = board.copy();

            makeMove(copy, move);

            boolean whiteMoved = board.isWhiteToMove();

            // Racing Kings: neither king may be in check after any move
            if (!isKingInCheck(copy, whiteMoved)
                    && !(board.isRacingKings() && isKingInCheck(copy, !whiteMoved))) {
                legal.add(move);
            }
        }

        return legal;
    }
	
	public static List<Move> generatePseudoLegalMoves(Board board) {

        List<Move> moves = new ArrayList<>();

        for (int square = 0; square < 64; square++) {

            char piece = board.getPiece(square);

            if (piece == '.') {
                continue;
            }

            boolean whitePiece = Character.isUpperCase(piece);

            if (whitePiece != board.isWhiteToMove()) {
                continue;
            }
            
            switch (Character.toLowerCase(piece)) {

            case 'p' -> generatePawnMoves(board, square, moves);
            case 'n' -> generateKnightMoves(board, square, moves);
            case 'b' -> generateSlidingMoves(
                    board,
                    square,
                    moves,
                    new int[]{9, 7, -9, -7}
            );
            case 'r' -> generateSlidingMoves(
                    board,
                    square,
                    moves,
                    new int[]{8, -8, 1, -1}
            );
            
            case 'q' -> generateSlidingMoves(
                    board,
                    square,
                    moves,
                    new int[]{
                            8, -8, 1, -1,9, 7, -9, -7
                    }
            );
            case 'k' -> generateKingMoves(board, square, moves);
        }
    }

        return moves;
	}
	
	private static void generatePawnMoves(
            Board board,
            int square,
            List<Move> moves) {

        char pawn = board.getPiece(square);

        boolean white = Character.isUpperCase(pawn);

        int direction = white ? 8 : -8;

        int rank = square / 8;
        int file = square % 8;

        int oneForward = square + direction;
        
        if (oneForward >= 0 &&
                oneForward < 64 &&
                board.getPiece(oneForward) == '.') {

            addPawnMove(board, square, oneForward, moves);

            int startingRank = white ? 1 : 6;

            int twoForward = square + 2 * direction;

            if ((rank == startingRank
	                    || HordeChess.canDoubleStepFromFirstRank(board, white, rank)) &&
                    board.getPiece(twoForward) == '.') {

                moves.add(new Move(square, twoForward));
            }
        }
        
        int[] captures = {file - 1, file + 1};

        for (int targetFile : captures) {

            if (targetFile < 0 || targetFile > 7) {
                continue;
            }

            int target = square + direction
                    + (targetFile - file);

            if (target < 0 || target >= 64) {
                continue;
            }

            char targetPiece = board.getPiece(target);

            if (targetPiece != '.' &&
                    Character.isUpperCase(targetPiece) != white) {

                addPawnMove(board, square, target, moves);
            }
            
            if (target == board.getEnPassantSquare()) {

                moves.add(
                        new Move(square,target,'\0',false,false,true));
            }
        }
    }
	
	 private static void addPawnMove(Board board,int from,int to,List<Move> moves) {

	        boolean white =
	                Character.isUpperCase(board.getPiece(from));

	        int rank = to / 8;
	        
	        if (rank == 7 || rank == 0) {

	            char[] promotions =
	                    white
	                            ? new char[]{'Q', 'R', 'B', 'N'}
	                            : new char[]{'q', 'r', 'b', 'n'};
	            for (char promotion : promotions) {
	                moves.add(new Move(from,to,promotion,false,false,false));
	            }

	        } else {
	            moves.add(new Move(from, to));
	        }
	    }
	 
	 private static void generateKnightMoves(Board board,int square,List<Move> moves) {

	        int file = square % 8;

	        int[] offsets = {17, 15, 10, 6,-6, -10, -15, -17};
	        
	        for (int offset : offsets) {

	            int target = square + offset;

	            if (target < 0 || target >= 64) {
	                continue;
	            }

	            int targetFile = target % 8;

	            if (Math.abs(targetFile - file) > 2) {
	                continue;
	            }

	            char targetPiece = board.getPiece(target);

	            if (targetPiece == '.' ||
	                    Character.isUpperCase(targetPiece) != Character.isUpperCase(board.getPiece(square))) {

	                moves.add(new Move(square, target));
	            }
	        }
	    }
	 
	 private static void generateSlidingMoves(Board board,int square,List<Move> moves,int[] directions) {

	        boolean white =
	                Character.isUpperCase(board.getPiece(square));

	        for (int direction : directions) {
	        	int current = square;

	            while (true) {

	                int next = current + direction;

	                if (next < 0 || next >= 64) {
	                    break;
	                }

	                int currentFile = current % 8;
	                int nextFile = next % 8;

	                if (Math.abs(nextFile - currentFile) > 1) {
	                    break;
	                }

	                char target = board.getPiece(next);
	                
	                if (target == '.') {

	                    moves.add(new Move(square, next));
	                    current = next;

	                } else {

	                    if (Character.isUpperCase(target) != white) {
	                        moves.add(new Move(square, next));
	                    }

	                    break;
	                }
	            }
	        }
	      }
	 
	 private static void generateKingMoves(Board board, int square, List<Move> moves) {

		    int[] directions = {8, -8, 1, -1, 9, 7, -9, -7};
		    boolean white = Character.isUpperCase(board.getPiece(square));

		    // Enemy color attacker flag: 
		    // If king is white, we check if target is attacked by BLACK (byWhite = false)
		    // If king is black, we check if target is attacked by WHITE (byWhite = true)
		    boolean attackedByEnemy = !white; 

		    for (int direction : directions) {
		        int target = square + direction;

		        if (target < 0 || target >= 64) {
		            continue;
		        }

		        if (Math.abs((target % 8) - (square % 8)) > 1) {
		            continue;
		        }

		        char piece = board.getPiece(target);

		        // 1. Target square must be empty or contain an enemy piece
		        if (piece == '.' || Character.isUpperCase(piece) != white) {
		            
		            // 2. CRITICAL FIX: Ensure king does not move onto a square attacked by opponent
		            // atomic chess: legality (including touching kings) is decided by AtomicChess
		            // anti-chess: no king safety at all
		            if (board.isAtomic() || board.isAntiChess() || !isSquareAttacked(board, target, attackedByEnemy)) {
		                moves.add(new Move(square, target));
		            }
		        }
		    }

		    // --- CASTLING --- (not allowed in anti-chess)
		    if (board.isAntiChess()) {
		        return;
		    }

		    if (white && square == 4) {

		        if (board.canWhiteCastleKingSide() &&
		                board.getPiece(5) == '.' &&
		                board.getPiece(6) == '.' &&
		                !isSquareAttacked(board, 4, false) &&
		                !isSquareAttacked(board, 5, false) &&
		                !isSquareAttacked(board, 6, false)) {

		            moves.add(new Move(4, 6, '\0', true, false, false));
		        }

		        if (board.canWhiteCastleQueenSide() &&
		                board.getPiece(3) == '.' &&
		                board.getPiece(2) == '.' &&
		                board.getPiece(1) == '.' &&
		                !isSquareAttacked(board, 4, false) &&
		                !isSquareAttacked(board, 3, false) &&
		                !isSquareAttacked(board, 2, false)) {

		            moves.add(new Move(4, 2, '\0', false, true, false));
		        }

		    } else if (!white && square == 60) {

		        if (board.canBlackCastleKingSide() &&
		                board.getPiece(61) == '.' &&
		                board.getPiece(62) == '.' &&
		                !isSquareAttacked(board, 60, true) &&
		                !isSquareAttacked(board, 61, true) &&
		                !isSquareAttacked(board, 62, true)) {

		            moves.add(new Move(60, 62, '\0', true, false, false));
		        }

		        if (board.canBlackCastleQueenSide() &&
		                board.getPiece(59) == '.' &&
		                board.getPiece(58) == '.' &&
		                board.getPiece(57) == '.' &&
		                !isSquareAttacked(board, 60, true) &&
		                !isSquareAttacked(board, 59, true) &&
		                !isSquareAttacked(board, 58, true)) {

		            moves.add(new Move(60, 58, '\0', false, true, false));
		        }
		    }
		}
	 public static void makeMove(Board board, Move move) {

	        // Atomic chess: a capture explodes (remember it before the board changes)
	        boolean atomicCapture = board.isAtomic()
	                && (board.getPiece(move.to) != '.' || move.enPassant);

	        char piece = board.getPiece(move.from);

	        board.setPiece(move.from, '.');
	        
	        //en-passant captures
	        
	        if (move.enPassant) {

	            int capturedPawn =
	                    move.to + (Character.isUpperCase(piece) ? -8 : 8);

	            board.setPiece(capturedPawn, '.');
	        }
	        
	        //castling
	        
	        if (move.castleKingSide) {

	            if (Character.isUpperCase(piece)) {

	                board.setPiece(7, '.');
	                board.setPiece(5, 'R');

	            } else {

	                board.setPiece(63, '.');
	                board.setPiece(61, 'r');
	            }
	        }
	        
	        if (move.castleQueenSide) {

	            if (Character.isUpperCase(piece)) {

	                board.setPiece(0, '.');
	                board.setPiece(3, 'R');

	            } else {

	                board.setPiece(56, '.');
	                board.setPiece(59, 'r');
	            }
	        }
	        
	        if (move.promotion != '\0') {
	            piece = move.promotion;
	        }

	        board.setPiece(move.to, piece);
	        
	        //castling rights
	        
	        if (piece == 'K') {
	            board.setWhiteKingSideCastle(false);
	            board.setWhiteQueenSideCastle(false);
	        }

	        if (piece == 'k') {
	            board.setBlackKingSideCastle(false);
	            board.setBlackQueenSideCastle(false);
	        }

	        if (move.from == 0 || move.to == 0) {
	            board.setWhiteQueenSideCastle(false);
	        }

	        if (move.from == 7 || move.to == 7) {
	            board.setWhiteKingSideCastle(false);
	        }

	        if (move.from == 56 || move.to == 56) {
	            board.setBlackQueenSideCastle(false);
	        }
	        
	        if (move.from == 63 || move.to == 63) {
	            board.setBlackKingSideCastle(false);
	        }
	        
	        //en-passant target
	        
	        board.setEnPassantSquare(-1);

	        if (Character.toLowerCase(piece) == 'p' &&
	                Math.abs(move.to - move.from) == 16 &&
	                HordeChess.doubleStepSetsEnPassant(board, move.from)) {

	            board.setEnPassantSquare(
	                    (move.from + move.to) / 2
	            );
	        }

	        if (atomicCapture) {
	            AtomicChess.explode(board, move.to);
	        }

	        board.setWhiteToMove(!board.isWhiteToMove());

	        // Three-Check: the side that just moved scores a check if it gave one
	        if (board.isThreeCheck() && isKingInCheck(board, board.isWhiteToMove())) {
	            if (board.isWhiteToMove()) {
	                board.setBlackChecks(board.getBlackChecks() + 1);
	            } else {
	                board.setWhiteChecks(board.getWhiteChecks() + 1);
	            }
	        }
	    }
	 
	 public static boolean isKingInCheck(
	            Board board,
	            boolean whiteKing) {

	        if (board.isAtomic()) {
	            return AtomicChess.isKingInCheck(board, whiteKing);
	        }

	        // Anti-chess has no check
	        if (board.isAntiChess()) {
	            return false;
	        }

	        char king = whiteKing ? 'K' : 'k';

	        for (int i = 0; i < 64; i++) {

	            if (board.getPiece(i) == king) {
	                return isSquareAttacked(board, i, !whiteKing);
	            }
	        }

	        // Horde chess: White has no king, so it can never be in check
	        return !(board.isHorde() && whiteKing);
	    }
	 
	 public static boolean isSquareAttacked(Board board,int square,boolean byWhite) {

	        if (board.isAtomic()) {
	            return AtomicChess.isSquareAttacked(board, square, byWhite);
	        }


	        for (int i = 0; i < 64; i++) {

	            char piece = board.getPiece(i);

	            if (piece == '.') {
	                continue;
	            }

	            if (Character.isUpperCase(piece) != byWhite) {
	                continue;
	            }
	            
	            char type = Character.toLowerCase(piece);

	            if (type == 'p') {

	                int direction = byWhite ? 8 : -8;

	                int file = i % 8;

	                int left = i + direction - 1;
	                int right = i + direction + 1;

	                if (file > 0 && left == square) {
	                    return true;
	                }

	                if (file < 7 && right == square) {
	                    return true;
	                }
	            }
	            
	            if (type == 'n') {

	                int[] offsets = {
	                        17, 15, 10, 6,
	                        -6, -10, -15, -17
	                };

	                for (int offset : offsets) {

	                    int target = i + offset;

	                    if (target >= 0 &&
	                            target < 64 &&
	                            Math.abs(target % 8 - i % 8) <= 2 &&
	                            target == square) {

	                        return true;
	                    }
	                }
	            }
	            
	            if (type == 'k') {

	                if (Math.abs(square % 8 - i % 8) <= 1 &&
	                        Math.abs(square / 8 - i / 8) <= 1) {

	                    return true;
	                }
	            }

	            if (type == 'b' || type == 'r' || type == 'q') {

	                int[] dirs;

	                if (type == 'b') {
	                    dirs = new int[]{9, 7, -9, -7};
	                } 
	                else if (type == 'r') {
	                    dirs = new int[]{8, -8, 1, -1};
	                } 
	                else {
	                    dirs = new int[]{8, -8, 1, -1,9, 7, -9, -7};
	                }
	                
	                for (int direction : dirs) {

	                    int current = i;

	                    while (true) {

	                        int next = current + direction;

	                        if (next < 0 || next >= 64) {
	                            break;
	                        }

	                        if (Math.abs(next % 8 -
	                                current % 8) > 1) {
	                            break;
	                        }

	                        if (next == square) {
	                            return true;
	                        }
	                        
	                        if(board.getPiece(next) != '.') {
	                            break;
	                        }

	                        current = next;
	                    }
	                }
	            }
	        }

	        return false;
	    }
}