package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.MessagingBroker;
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
import org.yaml.snakeyaml.Yaml;

/**
 * Resolves SmallRye Reactive Messaging channel configuration from
 * {@code application.properties}, {@code application.yaml}, or {@code application.yml}
 * inside a module's {@code src/main/resources} directory.
 *
 * <p>The resolver reads two property families per channel:
 * <ul>
 *   <li>{@code mp.messaging.{incoming|outgoing}.{channel}.connector} — broker</li>
 *   <li>{@code mp.messaging.{incoming|outgoing}.{channel}.{topic|address|queue.name|exchange.name}}
 *       — broker-side destination name (Kafka topic, AMQP address, RabbitMQ queue/exchange)</li>
 * </ul>
 *
 * <p>This is the only configuration file read in the entire extractor pipeline. Reading any
 * other property is forbidden — see {@code docs/ARCHITECTURE.md}.
 */
public class MessagingConfigResolver {

    private static final Pattern CONNECTOR_KEY =
            Pattern.compile("^mp\\.messaging\\.(incoming|outgoing)\\.(.+)\\.connector$");

    private static final Pattern DESTINATION_KEY = Pattern.compile(
            "^mp\\.messaging\\.(incoming|outgoing)\\.(.+)\\.(topic|address|queue\\.name|exchange\\.name)$");

    private static final List<String> RESOURCE_FILES =
            List.of("application.properties", "application.yaml", "application.yml");

    /** Per-channel resolved configuration. */
    public static final class ChannelConfig {
        /** Broker resolved from the {@code connector} property; never null. */
        public final MessagingBroker broker;
        /** Broker-side destination name (topic / address / queue) when set; otherwise null. */
        public final String topic;

        /**
         * Creates a config entry.
         *
         * @param broker resolved broker; null is coerced to {@link MessagingBroker#UNKNOWN}
         * @param topic  broker-side destination name; null when not configured
         */
        public ChannelConfig(MessagingBroker broker, String topic) {
            this.broker = broker == null ? MessagingBroker.UNKNOWN : broker;
            this.topic = topic;
        }
    }

    /** Creates a resolver. */
    public MessagingConfigResolver() {}

    /**
     * Resolves channel configuration for a module rooted at {@code moduleRoot}.
     *
     * @param moduleRoot module root directory (the one containing {@code src/main/resources})
     * @return map from channel name to {@link ChannelConfig}; missing channels are simply absent
     */
    public Map<String, ChannelConfig> resolve(File moduleRoot) {
        Map<String, MessagingBroker> brokers = new LinkedHashMap<>();
        Map<String, String> topics = new LinkedHashMap<>();
        File resources = new File(moduleRoot, "src/main/resources");
        if (!resources.isDirectory()) return Map.of();

        for (String name : RESOURCE_FILES) {
            File file = new File(resources, name);
            if (!file.isFile()) continue;
            try {
                Map<String, String> flat;
                if (name.endsWith(".properties")) {
                    flat = readProperties(file);
                } else {
                    flat = readYaml(file);
                }
                collect(flat, brokers, topics);
            } catch (IOException _) {
            }
        }

        Map<String, ChannelConfig> result = new LinkedHashMap<>();
        for (Map.Entry<String, MessagingBroker> e : brokers.entrySet()) {
            result.put(e.getKey(), new ChannelConfig(e.getValue(), topics.get(e.getKey())));
        }
        // Channels declaring only a destination (no connector) — keep topic, broker = UNKNOWN.
        for (Map.Entry<String, String> e : topics.entrySet()) {
            result.computeIfAbsent(e.getKey(), k -> new ChannelConfig(MessagingBroker.UNKNOWN, e.getValue()));
        }
        return result;
    }

    private void collect(Map<String, String> flat, Map<String, MessagingBroker> brokers, Map<String, String> topics) {
        for (Map.Entry<String, String> entry : flat.entrySet()) {
            Matcher cm = CONNECTOR_KEY.matcher(entry.getKey());
            if (cm.matches()) {
                brokers.put(cm.group(2), mapBroker(entry.getValue()));
                continue;
            }
            Matcher dm = DESTINATION_KEY.matcher(entry.getKey());
            if (dm.matches()) {
                // First value wins — incoming/outgoing rarely conflict for the same channel name.
                topics.putIfAbsent(dm.group(2), entry.getValue());
            }
        }
    }

    private Map<String, String> readProperties(File file) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file.toPath())) {
            props.load(in);
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            out.put(key, props.getProperty(key));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readYaml(File file) throws IOException {
        Yaml yaml = new Yaml();
        Object root;
        try (InputStream in = Files.newInputStream(file.toPath())) {
            root = yaml.load(in);
        }
        Map<String, String> out = new LinkedHashMap<>();
        if (!(root instanceof Map<?, ?> map)) return out;
        Map<String, Object> flat = new LinkedHashMap<>();
        flatten("", (Map<String, Object>) map, flat);
        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            out.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> in, Map<String, Object> out) {
        for (Map.Entry<String, Object> entry : in.entrySet()) {
            String key;
            if (prefix.isEmpty()) {
                key = entry.getKey();
            } else {
                key = prefix + "." + entry.getKey();
            }
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
