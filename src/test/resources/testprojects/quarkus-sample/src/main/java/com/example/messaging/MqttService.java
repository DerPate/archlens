package com.example.messaging;

import javax.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class MqttService {

    @Incoming("device-events")
    public void consumeDeviceEvent(String payload) {
    }
}
