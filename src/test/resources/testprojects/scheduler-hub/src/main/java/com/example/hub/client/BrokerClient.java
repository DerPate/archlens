package com.example.hub.client;
import jakarta.enterprise.context.ApplicationScoped;
@ApplicationScoped
public class BrokerClient {
    public void setClientName(String name) {}
    public void connect() {}
    public void disconnect() {}
    public void publish(String topic, String payload) {}
}
