package com.conceptcoding.interviewquestions.hello_all_questions.snakeladder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SnakeLadderDriver {

    public static void main(String[] args) {
        scenarioStandardBoardConstructs();
        scenarioLadderClimbWithFixedDice();
        scenarioSnakeBite();
        scenarioOvershootSkipsTurn();
        scenarioExactWin();
        scenarioBoardRejectsBadTopology();
        scenarioFullGameRandom();
    }

    // ---- 1. Standard 100-square board constructs cleanly ----
    private static void scenarioStandardBoardConstructs() {
        System.out.println("=== Scenario 1: standard 100-square board ===");
        Board b = Board.standard100();
        System.out.println("  size: " + b.getSize() + " (expect 100)");
        System.out.println("  snakes:  " + b.snakes());
        System.out.println("  ladders: " + b.ladders());
        System.out.println();
    }

    // ---- 2. Player lands on ladder bottom → climbs ----
    private static void scenarioLadderClimbWithFixedDice() {
        System.out.println("=== Scenario 2: ladder climb (fixed dice) ===");
        // Tiny board, single ladder 3 → 12. Player rolls 3 → lands on 3 → climbs to 12.
        Board b = new Board(20, Map.of(), Map.of(3, 12));
        Dice d = new FixedSequenceDice(3, 1);   // player A rolls 3, player B rolls 1
        Player a = new Player("A", "Alice"), bP = new Player("B", "Bob");
        Game g = new Game(b, d, List.of(a, bP));
        g.playTurn();   // Alice: 3 → ladder → 12
        g.playTurn();   // Bob:   1
        System.out.println("  " + a + " (expect Alice@12)");
        System.out.println("  " + bP + " (expect Bob@1)");
        System.out.println();
    }

    // ---- 3. Player lands on snake head → drops ----
    private static void scenarioSnakeBite() {
        System.out.println("=== Scenario 3: snake bite ===");
        // Single snake 14 → 4. Alice rolls 6 then 6 → 12, Bob rolls 5 then 6 → lands on 11 first then 17? no — we set up cleanly.
        // Simplest: Alice rolls 5 then 5 → lands on 10. Snake at 10 → 2. So Alice ends at 2 after second turn.
        Board b = new Board(30, Map.of(10, 2), Map.of());
        Dice d = new FixedSequenceDice(5, 1, 5, 1);   // alice=5, bob=1, alice=5, bob=1
        Player a = new Player("A", "Alice"), bP = new Player("B", "Bob");
        Game g = new Game(b, d, List.of(a, bP));
        g.playTurn();   // Alice 0+5=5
        g.playTurn();   // Bob   0+1=1
        g.playTurn();   // Alice 5+5=10 → snake → 2
        System.out.println("  " + a + " (expect Alice@2 — snake from 10 dropped her)");
        System.out.println();
    }

    // ---- 4. Overshooting the last square = stay put ----
    private static void scenarioOvershootSkipsTurn() {
        System.out.println("=== Scenario 4: overshoot the last square ===");
        // Board size 10. Alice at 8 needs exactly 2. Rolls 5 → overshoots → stays at 8.
        Board b = new Board(10, Map.of(), Map.of());
        Dice d = new FixedSequenceDice(8, 1, 5, 1);   // alice=8 (lands on 8), bob=1, alice=5 (overshoots), bob=1
        Player a = new Player("A", "Alice"), bP = new Player("B", "Bob");
        Game g = new Game(b, d, List.of(a, bP));
        g.playTurn();   // Alice 0+8 = 8
        g.playTurn();   // Bob   0+1 = 1
        g.playTurn();   // Alice 8+5 = 13 — overshoots → stays at 8
        System.out.println("  " + a + " (expect Alice@8 — 8+5=13 overshoots 10)");
        System.out.println();
    }

    // ---- 5. Exact-size roll wins the game ----
    private static void scenarioExactWin() {
        System.out.println("=== Scenario 5: exact-size win ===");
        Board b = new Board(10, Map.of(), Map.of());
        Dice d = new FixedSequenceDice(6, 1, 4);   // alice=6, bob=1, alice=4 (6+4=10)
        Player a = new Player("A", "Alice"), bP = new Player("B", "Bob");
        Game g = new Game(b, d, List.of(a, bP));
        g.playToFinish(20);
        System.out.println("  status: " + g.getStatus() + " (expect FINISHED)");
        System.out.println("  winner: " + g.getWinner().getName() + " (expect Alice)");
        System.out.println();
    }

    // ---- 6. Board construction rejects bad topology ----
    private static void scenarioBoardRejectsBadTopology() {
        System.out.println("=== Scenario 6: bad topology rejected ===");
        try {
            // Snake head 5 → tail 8 is invalid (head must be > tail)
            new Board(20, Map.of(5, 8), Map.of());
        } catch (IllegalArgumentException e) {
            System.out.println("  inverted snake rejected: " + e.getMessage());
        }
        try {
            // Square 10 is both snake head AND ladder bottom — overlap forbidden
            new Board(20, Map.of(10, 2), Map.of(10, 18));
        } catch (IllegalArgumentException e) {
            System.out.println("  overlap rejected:        " + e.getMessage());
        }
        System.out.println();
    }

    // ---- 7. Full random game runs to a winner ----
    private static void scenarioFullGameRandom() {
        System.out.println("=== Scenario 7: full game with real random dice ===");
        Board b = Board.standard100();
        Dice d = new StandardDice();
        Player a = new Player("A", "Alice"), bP = new Player("B", "Bob"), c = new Player("C", "Carol");
        Game g = new Game(b, d, List.of(a, bP, c));
        g.playToFinish(2000);
        if (g.getStatus() == Game.Status.FINISHED) {
            System.out.println("  winner: " + g.getWinner().getName());
        } else {
            System.out.println("  game did not finish within 2000 turns (extremely unlikely)");
        }
        // Show last 5 events from the log
        var log = g.getEventLog();
        System.out.println("  last 5 events:");
        for (int i = Math.max(0, log.size() - 5); i < log.size(); i++) {
            System.out.println("    " + log.get(i));
        }
        System.out.println();
    }
}
