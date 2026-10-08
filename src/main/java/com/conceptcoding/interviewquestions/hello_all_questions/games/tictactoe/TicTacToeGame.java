package com.conceptcoding.interviewquestions.hello_all_questions.games.tictactoe;

import com.conceptcoding.interviewquestions.hello_all_questions.games.tictactoe.model.GameState;
import com.conceptcoding.interviewquestions.hello_all_questions.games.tictactoe.model.Player;
import com.conceptcoding.interviewquestions.hello_all_questions.games.tictactoe.model.Symbol;

// One game: whose turn it is, whether it's over, who won.
// makeMove validates in a fixed order and throws with the reason, so the UI can show it.
// synchronized: two browser tabs (or two players' requests) can hit the same game at once.
public class TicTacToeGame {

    private final Board board;
    private final Player playerX;
    private final Player playerO;
    private Player currentPlayer;
    private GameState state = GameState.IN_PROGRESS;
    private Player winner;

    public TicTacToeGame(String nameX, String nameO, int size) {
        this.board = new Board(size);
        this.playerX = new Player(nameX, Symbol.X);
        this.playerO = new Player(nameO, Symbol.O);
        this.currentPlayer = playerX;                 // X always goes first
    }

    public TicTacToeGame(String nameX, String nameO) { this(nameX, nameO, 3); }

    public synchronized GameState makeMove(Player player, int row, int col) {
        if (state != GameState.IN_PROGRESS) throw new IllegalStateException("Game is over: " + state);
        if (player != currentPlayer)        throw new IllegalStateException("Not " + player + "'s turn");
        if (!board.isInside(row, col))      throw new IllegalArgumentException("(" + row + "," + col + ") is off the board");
        if (!board.isEmpty(row, col))       throw new IllegalArgumentException("(" + row + "," + col + ") is taken");

        if (board.placeAndCheckWin(row, col, player.getMark())) {
            state = GameState.WON;
            winner = player;
        } else if (board.isFull()) {
            state = GameState.DRAW;
        } else {
            currentPlayer = (player == playerX) ? playerO : playerX;
        }
        return state;
    }

    public synchronized void reset() {
        board.reset();
        currentPlayer = playerX;
        state = GameState.IN_PROGRESS;
        winner = null;
    }

    public synchronized Player    getCurrentPlayer() { return currentPlayer; }
    public synchronized GameState getGameState()     { return state; }
    public synchronized Player    getWinner()        { return winner; }
    public synchronized String    render()           { return board.render(); }
    public Player getPlayerX() { return playerX; }
    public Player getPlayerO() { return playerO; }
    public Board  getBoard()   { return board; }
}
