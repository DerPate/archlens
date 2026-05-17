package com.example.objectflow;

public class StateWriter {
    public void write(StateStoreProvider provider, String id, String value) {
        provider.store().cache().put(id, value);
    }
}
