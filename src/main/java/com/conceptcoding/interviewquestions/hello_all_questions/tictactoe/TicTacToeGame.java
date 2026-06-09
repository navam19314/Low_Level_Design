package com.conceptcoding.interviewquestions.hello_all_questions.tictactoe;

import com.conceptcoding.interviewquestions.hello_all_questions.tictactoe.model.GameState;
import com.conceptcoding.interviewquestions.hello_all_questions.tictactoe.model.Player;
import com.conceptcoding.interviewquestions.hello_all_questions.tictactoe.model.Symbol;

/*
 * TicTacToeGame — orchestrates a single game.
 *
 * Requirements → State mapping (Step 3 of the framework):
 *
 *   Requirement                                  State needed
 *   ----------------------------------------------------------
 *   Two players alternate, X goes first       →  playerX, playerO, currentPlayer
 *   3x3 grid holds marks                      →  Board
 *   Win via row / col / diagonal              →  GameState, winner   (board detects the line)
 *   Draw when board full with no winner       →  GameState (DRAW)
 *   Invalid moves rejected                    →  guards in makeMove
 *   Query state + reset                       →  getters, reset()
 *
 * Behaviors (Step 3 — derived from requirements):
 *   makeMove(player, row, col) → boolean
 *   getCurrentPlayer()         → Player
 *   getGameState()             → GameState
 *   getWinner()                → Player      (null until WON)
 *   getBoard()                 → Board
 *   reset()
 */
public class TicTacToeGame {

    private final Board board = new Board();
    private final Player playerX;
    private final Player playerO;
    private Player    currentPlayer;
    private GameState state  = GameState.IN_PROGRESS;
    private Player    winner = null;

    public TicTacToeGame(String nameX, String nameO) {
        playerX = new Player(nameX, Symbol.X);
        playerO = new Player(nameO, Symbol.O);
        currentPlayer = playerX;    // X always goes first
    }

    // ── core action ────────────────────────────────────────────────────────────

    public boolean makeMove(Player player, int row, int col) {
        if (state != GameState.IN_PROGRESS) return false;   // game already over
        if (player != currentPlayer)        return false;   // wrong turn
        if (!board.canPlace(row, col))      return false;   // occupied or OOB

        board.placeMark(row, col, player.getMark());

        if (board.checkWin(row, col, player.getMark())) {
            state  = GameState.WON;
            winner = player;
        } else if (board.isFull()) {
            state  = GameState.DRAW;
        } else {
            currentPlayer = (player == playerX) ? playerO : playerX;
        }

        return true;
    }

    // ── reset ──────────────────────────────────────────────────────────────────

    public void reset() {
        board.reset();
        currentPlayer = playerX;
        state         = GameState.IN_PROGRESS;
        winner        = null;
    }

    // ── queries ────────────────────────────────────────────────────────────────

    public Board     getBoard()         { return board; }
    public Player    getPlayerX()       { return playerX; }
    public Player    getPlayerO()       { return playerO; }
    public Player    getCurrentPlayer() { return currentPlayer; }
    public GameState getGameState()     { return state; }
    public Player    getWinner()        { return winner; }
}
