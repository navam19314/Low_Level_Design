package com.conceptcoding.interviewquestions.hello_all_questions.games.tictactoe;

import com.conceptcoding.interviewquestions.hello_all_questions.games.tictactoe.model.GameState;
import com.conceptcoding.interviewquestions.hello_all_questions.games.tictactoe.model.Symbol;

// One game: whose turn it is, whether it's over, who won.
// synchronized: two requests (a double click, two tabs) can hit the same game at once.
public class TicTacToeGame {

    private final Board board;
    private Symbol turn = Symbol.X;                    // X always goes first
    private GameState state = GameState.IN_PROGRESS;
    private Symbol winner;                             // null until someone wins

    public TicTacToeGame(int size) {
        this.board = new Board(size);
    }

    public synchronized GameState makeMove(Symbol player, int row, int col) {
        if (state != GameState.IN_PROGRESS) throw new IllegalStateException("Game is over: " + state);
        if (player != turn) throw new IllegalStateException("Not " + player + "'s turn");
        board.place(row, col, player);                 // throws if off the board or taken

        if (board.hasWon(row, col, player)) {
            state = GameState.WON;
            winner = player;
        } else if (board.isFull()) {
            state = GameState.DRAW;
        } else {
            turn = (turn == Symbol.X) ? Symbol.O : Symbol.X;
        }
        return state;
    }

    public synchronized Symbol    getTurn()   { return turn; }
    public synchronized GameState getState()  { return state; }
    public synchronized Symbol    getWinner() { return winner; }
    public synchronized String    render()    { return board.render(); }
}
