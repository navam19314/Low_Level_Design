package com.conceptcoding.interviewquestions.connectfour;

import com.conceptcoding.interviewquestions.connectfour.model.Board;
import com.conceptcoding.interviewquestions.connectfour.model.DiscColor;
import com.conceptcoding.interviewquestions.connectfour.model.GameState;
import com.conceptcoding.interviewquestions.connectfour.model.Player;

public class ConnectFourGame {

    private final Board board;
    private final Player playerRed;
    private final Player playerYellow;

    private Player currentPlayer;
    private GameState state;
    private Player winner;

    public ConnectFourGame(Player playerRed, Player playerYellow) {
        if (playerRed.getColor() != DiscColor.RED || playerYellow.getColor() != DiscColor.YELLOW) {
            throw new IllegalArgumentException("Players must be assigned RED and YELLOW respectively");
        }
        this.board = new Board();
        this.playerRed = playerRed;
        this.playerYellow = playerYellow;
        this.currentPlayer = playerRed;
        this.state = GameState.IN_PROGRESS;
        this.winner = null;
    }

    public boolean makeMove(Player player, int column) {
        if (state != GameState.IN_PROGRESS) {
            return false;
        }
        if (player != currentPlayer) {
            return false;
        }

        int row = board.placeDisc(column, player.getColor());
        if (row == -1) {
            return false;
        }

        if (board.checkWin(row, column, player.getColor())) {
            state = GameState.WON;
            winner = player;
        } else if (board.isFull()) {
            state = GameState.DRAW;
        } else {
            currentPlayer = (player == playerRed) ? playerYellow : playerRed;
        }
        return true;
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public GameState getGameState() {
        return state;
    }

    public Player getWinner() {
        return winner;
    }

    public Board getBoard() {
        return board;
    }
}
