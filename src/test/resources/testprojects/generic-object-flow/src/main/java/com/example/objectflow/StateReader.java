package com.example.objectflow;

public class StateReader {
    public int readSize(StateStoreProvider provider) {
        return provider.store().cache().values().size();
    }
}
