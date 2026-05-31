package com.conceptcoding.interviewquestions.hello_all_questions.snakeladder;

/**
 * Strategy seam for "give me a number".
 *
 * <p>Why a Strategy here when one-line {@code ThreadLocalRandom.current().nextInt(1,7)}
 * would suffice? Two reasons:
 *   1. <b>Testability</b> — the driver uses a {@link FixedSequenceDice} so scenarios
 *      land on snakes / ladders / 100 deterministically; without the seam every test
 *      becomes flaky.
 *   2. <b>Extensibility</b> — variants like "two-dice", "weighted dice", or "must-roll-6-to-start"
 *      slot in as new implementations without touching {@link Game}.
 *
 * <p>This is the canonical "one-sentence test" case for pre-baking a pattern:
 * <i>"I need at least two implementations on day one (real + test-fake)"</i> → ship the seam.
 */
public interface Dice {
    /** Roll. Implementations decide the range; standard die returns 1..6. */
    int roll();
}
