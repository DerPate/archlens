package com.example.messaging;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

@ApplicationScoped
public class KafkaClientService {

    private static final String OUT_TOPIC = "orders.events";

    @Inject
    KafkaProducer<String, String> ordersProducer;

    @Inject
    KafkaConsumer<String, String> ordersConsumer;

    public void publish(String key, String value) {
        ordersProducer.send(new ProducerRecord<>(OUT_TOPIC, key, value));
    }

    public void subscribeAll() {
        ordersConsumer.subscribe(List.of("orders.commands", "orders.replies"));
    }
}
