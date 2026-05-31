package com.conceptcoding.interviewquestions.hello_all_questions.snakeladder;

import java.util.concurrent.ThreadLocalRandom;

/** Standard fair 1d6. */
public class StandardDice implements Dice {
    @Override
    public int roll() {
        return ThreadLocalRandom.current().nextInt(1, 7);
    }
}
