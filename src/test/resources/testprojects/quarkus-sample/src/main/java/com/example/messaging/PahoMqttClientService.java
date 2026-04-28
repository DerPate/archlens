package com.example.messaging;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttClient;

@ApplicationScoped
public class PahoMqttClientService {

    private static final String DEVICE_STATE_TOPIC = "device/state";

    @Inject
    MqttClient pahoBlocking;

    @Inject
    IMqttAsyncClient pahoAsync;

    public void publishState(byte[] payload) throws Exception {
        pahoBlocking.publish(DEVICE_STATE_TOPIC, payload, 1, false);
    }

    public void subscribeKnown() throws Exception {
        String filter = "orders/updated";
        pahoAsync.subscribe(filter, 1);
    }

    public void subscribeFromCaller(String topic) throws Exception {
        pahoAsync.subscribe(topic, 1);
    }
}
