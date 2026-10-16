package com.conceptcoding.interviewquestions.hello_all_questions.games.tictactoe;

import com.conceptcoding.interviewquestions.hello_all_questions.games.tictactoe.model.Symbol;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.conceptcoding.interviewquestions.hello_all_questions.games.tictactoe.model.Symbol.O;
import static com.conceptcoding.interviewquestions.hello_all_questions.games.tictactoe.model.Symbol.X;

public class TicTacToeDriver {

    public static void main(String[] args) throws Exception {
        System.out.println("=== X wins: top row ===");
        TicTacToeGame g1 = play(3, X,0,0, O,1,0, X,0,1, O,1,1, X,0,2);
        System.out.print(g1.render());
        System.out.println("  " + g1.getState() + ", winner " + g1.getWinner() + " (expect WON, X)\n");

        System.out.println("=== O wins: anti-diagonal ===");
        TicTacToeGame g2 = play(3, X,0,0, O,0,2, X,1,0, O,1,1, X,2,2, O,2,0);
        System.out.print(g2.render());
        System.out.println("  " + g2.getState() + ", winner " + g2.getWinner() + " (expect WON, O)\n");

        System.out.println("=== Draw ===");
        TicTacToeGame g3 = play(3, X,0,0, O,0,1, X,0,2, O,1,2, X,1,0, O,2,0, X,1,1, O,2,2, X,2,1);
        System.out.print(g3.render());
        System.out.println("  " + g3.getState() + " (expect DRAW)\n");

        System.out.println("=== 4×4: X wins the main diagonal ===");
        TicTacToeGame g4 = play(4, X,0,0, O,0,1, X,1,1, O,1,2, X,2,2, O,2,3, X,3,3);
        System.out.print(g4.render());
        System.out.println("  " + g4.getState() + ", winner " + g4.getWinner() + " (expect WON, X)\n");

        System.out.println("=== Rejected moves (each says why) ===");
        TicTacToeGame g = new TicTacToeGame(3);
        tryIt("O moves first", () -> g.makeMove(O, 0, 0));
        g.makeMove(X, 0, 0);
        tryIt("O plays a taken cell", () -> g.makeMove(O, 0, 0));
        tryIt("O plays off the board", () -> g.makeMove(O, 5, 5));
        tryIt("X plays twice in a row", () -> g.makeMove(X, 1, 1));
        g.makeMove(O, 1, 0); g.makeMove(X, 0, 1); g.makeMove(O, 1, 1); g.makeMove(X, 0, 2);   // X wins
        tryIt("move after the game is over", () -> g.makeMove(O, 2, 2));
        tryIt("a 2×2 board", () -> new TicTacToeGame(2));

        doubleClick();
    }

    // X double-clicks: 20 threads send X's move at once. Exactly one is accepted.
    private static void doubleClick() throws Exception {
        System.out.println("\n=== Concurrency: 20 simultaneous X moves ===");
        TicTacToeGame g = new TicTacToeGame(3);
        AtomicInteger accepted = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < 20; i++) {
            final int cell = i % 9;
            pool.submit(() -> {
                go.await();
                try { g.makeMove(X, cell / 3, cell % 3); accepted.incrementAndGet(); }
                catch (RuntimeException e) { /* not X's turn any more, or the cell is taken */ }
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  accepted = " + accepted.get() + " (expect 1), turn now " + g.getTurn() + " (expect O)");
    }

    // play(3, X,0,0, O,1,0, ...) → a game after those moves
    private static TicTacToeGame play(int size, Object... moves) {
        TicTacToeGame g = new TicTacToeGame(size);
        for (int i = 0; i < moves.length; i += 3) g.makeMove((Symbol) moves[i], (Integer) moves[i + 1], (Integer) moves[i + 2]);
        return g;
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
