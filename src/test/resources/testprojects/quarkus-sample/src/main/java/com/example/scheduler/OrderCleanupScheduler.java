package com.example.scheduler;

import javax.enterprise.context.ApplicationScoped;
import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
public class OrderCleanupScheduler {

    @Scheduled(every = "1h")
    public void cleanup() {}

    @Scheduled(cron = "0 0 * * *")
    public void dailyReport() {}
}
