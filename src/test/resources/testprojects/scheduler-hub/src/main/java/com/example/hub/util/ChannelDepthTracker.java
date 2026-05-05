package com.example.hub.util;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.atomic.AtomicLong;
@ApplicationScoped
public class ChannelDepthTracker {
    public AtomicLong primaryPending = new AtomicLong();
    public AtomicLong altPending = new AtomicLong();
    public void incrementPrimary() { primaryPending.incrementAndGet(); }
    public void incrementAlt() { altPending.incrementAndGet(); }
}
