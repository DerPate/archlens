package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.MessagingBroker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessagingConfigResolverTest {

    private final MessagingConfigResolver resolver = new MessagingConfigResolver();

    @Test
    void readsConnectorsFromProperties(@TempDir Path tmp) throws Exception {
        Path resources = tmp.resolve("src/main/resources");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("application.properties"),
            "mp.messaging.incoming.orders-in.connector=smallrye-kafka\n" +
            "mp.messaging.outgoing.audit-log.connector=smallrye-kafka\n" +
            "mp.messaging.incoming.device-events.connector=smallrye-mqtt\n" +
            "quarkus.http.port=8080\n");

        Map<String, MessagingBroker> result = resolver.resolve(tmp.toFile());

        assertThat(result)
            .containsEntry("orders-in", MessagingBroker.KAFKA)
            .containsEntry("audit-log", MessagingBroker.KAFKA)
            .containsEntry("device-events", MessagingBroker.MQTT)
            .doesNotContainKey("quarkus.http.port");
    }

    @Test
    void readsConnectorsFromYaml(@TempDir Path tmp) throws Exception {
        Path resources = tmp.resolve("src/main/resources");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("application.yaml"),
            "mp:\n" +
            "  messaging:\n" +
            "    incoming:\n" +
            "      orders-in:\n" +
            "        connector: smallrye-kafka\n" +
            "    outgoing:\n" +
            "      audit-log:\n" +
            "        connector: smallrye-rabbitmq\n");

        Map<String, MessagingBroker> result = resolver.resolve(tmp.toFile());

        assertThat(result)
            .containsEntry("orders-in", MessagingBroker.KAFKA)
            .containsEntry("audit-log", MessagingBroker.RABBITMQ);
    }

    @Test
    void readsConnectorsFromYml(@TempDir Path tmp) throws Exception {
        Path resources = tmp.resolve("src/main/resources");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("application.yml"),
            "mp:\n" +
            "  messaging:\n" +
            "    incoming:\n" +
            "      pulsar-events:\n" +
            "        connector: smallrye-pulsar\n");

        Map<String, MessagingBroker> result = resolver.resolve(tmp.toFile());

        assertThat(result).containsEntry("pulsar-events", MessagingBroker.PULSAR);
    }

    @Test
    void unknownConnectorMapsToUnknown(@TempDir Path tmp) throws Exception {
        Path resources = tmp.resolve("src/main/resources");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("application.properties"),
            "mp.messaging.incoming.weird.connector=some-future-connector\n");

        Map<String, MessagingBroker> result = resolver.resolve(tmp.toFile());

        assertThat(result).containsEntry("weird", MessagingBroker.UNKNOWN);
    }

    @Test
    void missingResourcesReturnsEmptyMap(@TempDir Path tmp) {
        Map<String, MessagingBroker> result = resolver.resolve(tmp.toFile());
        assertThat(result).isEmpty();
    }

    @Test
    void ignoresNonConnectorMessagingKeys(@TempDir Path tmp) throws Exception {
        Path resources = tmp.resolve("src/main/resources");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("application.properties"),
            "mp.messaging.incoming.orders-in.connector=smallrye-kafka\n" +
            "mp.messaging.incoming.orders-in.topic=orders-events\n" +
            "mp.messaging.incoming.orders-in.bootstrap.servers=localhost:9092\n");

        Map<String, MessagingBroker> result = resolver.resolve(tmp.toFile());

        assertThat(result).hasSize(1).containsEntry("orders-in", MessagingBroker.KAFKA);
    }

    @Test
    void readsAllSupportedBrokers(@TempDir Path tmp) throws Exception {
        Path resources = tmp.resolve("src/main/resources");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("application.properties"),
            "mp.messaging.incoming.k.connector=smallrye-kafka\n" +
            "mp.messaging.incoming.m.connector=smallrye-mqtt\n" +
            "mp.messaging.incoming.a.connector=smallrye-amqp\n" +
            "mp.messaging.incoming.r.connector=smallrye-rabbitmq\n" +
            "mp.messaging.incoming.p.connector=smallrye-pulsar\n");

        Map<String, MessagingBroker> result = resolver.resolve(tmp.toFile());

        assertThat(result)
            .containsEntry("k", MessagingBroker.KAFKA)
            .containsEntry("m", MessagingBroker.MQTT)
            .containsEntry("a", MessagingBroker.AMQP)
            .containsEntry("r", MessagingBroker.RABBITMQ)
            .containsEntry("p", MessagingBroker.PULSAR);
    }

    @Test
    void onlyResolvesIfResourcesDirectoryExists(@TempDir Path tmp) throws Exception {
        File noResources = tmp.resolve("not-a-module").toFile();
        Map<String, MessagingBroker> result = resolver.resolve(noResources);
        assertThat(result).isEmpty();
    }
}
