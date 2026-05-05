package com.example.hub.util;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.Callable;
@ApplicationScoped
public class ConcurrencyGuard {
    public <T> T runWithPermit(Callable<T> task) {
        try { return task.call(); } catch (Exception e) { return null; }
    }
}
