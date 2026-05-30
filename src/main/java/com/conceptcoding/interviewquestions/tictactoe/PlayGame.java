package com.conceptcoding.interviewquestions.tictactoe;

import com.conceptcoding.interviewquestions.tictactoe.model.GameStatus;

public class PlayGame {

    public static void main(String[] args) {
        System.out.println("\n===>>> TicTacToe Game\n");
        
        TicTacToeGame game = new TicTacToeGame();
        game.initializeGame();
        GameStatus status = game.startGame();
        
        System.out.print("\n===>>> GAME OVER: ");
        switch (status) {
            case WIN:
                System.out.print(game.winner.name + " won the game");
                break;
            case DRAW:
                System.out.print("It's a Draw!");
                break;
            default:
                System.out.print("Game Ends");
                break;
        }
    }

}