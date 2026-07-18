package com.example.kafka;

import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
public class PushNotificationService {
    private final KafkaJsonProducer kafkaJsonProducer;

    public PushNotificationService(KafkaJsonProducer kafkaJsonProducer) {
        this.kafkaJsonProducer = kafkaJsonProducer;
    }

    public void send(String payload) {
        // Topic is in Message header — resolver must find PUSH_NOTIFICATION_TOPIC = "pushNotification"
        Message<String> msg = MessageBuilder.withPayload(payload)
                .setHeader(KafkaHeaders.TOPIC, KafkaConfig.PUSH_NOTIFICATION_TOPIC)
                .build();
        kafkaJsonProducer.sendMessage(msg);
    }
}
