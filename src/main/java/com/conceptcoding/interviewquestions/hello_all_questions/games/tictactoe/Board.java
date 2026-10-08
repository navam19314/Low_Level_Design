package com.conceptcoding.interviewquestions.hello_all_questions.games.tictactoe;

import com.conceptcoding.interviewquestions.hello_all_questions.games.tictactoe.model.Symbol;

// The N×N grid plus running counters that make the win check O(1).
//
// Counter trick: X adds +1, O adds -1 to its row, its column, and each diagonal it sits on.
// A line is won the moment its counter reaches +N (all X) or -N (all O).
// isFull is O(1) too: just count the moves.
//
// NOT here: turns, players, game state. Those belong to TicTacToeGame.
public class Board {

    private final int size;
    private final Symbol[][] grid;
    private final int[] rowSum;
    private final int[] colSum;
    private int diagSum;          // cells where row == col
    private int antiDiagSum;      // cells where row + col == size - 1
    private int moves;

    public Board(int size) {
        if (size < 3) throw new IllegalArgumentException("Board size must be >= 3");
        this.size = size;
        this.grid = new Symbol[size][size];
        this.rowSum = new int[size];
        this.colSum = new int[size];
    }

    public boolean isInside(int row, int col) {
        return row >= 0 && row < size && col >= 0 && col < size;
    }

    public boolean isEmpty(int row, int col) { return grid[row][col] == null; }

    // Places the mark and returns true if this move completed a line. O(1).
    public boolean placeAndCheckWin(int row, int col, Symbol mark) {
        grid[row][col] = mark;
        moves++;
        int delta = mark == Symbol.X ? 1 : -1;
        rowSum[row] += delta;
        colSum[col] += delta;
        if (row == col) diagSum += delta;
        if (row + col == size - 1) antiDiagSum += delta;

        int target = size * delta;                    // +N for X, -N for O
        return rowSum[row] == target || colSum[col] == target
                || diagSum == target || antiDiagSum == target;
    }

    public boolean isFull() { return moves == size * size; }

    // exact reverse of placeAndCheckWin, for undo: counters are reversible
    public void clear(int row, int col, Symbol mark) {
        grid[row][col] = null;
        moves--;
        int delta = mark == Symbol.X ? 1 : -1;
        rowSum[row] -= delta;
        colSum[col] -= delta;
        if (row == col) diagSum -= delta;
        if (row + col == size - 1) antiDiagSum -= delta;
    }

    public void reset() {
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) grid[r][c] = null;
            rowSum[r] = 0;
            colSum[r] = 0;
        }
        diagSum = 0;
        antiDiagSum = 0;
        moves = 0;
    }

    public Symbol getCell(int row, int col) { return grid[row][col]; }
    public int    getSize()                 { return size; }

    public String render() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                sb.append(grid[r][c] == null ? " ." : " " + grid[r][c]);
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
