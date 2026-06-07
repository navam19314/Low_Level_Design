package com.conceptcoding.interviewquestions.hello_all_questions.tictactoe;

import com.conceptcoding.interviewquestions.hello_all_questions.tictactoe.model.Player;

/*
 * Driver — trace through concrete scenarios to verify logic (Step 4 of framework).
 * In an interview, walk the interviewer through scenario 1 out loud while you code it.
 */
public class TicTacToeDriver {

    public static void main(String[] args) {
        xWinsTopRow();
        drawGame();
        invalidMovesReturnFalse();
        resetAndReplay();
    }

    // Trace: X wins via top row — X X X / O O . / . . .
    private static void xWinsTopRow() {
        System.out.println("=== X wins — top row ===");
        TicTacToeGame g = new TicTacToeGame("Alice", "Bob");
        Player x = g.getPlayerX(), o = g.getPlayerO();

        g.makeMove(x, 0, 0);
        g.makeMove(o, 1, 0);
        g.makeMove(x, 0, 1);
        g.makeMove(o, 1, 1);
        g.makeMove(x, 0, 2);   // completes row 0

        System.out.println(g.getBoard().render());
        System.out.println("state:  " + g.getGameState() + "  (expect WON)");
        System.out.println("winner: " + g.getWinner()    + "  (expect Alice(X))\n");
    }

    // Trace: X O X / X X O / O X O — no line, draw
    private static void drawGame() {
        System.out.println("=== Draw ===");
        TicTacToeGame g = new TicTacToeGame("Alice", "Bob");
        Player x = g.getPlayerX(), o = g.getPlayerO();

        g.makeMove(x, 0, 0); g.makeMove(o, 0, 1);
        g.makeMove(x, 0, 2); g.makeMove(o, 1, 2);
        g.makeMove(x, 1, 0); g.makeMove(o, 2, 0);
        g.makeMove(x, 1, 1); g.makeMove(o, 2, 2);
        g.makeMove(x, 2, 1);

        System.out.println(g.getBoard().render());
        System.out.println("state: " + g.getGameState() + "  (expect DRAW)\n");
    }

    // All four rejection guards: wrong player, occupied, out-of-bounds, game over
    private static void invalidMovesReturnFalse() {
        System.out.println("=== Invalid moves — all return false ===");
        TicTacToeGame g = new TicTacToeGame("Alice", "Bob");
        Player x = g.getPlayerX(), o = g.getPlayerO();

        System.out.println("wrong player  → " + g.makeMove(o, 0, 0));   // false: X's turn
        g.makeMove(x, 0, 0);
        System.out.println("occupied cell → " + g.makeMove(o, 0, 0));   // false: taken
        System.out.println("out of bounds → " + g.makeMove(o, 5, 5));   // false: OOB

        // fast-forward to game over
        g.makeMove(o, 1, 0); g.makeMove(x, 0, 1);
        g.makeMove(o, 1, 1); g.makeMove(x, 0, 2);  // X wins
        System.out.println("game over     → " + g.makeMove(o, 2, 2) + "\n");  // false: WON
    }

    // Reset clears board, resets turn and state
    private static void resetAndReplay() {
        System.out.println("=== Reset ===");
        TicTacToeGame g = new TicTacToeGame("Alice", "Bob");
        Player x = g.getPlayerX(), o = g.getPlayerO();

        g.makeMove(x, 1, 1);
        g.makeMove(o, 0, 0);
        System.out.println("before reset  — cell(1,1): " + g.getBoard().getCell(1, 1)
                + ",  state: " + g.getGameState());

        g.reset();
        System.out.println("after  reset  — cell(1,1): " + g.getBoard().getCell(1, 1)
                + ",  state: " + g.getGameState()
                + ",  turn: " + g.getCurrentPlayer() + "\n");
    }
}
