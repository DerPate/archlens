package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.MessagingBroker;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the broker behind a SmallRye Reactive Messaging channel by reading the
 * single configuration key {@code mp.messaging.{incoming|outgoing}.{channel}.connector}
 * from {@code application.properties}, {@code application.yaml}, or {@code application.yml}
 * inside a module's {@code src/main/resources} directory.
 *
 * <p>This is the only configuration file read in the entire extractor pipeline. Reading any
 * other property is forbidden — see {@code docs/ARCHITECTURE.md}.
 */
public class MessagingConfigResolver {

    private static final Pattern PROPERTY_KEY = Pattern.compile(
        "^mp\\.messaging\\.(incoming|outgoing)\\.(.+)\\.connector$");

    private static final List<String> RESOURCE_FILES = List.of(
        "application.properties",
        "application.yaml",
        "application.yml"
    );

    /** Creates a resolver. */
    public MessagingConfigResolver() {}

    /**
     * Resolves channel-to-broker mappings for a module rooted at {@code moduleRoot}.
     *
     * @param moduleRoot module root directory (the one containing {@code src/main/resources})
     * @return map from channel name to resolved broker; missing channels are simply absent
     */
    public Map<String, MessagingBroker> resolve(File moduleRoot) {
        Map<String, MessagingBroker> result = new LinkedHashMap<>();
        File resources = new File(moduleRoot, "src/main/resources");
        if (!resources.isDirectory()) return result;

        for (String name : RESOURCE_FILES) {
            File file = new File(resources, name);
            if (!file.isFile()) continue;
            try {
                if (name.endsWith(".properties")) {
                    readProperties(file, result);
                } else {
                    readYaml(file, result);
                }
            } catch (IOException ignored) {
            }
        }
        return result;
    }

    private void readProperties(File file, Map<String, MessagingBroker> result) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file.toPath())) {
            props.load(in);
        }
        for (String key : props.stringPropertyNames()) {
            Matcher m = PROPERTY_KEY.matcher(key);
            if (!m.matches()) continue;
            String channel = m.group(2);
            MessagingBroker broker = mapBroker(props.getProperty(key));
            result.put(channel, broker);
        }
    }

    @SuppressWarnings("unchecked")
    private void readYaml(File file, Map<String, MessagingBroker> result) throws IOException {
        Yaml yaml = new Yaml();
        Object root;
        try (InputStream in = Files.newInputStream(file.toPath())) {
            root = yaml.load(in);
        }
        if (!(root instanceof Map<?, ?> map)) return;
        Map<String, Object> flat = new LinkedHashMap<>();
        flatten("", (Map<String, Object>) map, flat);
        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            Matcher m = PROPERTY_KEY.matcher(entry.getKey());
            if (!m.matches()) continue;
            String channel = m.group(2);
            MessagingBroker broker = mapBroker(String.valueOf(entry.getValue()));
            result.put(channel, broker);
        }
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> in, Map<String, Object> out) {
        for (Map.Entry<String, Object> entry : in.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                flatten(key, (Map<String, Object>) nested, out);
            } else {
                out.put(key, value);
            }
        }
    }

    private MessagingBroker mapBroker(String connector) {
        if (connector == null) return MessagingBroker.UNKNOWN;
        return switch (connector.trim().toLowerCase()) {
            case "smallrye-kafka" -> MessagingBroker.KAFKA;
            case "smallrye-mqtt" -> MessagingBroker.MQTT;
            case "smallrye-amqp" -> MessagingBroker.AMQP;
            case "smallrye-rabbitmq" -> MessagingBroker.RABBITMQ;
            case "smallrye-pulsar" -> MessagingBroker.PULSAR;
            default -> MessagingBroker.UNKNOWN;
        };
    }
}
