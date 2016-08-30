package uucki.algorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import uucki.game.reversi.Board;
import uucki.heuristic.reversi.Basic;
import uucki.type.FieldValue;
import uucki.type.Move;
import uucki.type.Position;

public class MonteCarloTreeSearch extends Algorithm implements Runnable {

    private static final long MAX_TIME = 1000 * 7;
    private final static int THREADS = 4;

    private Board currentBoard = null;
    private FieldValue currentColor = null;
    private AtomicInteger simulationCount = new AtomicInteger();

    private ConcurrentHashMap<Board, Node<Board>> nodesBlack = new ConcurrentHashMap<Board, Node<Board>>();
    private ConcurrentHashMap<Board, Node<Board>> nodesWhite = new ConcurrentHashMap<Board, Node<Board>>();
    private Node<Board> rootNode = null;
    private long cutOffTime = 0;

    public Move run(Board board, FieldValue color) {
        long startingTime = System.currentTimeMillis();

        cutOffTime = startingTime + MAX_TIME;
        currentBoard = board;
        currentColor = color;
        rootNode = new Node<Board>(board, color);

        List<Position> positions = rootNode.item.getPossiblePositions(color);
        if(positions.size() == 0) {
            return null;
        }
        if(positions.size() == 1) {
            return new Move(positions.get(0), color);
        }


        ExecutorService executor = Executors.newCachedThreadPool();
        for(int i = 0; i < THREADS; i++) {
            executor.execute(this);
        }

        try {
            executor.awaitTermination(MAX_TIME * 2, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {

        }

        System.out.println(simulationCount);
        currentBoard = null;
        Move bestMove = getBestMove(rootNode);
        nodesBlack.clear();
        nodesWhite.clear();
        return bestMove;
    }

    public void run() {
        while(System.currentTimeMillis() < cutOffTime) {
            List<Node<Board>> ancestors = new ArrayList<Node<Board>>();
            ancestors.add(rootNode);
            selectAndExpand(ancestors);
            Node<Board> lastNode = ancestors.get(ancestors.size() - 1);
            FieldValue winner = simulate(lastNode);
            getNodes(lastNode.color).put(lastNode.item, lastNode);
            double lambda = 0;
            double heuristic = Basic.getValue(lastNode.item, FieldValue.WHITE);
            double whiteScore = (winner == FieldValue.WHITE ? 1 : 0) * (1-lambda) + (heuristic > 0 ? 1 : 0) * lambda;
            double blackScore = (winner == FieldValue.BLACK ? 1 : 0) * (1-lambda) + (heuristic > 0 ? 0 : 1) * lambda;
            update(ancestors, whiteScore, blackScore);
        }
    }

    private ConcurrentHashMap<Board, Node<Board>> getNodes(FieldValue color) {
        if(color == FieldValue.BLACK) {
            return nodesBlack;
        } else {
            return nodesWhite;
        }
    }

    private void selectAndExpand(List<Node<Board>> ancestors) {
        Node<Board> parent = ancestors.get(ancestors.size() - 1);

        synchronized(parent) {
            if(parent.item.isFinished()) {
                return;
            }

            List<Position> positions = parent.item.getPossiblePositions(parent.color);
            FieldValue opponentColor = parent.color == FieldValue.WHITE ? FieldValue.BLACK : FieldValue.WHITE;

            List<Node<Board>> children = new ArrayList<Node<Board>>();
            for(Position p : positions) {
                Move move = new Move(p, parent.color);
                Board b = parent.item.makeMove(move);
                Node<Board> node = new Node<Board>(b, opponentColor);
                if(getNodes(node.color).containsKey(node.item)) {
                    node = getNodes(node.color).get(node.item);
                    children.add(node);
                } else {
                    //if it isn't in nodes, we have not visited this node yet.
                    //So we can use it.
                    ancestors.add(node);
                    return;
                }
            }

            //at this point we have all the children in our list, so we pick one
            Node<Board> child = null;

            //if there are no children, this means this player cannot make a move
            //so per othello rules the other player can go
            //this is represented by the same board as the parent, but for the opponent
            if(children.size() == 0) {
                child = new Node<Board>(parent.item, opponentColor);
                if(getNodes(child.color).containsKey(child.item)) {
                    child = getNodes(child.color).get(child.item);
                } else {
                    ancestors.add(child);
                    return;
                }
            } else {
                //there are one or more children, using uct to pick one
                double logTotalPlays = children.stream().mapToDouble(c -> c.plays).reduce(0.0, (i, c) -> i + c);
                child = children.get(0);
                double oldScore = Double.NEGATIVE_INFINITY;
                for(Node<Board> node : children) {
                    double T = node.plays;
                    double X = node.score / (double)node.plays;
                    double S = X * X;
                    double V = S - X + Math.sqrt((2 * logTotalPlays) / T);
                    double score    = X + Math.sqrt((logTotalPlays / T) * Math.min(0.25, V));
                    if(child == null || score > oldScore) {
                        oldScore = score;
                        child = node;
                    }
                }
            }
            ancestors.add(child);
        }
        selectAndExpand(ancestors);
    }

    private FieldValue simulate(Node<Board> node) {
        Board board = node.item;
        FieldValue color = node.color;
        while(!board.isFinished()) {
            List<Position> positions = board.getPossiblePositions(color);
            if(positions.size() > 0) {
                Position randomPosition = positions.get(ThreadLocalRandom.current().nextInt(positions.size()));
                board = board.makeMove(new Move(randomPosition, color));
            }
            color = color.getOpponent();
        }
        simulationCount.incrementAndGet();
        return board.getWinner();
    }

    private void update(List<Node<Board>> nodes, double whiteScore, double blackScore) {
        for(Node<Board> node : nodes) {
            synchronized(node) {
                node.plays++;
                if(node.color == FieldValue.WHITE) {
                    node.score += blackScore;
                } else {
                    node.score += whiteScore;
                }
            }
        }
    }

    public HashMap<Position, Double> getMoveProbability() {
        HashMap<Position, Double> probabilities = new HashMap<Position, Double>();
        if(currentBoard == null) {
            return probabilities;
        }

        double sumScores = 0;
        List<Position> positions = currentBoard.getPossiblePositions(currentColor);
        for(Position position : positions) {
            Move newMove = new Move(position, currentColor);
            Board board = currentBoard.makeMove(newMove);
            Node<Board> newNode = getNodes(currentColor.getOpponent()).get(board);
            double score = 0;
            if(newNode != null) {
                score = newNode.score/ (double)newNode.plays;
            }
            sumScores += score;

            probabilities.put(position, score);
        }

        //normalize to 100%
        final double sumScoresFinal = sumScores;
        probabilities.replaceAll((k,v) -> v / sumScoresFinal);

        return probabilities;
    }

    private Move getBestMove(Node<Board> node) {
        List<Position> positions = node.item.getPossiblePositions(node.color);
        Move move = null;
        double score = Integer.MIN_VALUE;
        for(Position position : positions) {
            Move newMove = new Move(position, node.color);

            Board board = node.item.makeMove(newMove);
            Node<Board> newNode = getNodes(node.color.getOpponent()).get(board);
            double newScore = newNode.score/ (double)newNode.plays;
            System.out.println(newScore + " " + newNode.score + " / " + newNode.plays);
            if(move == null || newScore >= score) {
                score = newScore;
                move = newMove;
            }
        }

        return move;
    }

    class Node<T> {
        public int score = 0;
        public int plays = 0;
        public T item = null;
        public FieldValue color = null;

        public Node(T item, FieldValue color) {
            this.item = item;
            this.color = color;
        }
    }
}
