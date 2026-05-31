package com.conceptcoding.interviewquestions.hello_all_questions.snakeladder;

/**
 * Test double — returns a pre-programmed sequence of rolls, then cycles.
 *
 * <p>Lets the driver land players on specific squares (snake heads, ladder bottoms,
 * exact-100) so scenarios are reproducible.
 */
public class FixedSequenceDice implements Dice {

    private final int[] sequence;
    private int index = 0;

    public FixedSequenceDice(int... sequence) {
        if (sequence.length == 0)
            throw new IllegalArgumentException("Need at least one roll in the sequence");
        this.sequence = sequence;
    }

    @Override
    public int roll() {
        int next = sequence[index % sequence.length];
        index++;
        return next;
    }
}
