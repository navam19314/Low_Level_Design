package com.conceptcoding.interviewquestions.hello_all_questions.games.tictactoe;

import com.conceptcoding.interviewquestions.hello_all_questions.games.tictactoe.model.Symbol;

// The N×N grid. Knows where marks are and whether a line is complete. Nothing about turns.
public class Board {

    private final int size;
    private final Symbol[][] grid;              // null = empty
    private int moves;

    public Board(int size) {
        if (size < 3) throw new IllegalArgumentException("Board size must be at least 3");
        this.size = size;
        this.grid = new Symbol[size][size];
    }

    public void place(int row, int col, Symbol mark) {
        if (row < 0 || row >= size || col < 0 || col >= size) {
            throw new IllegalArgumentException("(" + row + "," + col + ") is off the board");
        }
        if (grid[row][col] != null) throw new IllegalArgumentException("(" + row + "," + col + ") is taken");
        grid[row][col] = mark;
        moves++;
    }

    // Did the move at (row, col) complete a line? Only the lines through that cell can have changed,
    // so check its row, its column and the two diagonals: one loop, O(N).
    public boolean hasWon(int row, int col, Symbol mark) {
        boolean rowWin = true, colWin = true, diagWin = true, antiDiagWin = true;
        for (int i = 0; i < size; i++) {
            if (grid[row][i] != mark) rowWin = false;
            if (grid[i][col] != mark) colWin = false;
            if (grid[i][i] != mark) diagWin = false;
            if (grid[i][size - 1 - i] != mark) antiDiagWin = false;
        }
        return rowWin || colWin || diagWin || antiDiagWin;
    }

    public boolean isFull() { return moves == size * size; }

    public Symbol getCell(int row, int col) { return grid[row][col]; }

    public String render() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) sb.append(grid[r][c] == null ? " ." : " " + grid[r][c]);
            sb.append('\n');
        }
        return sb.toString();
    }
}
