package dev.dominikbreu.archlens.mcp.tools.question;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.GraphQuery.DataFlowPathNode;
import dev.dominikbreu.archlens.cache.GraphQuery.DataFlowSinkNode;
import dev.dominikbreu.archlens.cache.GraphQuery.EntrypointNode;
import dev.dominikbreu.archlens.cache.GraphQuery.GraphNode;
import dev.dominikbreu.archlens.cache.GraphQuery.InterfaceNode;
import dev.dominikbreu.archlens.model.EntrypointType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Answers the {@code messaging_flow} intent: producers, consumers, broker/topic, and downstream continuation. */
public final class MessagingFlowAnswerer {

    private static final Set<EntrypointType> PRODUCER_TYPES = Set.of(EntrypointType.MESSAGING_PRODUCER);
    private static final Set<EntrypointType> CONSUMER_TYPES =
            Set.of(EntrypointType.MESSAGING_CONSUMER, EntrypointType.JMS_CONSUMER);

    private MessagingFlowAnswerer() {}

    /**
     * Answers a messaging question, resolving a topic/channel reference (or a specific
     * producer/consumer entrypoint) to its broker, consumers, non-entrypoint producer sinks,
     * and downstream continuation.
     *
     * @param graph the graph to query
     * @param args {@code entrypoint} (a specific producer/consumer) or {@code query}/{@code topic} (a topic/channel name)
     * @param recorder the graph-operation recorder
     * @return the messaging-flow answer
     */
    public static Answer answer(GraphQuery graph, Map<String, Object> args, QueryPlanRecorder recorder) {
        Answer result = new Answer();
        String entrypointRef = QuestionSupport.first(args, "entrypoint", "entrypointId", "entrypointName");
        String topicRef = QuestionSupport.first(args, "query", "topic", "component");

        List<EntrypointNode> messagingEntrypoints = new ArrayList<>();
        if (entrypointRef != null) {
            EntrypointNode entrypoint = QuestionSupport.entrypoint(graph, entrypointRef, result);
            if (entrypoint != null) messagingEntrypoints.add(entrypoint);
        }
        if (topicRef != null) {
            recorder.record("findNodes", "label", "Entrypoint");
            for (GraphNode node : graph.findNodes("Entrypoint", topicRef, Map.of(), QuestionSupport.DEFAULT_LIMIT)) {
                if (node instanceof EntrypointNode entrypoint
                        && (PRODUCER_TYPES.contains(entrypoint.type()) || CONSUMER_TYPES.contains(entrypoint.type()))) {
                    messagingEntrypoints.add(entrypoint);
                }
            }
        }

        String channel = null;
        String topic = null;
        List<Map<String, Object>> producers = new ArrayList<>();
        List<Map<String, Object>> consumers = new ArrayList<>();
        for (EntrypointNode entrypoint : messagingEntrypoints) {
            Map<String, Object> map = QuestionSupport.nodeMap(entrypoint);
            if (PRODUCER_TYPES.contains(entrypoint.type())) producers.add(map);
            else if (CONSUMER_TYPES.contains(entrypoint.type())) consumers.add(map);
            if (channel == null) channel = entrypoint.channelName();
            if (topic == null) topic = entrypoint.topic();
        }
        // Some frameworks (e.g. Spring's @KafkaListener(topics=...)) only populate channelName;
        // the destination name is the same string as the logical channel there.
        if (topic == null) topic = channel;

        String searchTerm = topic != null ? topic : topicRef;
        List<Map<String, Object>> producerSinks = new ArrayList<>();
        if (searchTerm != null) {
            recorder.record("findNodes", "label", "DataFlowSink");
            for (GraphNode node : graph.findNodes(
                    "DataFlowSink", searchTerm, Map.of("sinkKind", "messaging"), QuestionSupport.DEFAULT_LIMIT)) {
                if (node instanceof DataFlowSinkNode sink) {
                    producerSinks.add(QuestionSupport.nodeMap(sink));
                    if (topic == null) topic = sink.topic();
                    if (channel == null) channel = sink.channel();
                }
            }
        }

        String brokerSearch = topic != null ? topic : channel;
        String broker = null;
        if (brokerSearch != null) {
            recorder.record("findNodes", "label", "Interface");
            for (GraphNode node : graph.findNodes("Interface", brokerSearch, Map.of(), QuestionSupport.DEFAULT_LIMIT)) {
                if (node instanceof InterfaceNode iface && iface.broker() != null) {
                    broker = iface.broker().name();
                    break;
                }
            }
        }

        if (messagingEntrypoints.isEmpty() && producerSinks.isEmpty()) {
            result.unresolved.add(
                    entrypointRef == null && topicRef == null
                            ? "missing-subject: provide entrypoint or a topic/channel query"
                            : "no-messaging-entrypoint-or-sink-resolved");
            result.answer(emptyAnswer());
            return result;
        }
        result.subject(
                messagingEntrypoints.isEmpty()
                        ? Map.of("query", topicRef)
                        : QuestionSupport.nodeMap(messagingEntrypoints.getFirst()));

        List<Map<String, Object>> downstreamSinks = new ArrayList<>();
        for (EntrypointNode consumer : messagingEntrypoints.stream()
                .filter(e -> CONSUMER_TYPES.contains(e.type()))
                .toList()) {
            for (DataFlowPathNode path : graph.allDataFlowPaths()) {
                if (!path.entrypointId().serialize().equals(consumer.id().serialize())) continue;
                for (DataFlowSinkNode sink : graph.pathSinks(path.id())) {
                    downstreamSinks.add(QuestionSupport.nodeMap(sink));
                }
            }
        }

        if (producers.isEmpty() && producerSinks.isEmpty()) result.unresolved.add("no-producer-resolved-for-channel");
        if (consumers.isEmpty()) result.unresolved.add("no-consumer-resolved-for-channel");
        if (topic == null) result.unresolved.add("topic-not-resolved");

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("channel", channel);
        answer.put("broker", broker);
        answer.put("topic", topic);
        answer.put("producers", QuestionSupport.distinct(producers));
        answer.put("producerSinks", QuestionSupport.distinct(producerSinks));
        answer.put("consumers", QuestionSupport.distinct(consumers));
        answer.put("downstreamSinks", QuestionSupport.distinct(downstreamSinks));
        result.answer(answer);
        return result;
    }

    private static Map<String, Object> emptyAnswer() {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("channel", null);
        answer.put("broker", null);
        answer.put("topic", null);
        answer.put("producers", List.of());
        answer.put("producerSinks", List.of());
        answer.put("consumers", List.of());
        answer.put("downstreamSinks", List.of());
        return answer;
    }
}
