package dev.dominikbreu.archlens.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PropertyFileReaderTest {

    @Test
    void lastFileWinsWhenTheSameKeyAppearsInMultipleFiles() throws IOException {
        Path resources = Files.createTempDirectory("archlens-property-file-reader-");
        Files.writeString(
                resources.resolve("application.properties"), "mp.messaging.outgoing.orders.connector=smallrye-kafka\n");
        Files.writeString(
                resources.resolve("application.yml"),
                "mp:\n  messaging:\n    outgoing:\n      orders:\n        connector: smallrye-amqp\n");

        Map<String, PropertyFileReader.PropertyEntry> flat = PropertyFileReader.readAll(resources.toFile());

        assertThat(flat.get("mp.messaging.outgoing.orders.connector").value()).isEqualTo("smallrye-amqp");
        assertThat(flat.get("mp.messaging.outgoing.orders.connector").sourceFile())
                .isEqualTo("application.yml");
    }

    @Test
    void yamlNullValueIsOmittedRatherThanStringified() throws IOException {
        Path resources = Files.createTempDirectory("archlens-property-file-reader-");
        Files.writeString(
                resources.resolve("application.yaml"),
                "integration:\n  timeout:\n  base-url: https://example.internal\n");

        Map<String, PropertyFileReader.PropertyEntry> flat = PropertyFileReader.readAll(resources.toFile());

        assertThat(flat).doesNotContainKey("integration.timeout");
        assertThat(flat.get("integration.base-url").value()).isEqualTo("https://example.internal");
    }

    @Test
    void tracksWhichFileEachKeyWasReadFrom() throws IOException {
        Path resources = Files.createTempDirectory("archlens-property-file-reader-");
        Files.writeString(
                resources.resolve("application.properties"), "billing.client.base-url=https://billing.internal/api\n");

        Map<String, PropertyFileReader.PropertyEntry> flat = PropertyFileReader.readAll(resources.toFile());

        assertThat(flat.get("billing.client.base-url").sourceFile()).isEqualTo("application.properties");
    }
}
