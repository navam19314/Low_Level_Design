package com.conceptcoding.interviewquestions.hello_all_questions.snakeladder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrator. Owns:
 *   - the board (immutable topology)
 *   - the dice (Strategy, swappable for tests)
 *   - the turn queue (Deque so we rotate cheaply)
 *   - the game state (IN_PROGRESS / FINISHED + winner)
 *
 * <p>Game RULES live here, not on Board:
 *   - overshoot (target &gt; boardSize) → no move (skip turn)
 *   - exact size → win
 *   - otherwise apply Board.applyJump and rotate
 *
 * <p>Out of scope (extensions called out in the walkthrough):
 *   - roll-6-grants-extra-turn variant
 *   - must-roll-6-to-leave-start
 *   - persistence / multi-game session
 */
public class Game {

    public enum Status { IN_PROGRESS, FINISHED }

    private final Board   board;
    private final Dice    dice;
    private final Deque<Player> turnOrder;
    private final List<String>  eventLog = new ArrayList<>();
    private Status status = Status.IN_PROGRESS;
    private Player winner = null;

    public Game(Board board, Dice dice, List<Player> players) {
        if (players == null || players.size() < 2)
            throw new IllegalArgumentException("Need at least 2 players");
        this.board     = Objects.requireNonNull(board);
        this.dice      = Objects.requireNonNull(dice);
        this.turnOrder = new ArrayDeque<>(players);
    }

    public Status      getStatus()    { return status; }
    public Player      getWinner()    { return winner; }
    public List<String> getEventLog() { return List.copyOf(eventLog); }

    /** Whose turn is it next? Returns null if game over. */
    public Player getCurrentPlayer() {
        return status == Status.FINISHED ? null : turnOrder.peekFirst();
    }

    /**
     * Play one turn for the current player. Returns the player who just moved.
     * Throws if the game is already over.
     */
    public Player playTurn() {
        if (status == Status.FINISHED) throw new IllegalStateException("Game over");
        Player p   = turnOrder.removeFirst();
        int    roll = dice.roll();
        int target  = p.getPosition() + roll;

        if (target > board.getSize()) {
            log(p, "rolled " + roll + " — overshoots " + board.getSize() + ", stays at " + p.getPosition());
            turnOrder.addLast(p);
            return p;
        }

        int landed = board.applyJump(target);
        if (landed != target) {
            String kind = board.snakes().containsKey(target) ? "snake" : "ladder";
            log(p, "rolled " + roll + " → " + target + " (" + kind + " → " + landed + ")");
        } else {
            log(p, "rolled " + roll + " → " + target);
        }
        p.setPosition(landed);

        if (landed == board.getSize()) {
            winner = p;
            status = Status.FINISHED;
            log(p, "WINS");
            return p;
        }
        turnOrder.addLast(p);
        return p;
    }

    /** Run the game to completion (or until {@code maxTurns} reached). */
    public void playToFinish(int maxTurns) {
        for (int i = 0; i < maxTurns && status == Status.IN_PROGRESS; i++) {
            playTurn();
        }
    }

    private void log(Player p, String msg) {
        eventLog.add(p.getName() + " " + msg);
    }
}
