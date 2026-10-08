package com.conceptcoding.interviewquestions.hello_all_questions.games.connectfour;

import com.conceptcoding.interviewquestions.hello_all_questions.games.connectfour.model.DiscColor;
import com.conceptcoding.interviewquestions.hello_all_questions.games.connectfour.model.GameState;
import com.conceptcoding.interviewquestions.hello_all_questions.games.connectfour.model.Player;

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

                try {
                    game.makeMove(current, column);
                } catch (RuntimeException e) {
                    System.out.println("Invalid move: " + e.getMessage() + ". Try again.");
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
