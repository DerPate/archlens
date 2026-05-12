package com.example.api;

import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint("/chat/{id}")
public class ChatResource {

    @OnMessage
    public void onMessage(String message, Session session) {
    }
}
