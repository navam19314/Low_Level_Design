package com.conceptcoding.interviewquestions.snakeNladder;

import java.util.concurrent.ThreadLocalRandom;

public class Board {

    Cell[][] cells;

    Board(int boardSize, int numberOfSnakes, int numberOfLadders) {

        initializeCells(boardSize);
        addSnakesLadders(cells, numberOfSnakes, numberOfLadders);
    }

    private void initializeCells(int boardSize) {
        cells = new Cell[boardSize][boardSize];

        for (int i = 0; i < boardSize; i++) {
            for (int j = 0; j < boardSize; j++) {
                cells[i][j] = new Cell();
            }
        }
    }

    private void addSnakesLadders(Cell[][] cells, int numberOfSnakes, int numberOfLadders) {
        int boardSize = cells.length * cells.length;

        // Add snakes (head must be greater than tail)
        while (numberOfSnakes > 0) {
            int snakeHead = ThreadLocalRandom.current().nextInt(1, boardSize - 1);
            int snakeTail = ThreadLocalRandom.current().nextInt(1, boardSize - 1);
            
            // Snake tail must be less than head
            if (snakeTail >= snakeHead) {
                continue;
            }

            Jump snake = new Jump();
            snake.start = snakeHead;
            snake.end = snakeTail;

            Cell cell = getCell(snakeHead);
            cell.jump = snake;

            numberOfSnakes--;
        }

        // Add ladders (start must be less than end)
        while (numberOfLadders > 0) {
            int ladderStart = ThreadLocalRandom.current().nextInt(1, boardSize - 1);
            int ladderEnd = ThreadLocalRandom.current().nextInt(1, boardSize - 1);
            
            // Ladder start must be less than end
            if (ladderStart >= ladderEnd) {
                continue;
            }

            Jump ladder = new Jump();
            ladder.start = ladderStart;
            ladder.end = ladderEnd;

            Cell cell = getCell(ladderStart);
            cell.jump = ladder;

            numberOfLadders--;
        }
    }

    Cell getCell(int playerPosition) {
        int boardRow = playerPosition / cells.length;
        int boardColumn = playerPosition % cells.length;
        return cells[boardRow][boardColumn];
    }
}
