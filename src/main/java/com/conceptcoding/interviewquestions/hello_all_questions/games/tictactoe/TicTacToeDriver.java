package com.conceptcoding.interviewquestions.hello_all_questions.games.tictactoe;

import com.conceptcoding.interviewquestions.hello_all_questions.games.tictactoe.model.Player;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TicTacToeDriver {

    public static void main(String[] args) throws Exception {
        xWinsTopRow();
        oWinsAntiDiagonal();
        drawGame();
        rejectedMoves();
        fourByFour();
        resetAndReplay();
        doubleClick();
    }

    private static void xWinsTopRow() {
        System.out.println("=== X wins: top row ===");
        TicTacToeGame g = new TicTacToeGame("Alice", "Bob");
        Player x = g.getPlayerX(), o = g.getPlayerO();
        g.makeMove(x, 0, 0); g.makeMove(o, 1, 0);
        g.makeMove(x, 0, 1); g.makeMove(o, 1, 1);
        g.makeMove(x, 0, 2);
        System.out.print(g.render());
        System.out.println("  " + g.getGameState() + ", winner " + g.getWinner() + "  (expect WON, Alice(X))\n");
    }

    private static void oWinsAntiDiagonal() {
        System.out.println("=== O wins: anti-diagonal ===");
        TicTacToeGame g = new TicTacToeGame("Alice", "Bob");
        Player x = g.getPlayerX(), o = g.getPlayerO();
        g.makeMove(x, 0, 0); g.makeMove(o, 0, 2);
        g.makeMove(x, 1, 0); g.makeMove(o, 1, 1);
        g.makeMove(x, 2, 2); g.makeMove(o, 2, 0);
        System.out.print(g.render());
        System.out.println("  " + g.getGameState() + ", winner " + g.getWinner() + "  (expect WON, Bob(O))\n");
    }

    private static void drawGame() {
        System.out.println("=== Draw ===");
        TicTacToeGame g = new TicTacToeGame("Alice", "Bob");
        Player x = g.getPlayerX(), o = g.getPlayerO();
        g.makeMove(x, 0, 0); g.makeMove(o, 0, 1);
        g.makeMove(x, 0, 2); g.makeMove(o, 1, 2);
        g.makeMove(x, 1, 0); g.makeMove(o, 2, 0);
        g.makeMove(x, 1, 1); g.makeMove(o, 2, 2);
        g.makeMove(x, 2, 1);
        System.out.print(g.render());
        System.out.println("  " + g.getGameState() + "  (expect DRAW)\n");
    }

    private static void rejectedMoves() {
        System.out.println("=== Rejected moves (each says why) ===");
        TicTacToeGame g = new TicTacToeGame("Alice", "Bob");
        Player x = g.getPlayerX(), o = g.getPlayerO();
        tryIt("O moves first", () -> g.makeMove(o, 0, 0));
        g.makeMove(x, 0, 0);
        tryIt("O plays a taken cell", () -> g.makeMove(o, 0, 0));
        tryIt("O plays off the board", () -> g.makeMove(o, 5, 5));
        g.makeMove(o, 1, 0); g.makeMove(x, 0, 1);
        g.makeMove(o, 1, 1); g.makeMove(x, 0, 2);          // X wins
        tryIt("move after the game is over", () -> g.makeMove(o, 2, 2));
        System.out.println();
    }

    private static void fourByFour() {
        System.out.println("=== 4×4: X wins the main diagonal (counters reach +4) ===");
        TicTacToeGame g = new TicTacToeGame("Alice", "Bob", 4);
        Player x = g.getPlayerX(), o = g.getPlayerO();
        for (int i = 0; i < 3; i++) { g.makeMove(x, i, i); g.makeMove(o, i, (i + 1) % 4); }
        g.makeMove(x, 3, 3);
        System.out.print(g.render());
        System.out.println("  " + g.getGameState() + ", winner " + g.getWinner() + "\n");
    }

    private static void resetAndReplay() {
        System.out.println("=== Reset ===");
        TicTacToeGame g = new TicTacToeGame("Alice", "Bob");
        g.makeMove(g.getPlayerX(), 1, 1);
        g.reset();
        System.out.println("  cell(1,1) = " + g.getBoard().getCell(1, 1) + ", state " + g.getGameState()
                + ", turn " + g.getCurrentPlayer() + "  (expect null, IN_PROGRESS, Alice(X))\n");
    }

    // X double-clicks: 20 threads send X's move at once. Exactly one is accepted.
    private static void doubleClick() throws Exception {
        System.out.println("=== Concurrency: 20 simultaneous X moves ===");
        TicTacToeGame g = new TicTacToeGame("Alice", "Bob");
        AtomicInteger accepted = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 20; i++) {
            final int cell = i % 9;
            pool.submit(() -> {
                start.await();
                try {
                    g.makeMove(g.getPlayerX(), cell / 3, cell % 3);
                    accepted.incrementAndGet();
                } catch (RuntimeException e) {
                    // not X's turn any more, or the cell is taken
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  accepted = " + accepted.get() + " (expect 1), turn now " + g.getCurrentPlayer());
    }

    private static void tryIt(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + ": ALLOWED ✗");
        } catch (RuntimeException e) {
            System.out.println("  " + label + ": rejected → " + e.getMessage());
        }
    }
}
