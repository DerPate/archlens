package com.example.messaging;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class HiveMqClientService {

    @Inject
    Mqtt5Client hivemq5;

    @Inject
    Mqtt3AsyncClient hivemq3Async;

    public void publishTelemetry() {
        hivemq5.publishWith().topic("device/telemetry").send();
    }

    public void publishViaAsync() {
        hivemq5.toAsync().publishWith().topic("device/control").send();
    }

    public void subscribeUpdates() {
        hivemq3Async.subscribeWith().topicFilter("device/+").send();
    }
}
