package com.example.objectflow;

public class GameService {
    private final Player[] players = new Player[] { new RandomPlayer(), new SimplePlayer() };

    public void run() {
        players[0].nextMove();
        players[1].nextMove();
        validate(new Rock(), new Paper());
    }

    public void validate(Move left, Move right) {
        left.compareTo(right);
    }

    public void printStats() {
        System.out.println("stats");
    }
}
