package com.example.kafka;

public interface IKafkaEvent {
    String getType();
    String getId();
}
