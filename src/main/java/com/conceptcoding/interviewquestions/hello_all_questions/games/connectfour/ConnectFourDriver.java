package com.conceptcoding.interviewquestions.hello_all_questions.games.connectfour;

import com.conceptcoding.interviewquestions.hello_all_questions.games.connectfour.model.DiscColor;
import com.conceptcoding.interviewquestions.hello_all_questions.games.connectfour.model.GameState;
import com.conceptcoding.interviewquestions.hello_all_questions.games.connectfour.model.Player;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// Scripted scenarios (PlayGame is the interactive version).
public class ConnectFourDriver {

    static final Player RED = new Player("Red", DiscColor.RED);
    static final Player YELLOW = new Player("Yellow", DiscColor.YELLOW);

    public static void main(String[] args) throws Exception {
        win("vertical (column 0)", new int[] { 0, 1, 0, 1, 0, 1, 0 });
        win("horizontal (bottom row)", new int[] { 0, 0, 1, 1, 2, 2, 3 });
        win("diagonal /", new int[] { 0, 1, 1, 2, 2, 3, 2, 3, 3, 6, 3 });
        win("diagonal \\", new int[] { 6, 5, 5, 4, 4, 3, 4, 3, 3, 0, 3 });
        rejected();
        draw();
        doubleClick();
    }

    private static void win(String label, int[] columns) {
        ConnectFourGame g = new ConnectFourGame(RED, YELLOW);
        for (int i = 0; i < columns.length; i++) g.makeMove(i % 2 == 0 ? RED : YELLOW, columns[i]);
        System.out.println("=== " + label + " ===");
        g.getBoard().printBoard();
        System.out.println("  " + g.getGameState() + ", winner " + g.getWinner().getName() + " (expect WON, Red)\n");
    }

    private static void rejected() {
        System.out.println("=== Rejected moves ===");
        ConnectFourGame g = new ConnectFourGame(RED, YELLOW);
        tryIt("Yellow moves first", () -> g.makeMove(YELLOW, 0));
        tryIt("column 9", () -> g.makeMove(RED, 9));
        for (int i = 0; i < 6; i++) g.makeMove(i % 2 == 0 ? RED : YELLOW, 2);   // fill column 2
        tryIt("drop into full column 2", () -> g.makeMove(RED, 2));
        System.out.println();
    }

    // 2×4 board, filled without four in a row → DRAW
    private static void draw() {
        System.out.println("=== Draw on a tiny 2×4 board ===");
        ConnectFourGame g = new ConnectFourGame(RED, YELLOW, 2, 4);
        int[] columns = { 0, 1, 2, 3, 1, 0, 3, 2 };
        for (int i = 0; i < columns.length; i++) g.makeMove(i % 2 == 0 ? RED : YELLOW, columns[i]);
        g.getBoard().printBoard();
        System.out.println("  " + g.getGameState() + " (expect DRAW)\n");
    }

    // Red double-clicks: 20 threads send Red's move at once. Exactly one is accepted.
    private static void doubleClick() throws Exception {
        System.out.println("=== Concurrency: 20 simultaneous Red moves ===");
        ConnectFourGame g = new ConnectFourGame(RED, YELLOW);
        AtomicInteger accepted = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 20; i++) {
            final int col = i % 7;
            pool.submit(() -> {
                start.await();
                try { g.makeMove(RED, col); accepted.incrementAndGet(); } catch (RuntimeException e) { }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  accepted = " + accepted.get() + " (expect 1), turn now " + g.getCurrentPlayer().getName());
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
