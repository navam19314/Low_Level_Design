package com.conceptcoding.interviewquestions.tictactoe;

import com.conceptcoding.interviewquestions.tictactoe.model.*;
import org.antlr.v4.runtime.misc.Pair;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

public class TicTacToeGame {

    Deque<Player> players;
    Board gameBoard;
    Player winner;


    public void initializeGame() {
        // Step 1: Create 2 players with their playing pieces
        players = new LinkedList<>();
        PlayingPieceX crossPiece = new PlayingPieceX();
        Player player1 = new Player("Player1", crossPiece);

        PlayingPieceO noughtsPiece = new PlayingPieceO();
        Player player2 = new Player("Player2", noughtsPiece);

        players.add(player1);
        players.add(player2);

        // Step 2: Initialize board of size 3x3
        gameBoard = new Board(3);
    }

    public GameStatus startGame() {
        boolean gameInProgress = true;
        
        while (gameInProgress) {
            // Step 1: Get current player (remove from front of queue)
            Player currentPlayer = players.removeFirst();

            // Step 2: Display board and check for free spaces
            gameBoard.printBoard();
            List<Pair<Integer, Integer>> freeSpaces = gameBoard.getFreeCells();
            if (freeSpaces.isEmpty()) {
                gameInProgress = false;
                continue; // No free spaces - game ends in draw
            }

            // Step 3: Read user input for row and column
            System.out.print("Player: " + currentPlayer.name + " - Please enter [row, column]: ");
            Scanner inputScanner = new Scanner(System.in);
            String input = inputScanner.nextLine();
            String[] values = input.split(",");
            int inputRow = Integer.parseInt(values[0]);
            int inputColumn = Integer.parseInt(values[1]);

            // Step 4: Place the piece on the board
            boolean isValidMove = gameBoard.addPiece(inputRow, inputColumn, currentPlayer.playingPiece);
            if (!isValidMove) {
                // Invalid move: cell already occupied, player must choose another cell
                System.out.println("Incorrect position chosen, try again!");
                players.addFirst(currentPlayer); // Add player back to front of queue
                continue;
            }
            players.addLast(currentPlayer); // Add player to end of queue for next turn

            // Step 5: Check if this move is a winning move
            boolean isWinner = checkForWinner(inputRow, inputColumn, currentPlayer.playingPiece.pieceType);
            if (isWinner) {
                gameBoard.printBoard();
                winner = currentPlayer;
                return GameStatus.WIN;
            }
        }

        return GameStatus.DRAW;
    }

    public boolean checkForWinner(int row, int column, PieceType pieceType) {
        boolean rowMatch = true;
        boolean columnMatch = true;
        boolean diagonalMatch = true;
        boolean antiDiagonalMatch = true;

        // Check if entire row matches
        for (int i = 0; i < gameBoard.size; i++) {
            if (gameBoard.board[row][i] == null || gameBoard.board[row][i].pieceType != pieceType) {
                rowMatch = false;
                break;
            }
        }

        // Check if entire column matches
        for (int i = 0; i < gameBoard.size; i++) {
            if (gameBoard.board[i][column] == null || gameBoard.board[i][column].pieceType != pieceType) {
                columnMatch = false;
                break;
            }
        }

        // Check if main diagonal matches (top-left to bottom-right)
        for (int i = 0, j = 0; i < gameBoard.size; i++, j++) {
            if (gameBoard.board[i][j] == null || gameBoard.board[i][j].pieceType != pieceType) {
                diagonalMatch = false;
                break;
            }
        }

        // Check if anti-diagonal matches (top-right to bottom-left)
        for (int i = 0, j = gameBoard.size - 1; i < gameBoard.size; i++, j--) {
            if (gameBoard.board[i][j] == null || gameBoard.board[i][j].pieceType != pieceType) {
                antiDiagonalMatch = false;
                break;
            }
        }

        return rowMatch || columnMatch || diagonalMatch || antiDiagonalMatch;
    }

}