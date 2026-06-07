package com.conceptcoding.interviewquestions.hello_all_questions.tictactoe;

import com.conceptcoding.interviewquestions.hello_all_questions.tictactoe.model.Symbol;

/*
 * Board — physical state of the 3x3 grid.
 *
 * Responsibilities:
 *   - canPlace   : is this cell empty and in bounds?
 *   - placeMark  : write a symbol to the cell
 *   - checkWin   : did the last move complete a line?
 *   - isFull     : are all 9 cells occupied?
 *   - getCell    : read what's at a cell (for query / UI)
 *   - reset      : clear the board for a new game
 *
 * NOT here: turn order, game state, who the players are — those belong to Game.
 */
public class Board {

    static final int SIZE = 3;
    private final Symbol[][] grid = new Symbol[SIZE][SIZE];

    // --- guards ---

    public boolean canPlace(int row, int col) {
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return false;
        return grid[row][col] == null;
    }

    // --- mutations ---

    public void placeMark(int row, int col, Symbol mark) {
        grid[row][col] = mark;
    }

    public void reset() {
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                grid[r][c] = null;
    }

    // --- queries ---

    public Symbol getCell(int row, int col) {
        return grid[row][col];
    }

    public boolean isFull() {
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                if (grid[r][c] == null) return false;
        return true;
    }

    /*
     * Only check the four lines that pass through (row, col).
     * A winning line must include the cell just placed, so scanning the whole
     * board every move is unnecessary — this is O(N) not O(N²).
     */
    public boolean checkWin(int row, int col, Symbol mark) {
        if (countLine(mark, row, 0,      0, +1) == SIZE) return true;  // row
        if (countLine(mark, 0,   col,   +1,  0) == SIZE) return true;  // col
        if (row == col
                && countLine(mark, 0, 0,      +1, +1) == SIZE) return true;  // main diagonal
        if (row + col == SIZE - 1
                && countLine(mark, 0, SIZE-1, +1, -1) == SIZE) return true;  // anti-diagonal
        return false;
    }

    private int countLine(Symbol mark, int r, int c, int dr, int dc) {
        int count = 0;
        for (int i = 0; i < SIZE; i++, r += dr, c += dc)
            if (grid[r][c] == mark) count++;
        return count;
    }

    // --- display ---

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
