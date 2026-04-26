package com.example.mdb;

import javax.ejb.MessageDriven;
import javax.jms.Message;
import javax.jms.MessageListener;

@MessageDriven
public class NotificationMDB implements MessageListener {

    @Override
    public void onMessage(Message message) {}
}
