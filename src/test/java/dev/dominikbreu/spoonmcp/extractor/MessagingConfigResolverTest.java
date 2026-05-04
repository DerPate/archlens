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

        Map<String, MessagingConfigResolver.ChannelConfig> result = resolver.resolve(tmp.toFile());

        assertThat(result.get("orders-in").broker).isEqualTo(MessagingBroker.KAFKA);
        assertThat(result.get("audit-log").broker).isEqualTo(MessagingBroker.KAFKA);
        assertThat(result.get("device-events").broker).isEqualTo(MessagingBroker.MQTT);
        assertThat(result).doesNotContainKey("quarkus.http.port");
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

        Map<String, MessagingConfigResolver.ChannelConfig> result = resolver.resolve(tmp.toFile());

        assertThat(result.get("orders-in").broker).isEqualTo(MessagingBroker.KAFKA);
        assertThat(result.get("audit-log").broker).isEqualTo(MessagingBroker.RABBITMQ);
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

        Map<String, MessagingConfigResolver.ChannelConfig> result = resolver.resolve(tmp.toFile());

        assertThat(result.get("pulsar-events").broker).isEqualTo(MessagingBroker.PULSAR);
    }

    @Test
    void unknownConnectorMapsToUnknown(@TempDir Path tmp) throws Exception {
        Path resources = tmp.resolve("src/main/resources");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("application.properties"),
            "mp.messaging.incoming.weird.connector=some-future-connector\n");

        Map<String, MessagingConfigResolver.ChannelConfig> result = resolver.resolve(tmp.toFile());

        assertThat(result.get("weird").broker).isEqualTo(MessagingBroker.UNKNOWN);
    }

    @Test
    void missingResourcesReturnsEmptyMap(@TempDir Path tmp) {
        Map<String, MessagingConfigResolver.ChannelConfig> result = resolver.resolve(tmp.toFile());
        assertThat(result).isEmpty();
    }

    @Test
    void resolvesKafkaTopicAlongsideConnector(@TempDir Path tmp) throws Exception {
        Path resources = tmp.resolve("src/main/resources");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("application.properties"),
            "mp.messaging.incoming.snapshots.connector=smallrye-kafka\n" +
            "mp.messaging.incoming.snapshots.topic=device_snapshots\n");

        Map<String, MessagingConfigResolver.ChannelConfig> result = resolver.resolve(tmp.toFile());

        assertThat(result.get("snapshots").broker).isEqualTo(MessagingBroker.KAFKA);
        assertThat(result.get("snapshots").topic).isEqualTo("device_snapshots");
    }

    @Test
    void resolvesAmqpAddressAsTopic(@TempDir Path tmp) throws Exception {
        Path resources = tmp.resolve("src/main/resources");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("application.properties"),
            "mp.messaging.outgoing.audit.connector=smallrye-amqp\n" +
            "mp.messaging.outgoing.audit.address=audit.events\n");

        Map<String, MessagingConfigResolver.ChannelConfig> result = resolver.resolve(tmp.toFile());

        assertThat(result.get("audit").broker).isEqualTo(MessagingBroker.AMQP);
        assertThat(result.get("audit").topic).isEqualTo("audit.events");
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

        Map<String, MessagingConfigResolver.ChannelConfig> result = resolver.resolve(tmp.toFile());

        assertThat(result.get("k").broker).isEqualTo(MessagingBroker.KAFKA);
        assertThat(result.get("m").broker).isEqualTo(MessagingBroker.MQTT);
        assertThat(result.get("a").broker).isEqualTo(MessagingBroker.AMQP);
        assertThat(result.get("r").broker).isEqualTo(MessagingBroker.RABBITMQ);
        assertThat(result.get("p").broker).isEqualTo(MessagingBroker.PULSAR);
    }

    @Test
    void onlyResolvesIfResourcesDirectoryExists(@TempDir Path tmp) throws Exception {
        File noResources = tmp.resolve("not-a-module").toFile();
        Map<String, MessagingConfigResolver.ChannelConfig> result = resolver.resolve(noResources);
        assertThat(result).isEmpty();
    }
}
