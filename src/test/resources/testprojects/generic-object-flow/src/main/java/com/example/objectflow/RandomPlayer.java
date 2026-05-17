package com.example.objectflow;

public class RandomPlayer implements Player {
    private final Strategy strategy = new Strategy();

    @Override
    public Move nextMove() {
        return strategy.next();
    }
}
