package com.example.objectflow;

import java.util.List;

public class MainApp {
    private static final GameService STATIC_GAME = new GameService();

    private final GameService fieldGame = new GameService();

    public static void main(String[] args) {
        new MainApp().run();
        STATIC_GAME.printStats();
    }

    public void run() {
        GameService localGame = new GameService();
        fieldGame.run();
        localGame.printStats();
    }

    public void collectionDispatch() {
        List<Player> players = List.of(new RandomPlayer(), new SimplePlayer());
        for (Player player : players) {
            player.nextMove();
        }
    }
}
