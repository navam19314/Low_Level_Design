package com.conceptcoding.interviewquestions.snakeNladder;

import java.util.Deque;
import java.util.LinkedList;

public class Game {

    Board board;
    Dice dice;
    Deque<Player> playersList = new LinkedList<>();
    Player winner;

    public Game() {
        initializeGame();
    }

    private void initializeGame() {
        board = new Board(10, 5, 4);
        dice = new Dice(1);
        winner = null;
        addPlayers();
    }

    private void addPlayers() {
        Player player1 = new Player("Player-1", 0);
        Player player2 = new Player("Player-2", 0);
        playersList.add(player1);
        playersList.add(player2);
    }

    public void startGame() {
        while (winner == null) {
            // Step 1: Find whose turn it is
            Player currentPlayer = findPlayerTurn();
            System.out.println("Player turn: " + currentPlayer.id + 
                             " current position is: " + currentPlayer.currentPosition);

            // Step 2: Roll the dice
            int diceValue = dice.rollDice();

            // Step 3: Calculate new position
            int newPosition = currentPlayer.currentPosition + diceValue;
            newPosition = jumpCheck(newPosition);
            currentPlayer.currentPosition = newPosition;

            System.out.println("Player turn: " + currentPlayer.id + 
                             " new Position is: " + newPosition);
            
            // Step 4: Check for winning condition
            int boardSize = board.cells.length * board.cells.length;
            if (newPosition >= boardSize - 1) {
                winner = currentPlayer;
            }
        }
        System.out.println("\n===> The Winner is: " + winner.id);
    }


    private Player findPlayerTurn() {
        // Rotate players: remove from front and add to back
        Player currentPlayer = playersList.removeFirst();
        playersList.addLast(currentPlayer);
        return currentPlayer;
    }

    private int jumpCheck(int newPosition) {
        int boardSize = board.cells.length * board.cells.length;
        
        // If position exceeds board, return as is
        if (newPosition > boardSize - 1) {
            return newPosition;
        }

        // Check if cell has a snake or ladder
        Cell cell = board.getCell(newPosition);
        if (cell.jump != null && cell.jump.start == newPosition) {
            String jumpType = (cell.jump.start < cell.jump.end) ? "Ladder" : "Snake";
            System.out.println("[+] Jump done by: " + jumpType);
            return cell.jump.end;
        }
        
        return newPosition;
    }
}
