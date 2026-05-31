package com.conceptcoding.interviewquestions.hello_all_questions.snakeladder;

import java.util.HashMap;
import java.util.Map;

/**
 * The physical board — size + the snake / ladder topology.
 *
 * <p>Snakes: key = head (higher square), value = tail (lower square).<br>
 * Ladders: key = bottom (lower square), value = top (higher square).
 *
 * <p>Validation in the constructor enforces puzzle well-formedness:
 *   - no snake head / ladder bottom on 1 or size (start / finish)
 *   - no square is both a snake head AND a ladder bottom (loops / contradictions)
 *   - tails &lt; heads, tops &gt; bottoms
 *
 * <p>{@link #applyJump(int)} encapsulates the "land on a snake / ladder" rule —
 * Game asks the Board "where do I end up?" and doesn't itself branch on whether
 * the square is a snake or a ladder. <b>Tell, Don't Ask.</b>
 */
public class Board {

    private final int size;
    private final Map<Integer, Integer> snakes;
    private final Map<Integer, Integer> ladders;

    public Board(int size, Map<Integer, Integer> snakes, Map<Integer, Integer> ladders) {
        if (size < 10) throw new IllegalArgumentException("Board must have at least 10 squares");
        this.size    = size;
        this.snakes  = new HashMap<>(snakes);
        this.ladders = new HashMap<>(ladders);
        validate();
    }

    private void validate() {
        for (var e : snakes.entrySet()) {
            int head = e.getKey(), tail = e.getValue();
            if (head <= tail)              throw new IllegalArgumentException("Snake head " + head + " must be > tail " + tail);
            if (head == size || head == 1) throw new IllegalArgumentException("Snake cannot start at 1 or " + size);
            if (ladders.containsKey(head)) throw new IllegalArgumentException("Square " + head + " is both snake head and ladder bottom");
        }
        for (var e : ladders.entrySet()) {
            int bottom = e.getKey(), top = e.getValue();
            if (bottom >= top)             throw new IllegalArgumentException("Ladder bottom " + bottom + " must be < top " + top);
            if (top > size || bottom == 1) throw new IllegalArgumentException("Ladder out of bounds: " + bottom + "->" + top);
        }
    }

    public int  getSize()                { return size; }
    public Map<Integer, Integer> snakes() { return Map.copyOf(snakes); }
    public Map<Integer, Integer> ladders(){ return Map.copyOf(ladders); }

    /**
     * Given a landing square, return the FINAL position after applying any snake
     * or ladder. Returns {@code position} unchanged if the square is neither.
     *
     * <p>Note: we do NOT chain (i.e. if a ladder top happens to be a snake head,
     * we still resolve only one jump). Board validation forbids that overlap.
     */
    public int applyJump(int position) {
        if (snakes.containsKey(position))  return snakes.get(position);
        if (ladders.containsKey(position)) return ladders.get(position);
        return position;
    }

    /** Convenience builder for the classic 100-square board with sensible defaults. */
    public static Board standard100() {
        Map<Integer, Integer> snakes = new HashMap<>();
        snakes.put(99, 54); snakes.put(70, 55); snakes.put(52, 42); snakes.put(25,  2); snakes.put(95, 72);

        Map<Integer, Integer> ladders = new HashMap<>();
        ladders.put( 6, 25); ladders.put(11, 40); ladders.put(60, 85); ladders.put(46, 90); ladders.put(17, 69);

        return new Board(100, snakes, ladders);
    }
}
