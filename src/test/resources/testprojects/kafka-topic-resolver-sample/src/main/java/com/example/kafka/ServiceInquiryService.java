package com.example.kafka;

import org.springframework.stereotype.Service;

@Service
public class ServiceInquiryService {
    private final KafkaProducer kafkaProducer;

    public ServiceInquiryService(KafkaProducer kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    public void accept() {
        // Caller passes the topic as the 3rd argument of the overloaded wrapper —
        // resolver must find "serviceInquiryAccepted" at this call site.
        kafkaProducer.sendEvent(new Object(), "key", "serviceInquiryAccepted");
    }
}
