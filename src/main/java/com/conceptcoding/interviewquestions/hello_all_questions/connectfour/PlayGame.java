package com.conceptcoding.interviewquestions.hello_all_questions.connectfour;

import com.conceptcoding.interviewquestions.hello_all_questions.connectfour.model.DiscColor;
import com.conceptcoding.interviewquestions.hello_all_questions.connectfour.model.GameState;
import com.conceptcoding.interviewquestions.hello_all_questions.connectfour.model.Player;

import java.util.Scanner;

public class PlayGame {

    public static void main(String[] args) {
        Player red = new Player("Player1", DiscColor.RED);
        Player yellow = new Player("Player2", DiscColor.YELLOW);
        ConnectFourGame game = new ConnectFourGame(red, yellow);

        try (Scanner scanner = new Scanner(System.in)) {
            while (game.getGameState() == GameState.IN_PROGRESS) {
                game.getBoard().printBoard();
                Player current = game.getCurrentPlayer();
                System.out.printf("%s (%s) — enter column [0-%d]: ",
                        current.getName(), current.getColor(), game.getBoard().getCols() - 1);

                if (!scanner.hasNextInt()) {
                    scanner.next();
                    System.out.println("Please enter a valid integer.");
                    continue;
                }
                int column = scanner.nextInt();

                if (!game.makeMove(current, column)) {
                    System.out.println("Invalid move — try again.");
                }
            }
        }

        game.getBoard().printBoard();
        if (game.getGameState() == GameState.WON) {
            System.out.println("Winner: " + game.getWinner().getName()
                    + " (" + game.getWinner().getColor() + ")");
        } else {
            System.out.println("Game drawn.");
        }
    }
}
