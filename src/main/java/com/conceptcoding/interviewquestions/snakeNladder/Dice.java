package com.conceptcoding.interviewquestions.snakeNladder;

import java.util.concurrent.ThreadLocalRandom;

public class Dice {

    int diceCount;
    int min = 1;
    int max = 6;

    public Dice(int diceCount) {
        this.diceCount = diceCount;
    }

    public int rollDice() {
        int totalSum = 0;
        int diceUsed = 0;

        // Roll all dice and sum up the values
        while (diceUsed < diceCount) {
            int diceValue = ThreadLocalRandom.current().nextInt(min, max + 1);
            totalSum += diceValue;
            diceUsed++;
        }

        return totalSum;
    }
}
