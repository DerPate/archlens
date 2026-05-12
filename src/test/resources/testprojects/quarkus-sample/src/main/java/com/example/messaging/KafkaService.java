package com.example.messaging;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

@ApplicationScoped
public class KafkaService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(KafkaService.class);

    @Inject
    @Channel("audit-log")
    Emitter<String> auditEmitter;

    @Incoming("orders-in")
    public void consumeOrder(String payload) {
    }

    @Outgoing("orders-out")
    public String produceOrder() {
        return "order";
    }

    public void publishAudit(String msg) {
        auditEmitter.send(msg);
    }
}
