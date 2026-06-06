package com.conceptcoding.interviewquestions.hello_all_questions.tictactoe;

import com.conceptcoding.interviewquestions.hello_all_questions.tictactoe.model.Player;

public class TicTacToeDriver {

    public static void main(String[] args) {
        scenarioXWins();
        scenarioDraw();
        scenarioInvalidMoves();
    }

    // X wins via top row: X X X / O O . / . . .
    private static void scenarioXWins() {
        System.out.println("=== X wins (top row) ===");
        TicTacToeGame g = new TicTacToeGame("Alice", "Bob");
        Player x = g.getPlayerX(), o = g.getPlayerO();

        g.makeMove(x, 0, 0);
        g.makeMove(o, 1, 0);
        g.makeMove(x, 0, 1);
        g.makeMove(o, 1, 1);
        g.makeMove(x, 0, 2);

        System.out.println(g.getBoard().render());
        System.out.println("state: " + g.getGameState() + ", winner: " + g.getWinner());
        System.out.println();
    }

    // Draw: X O X / X X O / O X O
    private static void scenarioDraw() {
        System.out.println("=== Draw ===");
        TicTacToeGame g = new TicTacToeGame("Alice", "Bob");
        Player x = g.getPlayerX(), o = g.getPlayerO();

        g.makeMove(x, 0, 0); g.makeMove(o, 0, 1);
        g.makeMove(x, 0, 2); g.makeMove(o, 1, 2);
        g.makeMove(x, 1, 0); g.makeMove(o, 2, 0);
        g.makeMove(x, 1, 1); g.makeMove(o, 2, 2);
        g.makeMove(x, 2, 1);

        System.out.println(g.getBoard().render());
        System.out.println("state: " + g.getGameState());
        System.out.println();
    }

    // Edge cases: wrong player, occupied cell, out of bounds, move after game over
    private static void scenarioInvalidMoves() {
        System.out.println("=== Invalid moves (all return false) ===");
        TicTacToeGame g = new TicTacToeGame("Alice", "Bob");
        Player x = g.getPlayerX(), o = g.getPlayerO();

        System.out.println("wrong player:   " + g.makeMove(o, 0, 0));   // false — X's turn
        g.makeMove(x, 0, 0);
        System.out.println("occupied cell:  " + g.makeMove(o, 0, 0));   // false — taken
        System.out.println("out of bounds:  " + g.makeMove(o, 5, 5));   // false — OOB

        // trigger a win then try to move
        g.makeMove(o, 1, 0); g.makeMove(x, 0, 1);
        g.makeMove(o, 1, 1); g.makeMove(x, 0, 2);   // X wins
        System.out.println("after game over:" + g.makeMove(o, 2, 2));   // false — WON
        System.out.println();
    }
}
