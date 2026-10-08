package com.conceptcoding.interviewquestions.hello_all_questions.composition.kanban;

import com.conceptcoding.interviewquestions.hello_all_questions.composition.kanban.model.Board;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.kanban.model.Card;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.kanban.model.Lane;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class KanbanDriver {

    public static void main(String[] args) throws Exception {
        KanbanService app = new KanbanService();
        Board board = app.createBoard("Sprint 12", "priya");
        String b = board.getId();
        app.addMember(b, "priya", "rahul");
        Lane todo  = app.addLane(b, "priya", "To Do", 0);
        Lane doing = app.addLane(b, "priya", "Doing", 2);        // WIP limit 2
        Lane done  = app.addLane(b, "priya", "Done", 0);

        Card login  = app.createCard(b, "priya", todo.getId(), "Login page", "");
        Card api    = app.createCard(b, "rahul", todo.getId(), "Quote API", "");
        Card tests  = app.createCard(b, "priya", todo.getId(), "Write tests", "");
        Card deploy = app.createCard(b, "priya", todo.getId(), "Deploy", "");
        System.out.println("=== New board ===\n" + board.render());

        System.out.println("=== Reorder inside To Do: Deploy to the top ===");
        app.moveCard(b, "priya", deploy.getId(), todo.getId(), 0);
        System.out.print(board.render());

        System.out.println("\n=== Move across lanes + assign ===");
        app.moveCard(b, "rahul", api.getId(), doing.getId(), 0);
        app.assignCard(b, "rahul", api.getId(), "rahul");
        app.moveCard(b, "priya", login.getId(), doing.getId(), 1);
        System.out.print(board.render());

        System.out.println("\n=== Rules ===");
        tryIt("move into Doing past its WIP limit of 2", () -> app.moveCard(b, "priya", tests.getId(), doing.getId(), 0));
        tryIt("position 9 in the empty Done lane", () -> app.moveCard(b, "priya", tests.getId(), done.getId(), 9));
        tryIt("non-member moves a card", () -> app.moveCard(b, "stranger", tests.getId(), done.getId(), 0));
        tryIt("assign to a non-member", () -> app.assignCard(b, "priya", tests.getId(), "stranger"));
        tryIt("delete a lane that still has cards", () -> board.removeLane(todo.getId()));
        tryIt("card with an empty title", () -> app.createCard(b, "priya", todo.getId(), "  ", ""));
        System.out.println("  board unchanged after the rejections:\n" + board.render());

        System.out.println("=== Finish work: Doing → Done frees WIP space ===");
        app.moveCard(b, "rahul", api.getId(), done.getId(), 0);
        app.moveCard(b, "priya", tests.getId(), doing.getId(), 0);
        System.out.print(board.render());

        concurrentMoves();
    }

    // Two people drag cards in opposite directions between the same two lanes, 1000 times each.
    // With one lock per board: no deadlock, no lost or duplicated card.
    private static void concurrentMoves() throws Exception {
        System.out.println("\n=== Concurrency: 2 users × 1000 opposite moves between two lanes ===");
        KanbanService app = new KanbanService();
        Board board = app.createBoard("Race", "u1");
        String b = board.getId();
        app.addMember(b, "u1", "u2");
        Lane left = app.addLane(b, "u1", "Left", 0);
        Lane right = app.addLane(b, "u1", "Right", 0);
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < 10; i++) cards.add(app.createCard(b, "u1", (i % 2 == 0 ? left : right).getId(), "card" + i, ""));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        for (String user : List.of("u1", "u2")) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < 1000; i++) {
                    Card c = cards.get(i % cards.size());
                    // each user moves the card to whichever lane it is NOT in right now
                    String target = board.getLaneIdOfCard(c.getId()).equals(left.getId()) ? right.getId() : left.getId();
                    try {
                        app.moveCard(b, user, c.getId(), target, 0);
                    } catch (IllegalStateException | IndexOutOfBoundsException e) {
                        // fine: the other user moved it first
                    }
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        boolean finished = pool.awaitTermination(10, TimeUnit.SECONDS);

        int inLanes = 0;
        for (Lane lane : board.getLanes()) inLanes += lane.getCards().size();
        System.out.println("  finished without deadlock: " + finished);
        System.out.println("  cards on board = " + board.cardCount() + ", cards in lanes = " + inLanes + " (expect 10 and 10)");
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
