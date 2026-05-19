package com.example.spring;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class OutboundGateway {
    private final RestTemplate restTemplate = new RestTemplate();
    private final WebClient webClient = WebClient.create();
    private final KafkaTemplate<String, String> kafkaTemplate = null;
    private final RabbitTemplate rabbitTemplate = null;
    private final JmsTemplate jmsTemplate = null;

    public void callHttp() {
        restTemplate.getForObject("${billing.base-url}/health", String.class);
        webClient.get().uri("https://inventory.example.test/items").retrieve();
    }

    public void publish(String payload) {
        kafkaTemplate.send("${orders.topic}", payload);
        rabbitTemplate.convertAndSend("orders.exchange", "orders.created", payload);
        jmsTemplate.convertAndSend("orders.jms", payload);
    }
}
