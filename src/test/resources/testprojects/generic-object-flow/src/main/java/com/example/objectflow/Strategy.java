package com.example.objectflow;

public class Strategy {
    public Move next() {
        return new Rock();
    }
}
