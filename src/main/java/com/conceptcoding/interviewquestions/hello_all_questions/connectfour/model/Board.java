package com.conceptcoding.interviewquestions.hello_all_questions.connectfour.model;

public class Board {

    private static final int CONNECT = 4;

    // Only 4 axes pass through any cell; for each we count outward in BOTH dir and -dir.
    private static final int[][] DIRECTIONS = {
            {0, 1},   // horizontal
            {1, 0},   // vertical
            {1, 1},   // diagonal  \
            {-1, 1}   // diagonal  /
    };

    private final int rows;
    private final int cols;
    private final DiscColor[][] grid;

    public Board() {
        this(6, 7);
    }

    public Board(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.grid = new DiscColor[rows][cols];
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public DiscColor getCell(int row, int col) {
        if (!inBounds(row, col)) {
            return null;
        }
        return grid[row][col];
    }

    // Top row empty => column has room. Cheaper than scanning the whole column.
    public boolean canPlace(int col) {
        return col >= 0 && col < cols && grid[0][col] == null;
    }

    // Gravity: scan from bottom up; first null slot is where the disc lands.
    public int placeDisc(int col, DiscColor color) {
        if (!canPlace(col)) {
            return -1;
        }
        for (int row = rows - 1; row >= 0; row--) {
            if (grid[row][col] == null) {
                grid[row][col] = color;
                return row;
            }
        }
        return -1;
    }

    // If every top cell is filled, every column below it must also be filled.
    public boolean isFull() {
        for (int c = 0; c < cols; c++) {
            if (grid[0][c] == null) {
                return false;
            }
        }
        return true;
    }

    // Only check the 4 lines through the disc just placed: +1 for itself, then count outward each way.
    public boolean checkWin(int row, int col, DiscColor color) {
        if (!inBounds(row, col) || grid[row][col] != color) {
            return false;
        }
        for (int[] dir : DIRECTIONS) {
            int count = 1
                    + countInDirection(row, col, dir[0], dir[1], color)
                    + countInDirection(row, col, -dir[0], -dir[1], color);
            if (count >= CONNECT) {
                return true;
            }
        }
        return false;
    }

    private int countInDirection(int row, int col, int dr, int dc, DiscColor color) {
        int count = 0;
        int r = row + dr;
        int c = col + dc;
        while (inBounds(r, c) && grid[r][c] == color) {
            count++;
            r += dr;
            c += dc;
        }
        return count;
    }

    private boolean inBounds(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < cols;
    }

    public void printBoard() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                DiscColor cell = grid[r][c];
                sb.append('|').append(cell == null ? ' ' : cell == DiscColor.RED ? 'R' : 'Y');
            }
            sb.append("|\n");
        }
        for (int c = 0; c < cols; c++) {
            sb.append(' ').append(c);
        }
        sb.append('\n');
        System.out.println(sb);
    }
}
