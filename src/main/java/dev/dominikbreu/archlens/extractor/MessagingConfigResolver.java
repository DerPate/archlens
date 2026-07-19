package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.MessagingBroker;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * <p>File reading and flattening is shared with {@link ConfigPropertyResolver} via
 * {@link PropertyFileReader}. This resolver's own scope stays narrow: only the
 * {@code mp.messaging.*} property family — see {@code docs/ARCHITECTURE.md}.
 */
public class MessagingConfigResolver {

    private static final Pattern CONNECTOR_KEY =
            Pattern.compile("^mp\\.messaging\\.(incoming|outgoing)\\.(.+)\\.connector$");

    private static final Pattern DESTINATION_KEY = Pattern.compile(
            "^mp\\.messaging\\.(incoming|outgoing)\\.(.+)\\.(topic|address|queue\\.name|exchange\\.name)$");

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
        File resources = new File(moduleRoot, "src/main/resources");
        Map<String, String> flat = PropertyFileReader.readAll(resources);
        Map<String, MessagingBroker> brokers = new LinkedHashMap<>();
        Map<String, String> topics = new LinkedHashMap<>();
        collect(flat, brokers, topics);

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
