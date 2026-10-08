package com.conceptcoding.interviewquestions.hello_all_questions.games.connectfour;

import com.conceptcoding.interviewquestions.hello_all_questions.games.connectfour.model.Board;
import com.conceptcoding.interviewquestions.hello_all_questions.games.connectfour.model.DiscColor;
import com.conceptcoding.interviewquestions.hello_all_questions.games.connectfour.model.GameState;
import com.conceptcoding.interviewquestions.hello_all_questions.games.connectfour.model.Player;

public class ConnectFourGame {

    private final Board board;
    private final Player playerRed;
    private final Player playerYellow;

    private Player currentPlayer;
    private GameState state;
    private Player winner;

    public ConnectFourGame(Player playerRed, Player playerYellow) {
        this(playerRed, playerYellow, 6, 7);
    }

    public ConnectFourGame(Player playerRed, Player playerYellow, int rows, int cols) {
        if (playerRed.getColor() != DiscColor.RED || playerYellow.getColor() != DiscColor.YELLOW) {
            throw new IllegalArgumentException("Players must be assigned RED and YELLOW respectively");
        }
        this.board = new Board(rows, cols);
        this.playerRed = playerRed;
        this.playerYellow = playerYellow;
        this.currentPlayer = playerRed;
        this.state = GameState.IN_PROGRESS;
        this.winner = null;
    }

    // Guard clauses up top keep the happy path unindented; each says WHY a move is refused.
    // synchronized: in an online game both players' requests (and double-clicks) arrive on different threads.
    // Returns the row the disc landed in.
    public synchronized int makeMove(Player player, int column) {
        if (state != GameState.IN_PROGRESS)        throw new IllegalStateException("Game is over: " + state);
        if (player != currentPlayer)               throw new IllegalStateException("Not " + player.getName() + "'s turn");
        if (column < 0 || column >= board.getCols()) throw new IllegalArgumentException("Column " + column + " is off the board");
        if (!board.canPlace(column))               throw new IllegalArgumentException("Column " + column + " is full");

        int row = board.placeDisc(column, player.getColor());   // gravity: lowest empty row

        if (board.checkWin(row, column, player.getColor())) {    // win BEFORE full: the last disc can win
            state = GameState.WON;
            winner = player;
        } else if (board.isFull()) {
            state = GameState.DRAW;
        } else {
            currentPlayer = (player == playerRed) ? playerYellow : playerRed;
        }
        return row;
    }

    public synchronized Player getCurrentPlayer() {
        return currentPlayer;
    }

    public synchronized GameState getGameState() {
        return state;
    }

    public synchronized Player getWinner() {
        return winner;
    }

    public Board getBoard() {
        return board;
    }
}
