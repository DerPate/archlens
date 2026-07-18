package com.example.kafka;

public class SisKafkaEvent implements IKafkaEvent {
    private final String id;

    public SisKafkaEvent(String id) {
        this.id = id;
    }

    @Override
    public String getType() {
        return "sisPDFCreation";
    }

    @Override
    public String getId() {
        return id;
    }
}
