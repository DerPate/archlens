package com.example.spring;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderListeners {
    @KafkaListener(topics = "${orders.topic}")
    public void onKafka(String payload) {}

    @KafkaListener(topics = {OrderTopics.RETRY_TOPIC})
    public void onKafkaRetry(String payload) {}

    @RabbitListener(queues = "orders.queue")
    public void onRabbit(String payload) {}

    @JmsListener(destination = "orders.jms")
    public void onJms(String payload) {}
}
