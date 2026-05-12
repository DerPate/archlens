package com.example.events;

import io.vertx.core.eventbus.EventBus;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class VertxBusHandlerConsumer {

    @Inject
    EventBus eventBus;

    @PostConstruct
    void register() {
        eventBus.consumer("item.events").handler(message -> {
            // handle item event
        });
    }
}
