package uucki;

import uucki.game.reversi.Board;
import uucki.type.FieldValue;
import uucki.type.Move;
import uucki.type.Position;
import uucki.type.Node;
import uucki.algorithm.MonteCarloTreeSearch;
import uucki.modes.AIvsAI;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.*;

public class AnalyseBeginnerBias {

    public static int ROUNDS = 100;

    public static void main(String[] args) {
        Board board = Board.initialBoard(false);
        ExecutorService executor = Executors.newCachedThreadPool();
        MonteCarloTreeSearch ai = new MonteCarloTreeSearch(0.25, true, false, executor);
        MonteCarloTreeSearch ai1 = new MonteCarloTreeSearch(0.25, true, false, executor);

        int aiWins = 0;
        int ai1Wins = 0;

        for(int i = 0; i < ROUNDS; i++) {
            System.out.println("New round");

            board = Board.initialBoard(false);
            while(!board.isFinished()) {
                Move move = ai.run(board, FieldValue.WHITE);
                if(move != null) {
                    board = board.makeMove(move);
                }

                move = ai1.run(board, FieldValue.BLACK);
                if(move != null) {
                    board = board.makeMove(move);
                }
            }

            if(board.getWinner() == FieldValue.WHITE) {
                aiWins++;
            } else {
                ai1Wins++;
            }
        }

        System.out.println(aiWins);
        System.out.println(ai1Wins);

        if(aiWins > ai1Wins) {
            System.out.println("AI is better");
        } else if(ai1Wins > aiWins) {
            System.out.println("AI1 is better");
        } else {
            System.out.println("Draw");
        }

        executor.shutdown();
    }
}
