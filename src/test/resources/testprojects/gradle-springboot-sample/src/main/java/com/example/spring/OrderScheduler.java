package com.example.spring;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderScheduler {
    @Scheduled(cron = "0 0 * * * *")
    public void cleanUp() {}
}
