package com.example.events;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

@ApplicationScoped
public class OrderEventConsumer {

    public void onOrderCreated(@Observes OrderCreated event) {
    }
}
