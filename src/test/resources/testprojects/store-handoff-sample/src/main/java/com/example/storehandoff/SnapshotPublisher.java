package com.example.storehandoff;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

/**
 * Scheduled component that reads the shared snapshot store through a getter
 * on the collaborating SnapshotIngestor and fans out to two downstream channels.
 */
@ApplicationScoped
public class SnapshotPublisher {

    @Inject
    SnapshotIngestor ingestor;

    @Channel("processed-a")
    Emitter<String> emitterA;

    @Channel("processed-b")
    Emitter<String> emitterB;

    @Scheduled(every = "5s")
    public void publishAll() {
        ingestor.getSnapshots().forEach((key, value) -> {
            if (key.startsWith("A")) {
                emitterA.send(value);
            } else {
                emitterB.send(value);
            }
        });
    }
}
