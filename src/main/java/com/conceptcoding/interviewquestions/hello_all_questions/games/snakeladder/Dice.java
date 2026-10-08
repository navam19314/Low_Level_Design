package com.conceptcoding.interviewquestions.hello_all_questions.games.snakeladder;

// Strategy seam for "give me a number".
//
// Why a Strategy here when one-line ThreadLocalRandom.current().nextInt(1,7)
// would suffice? Two reasons:
// 1. Testability — the driver uses a FixedSequenceDice so scenarios
// land on snakes / ladders / 100 deterministically; without the seam every test
// becomes flaky.
// 2. Extensibility — variants like "two-dice", "weighted dice", or "must-roll-6-to-start"
// slot in as new implementations without touching Game.
//
// This is the canonical "one-sentence test" case for pre-baking a pattern:
// "I need at least two implementations on day one (real + test-fake)" → ship the seam.
public interface Dice {
    // Roll. Implementations decide the range; standard die returns 1..6.
    int roll();
}
