package com.example.events;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;

@ApplicationScoped
public class OrderEventProducer {

    @Inject
    Event<OrderCreated> events;
}
