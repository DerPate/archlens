package com.example.objectflow;

public class StateStoreProvider {
    private final StateStore store = new StateStore();

    public StateStore store() {
        return store;
    }
}
