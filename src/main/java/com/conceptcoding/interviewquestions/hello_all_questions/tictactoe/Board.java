package com.conceptcoding.interviewquestions.hello_all_questions.tictactoe;

import com.conceptcoding.interviewquestions.hello_all_questions.tictactoe.model.Symbol;

public class Board {

    static final int SIZE = 3;
    private final Symbol[][] grid = new Symbol[SIZE][SIZE];

    public boolean canPlace(int row, int col) {
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return false;
        return grid[row][col] == null;
    }

    public void placeMark(int row, int col, Symbol mark) {
        grid[row][col] = mark;
    }

    // Only check lines through (row, col) — winning line must include the last move. O(N) not O(N²).
    public boolean checkWin(int row, int col, Symbol mark) {
        if (countInLine(mark, row, 0, 0, 1) == SIZE) return true;           // row
        if (countInLine(mark, 0, col, 1, 0) == SIZE) return true;           // col
        if (row == col && countInLine(mark, 0, 0, 1, 1) == SIZE) return true;          // main diag
        if (row + col == SIZE - 1 && countInLine(mark, 0, SIZE - 1, 1, -1) == SIZE) return true; // anti-diag
        return false;
    }

    public boolean isFull() {
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                if (grid[r][c] == null) return false;
        return true;
    }

    private int countInLine(Symbol mark, int startRow, int startCol, int dRow, int dCol) {
        int count = 0;
        for (int i = 0; i < SIZE; i++, startRow += dRow, startCol += dCol)
            if (grid[startRow][startCol] == mark) count++;
        return count;
    }

    public String render() {
        StringBuilder sb = new StringBuilder("  0   1   2\n");
        for (int r = 0; r < SIZE; r++) {
            sb.append(r).append(" ");
            for (int c = 0; c < SIZE; c++) {
                sb.append(grid[r][c] == null ? " . " : " " + grid[r][c] + " ");
                if (c < SIZE - 1) sb.append("|");
            }
            sb.append('\n');
            if (r < SIZE - 1) sb.append("  -----------\n");
        }
        return sb.toString();
    }
}
