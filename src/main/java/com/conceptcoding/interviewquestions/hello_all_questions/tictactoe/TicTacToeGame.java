package com.conceptcoding.interviewquestions.hello_all_questions.tictactoe;

import com.conceptcoding.interviewquestions.hello_all_questions.tictactoe.model.GameState;
import com.conceptcoding.interviewquestions.hello_all_questions.tictactoe.model.Player;
import com.conceptcoding.interviewquestions.hello_all_questions.tictactoe.model.Symbol;

public class TicTacToeGame {

    private final Board board = new Board();
    private final Player playerX;
    private final Player playerO;
    private Player currentPlayer;
    private GameState state = GameState.IN_PROGRESS;
    private Player winner = null;

    public TicTacToeGame(String nameX, String nameO) {
        this.playerX = new Player(nameX, Symbol.X);
        this.playerO = new Player(nameO, Symbol.O);
        this.currentPlayer = playerX;
    }

    public boolean makeMove(Player player, int row, int col) {
        if (state != GameState.IN_PROGRESS)  return false;
        if (player != currentPlayer)         return false;
        if (!board.canPlace(row, col))       return false;

        board.placeMark(row, col, player.mark());

        if (board.checkWin(row, col, player.mark())) {
            state = GameState.WON;
            winner = player;
        } else if (board.isFull()) {
            state = GameState.DRAW;
        } else {
            currentPlayer = (player == playerX) ? playerO : playerX;
        }

        return true;
    }

    public Board     getBoard()         { return board; }
    public Player    getPlayerX()       { return playerX; }
    public Player    getPlayerO()       { return playerO; }
    public Player    getCurrentPlayer() { return currentPlayer; }
    public GameState getGameState()     { return state; }
    public Player    getWinner()        { return winner; }
}
