package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.MessagingBroker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;

/**
 * Resolves the broker topics referenced by raw Kafka and MQTT client field usages
 * within a class, statically. Supports two patterns:
 *
 * <ol>
 *   <li><b>Direct calls</b>: {@code mqttClient.publish(topic, ...)},
 *       {@code mqttClient.subscribe(topic, qos)}, {@code mqttClient.unsubscribe(topic)},
 *       {@code kafkaProducer.send(new ProducerRecord<>(topic, ...))},
 *       {@code kafkaConsumer.subscribe(List.of("topic", ...))}.</li>
 *   <li><b>HiveMQ fluent chains</b>: any chain rooted at a tracked field that contains
 *       {@code publishWith()} or {@code subscribeWith()} together with a {@code topic(...)}
 *       or {@code topicFilter(...)} call. The chain may include intermediate methods such as
 *       {@code toAsync()} or {@code toBlocking()}.</li>
 * </ol>
 *
 * <p>Topic argument resolution attempts, in order: string literal, field read whose
 * declaring field has a literal initializer (covers {@code static final String TOPIC = "..."}),
 * or local variable read with a literal initializer in the same scope. Anything else
 * yields a finding with topic {@code (unresolved)}.
 */
public class MessagingCallSiteResolver {

    private static final String UNRESOLVED = "(unresolved)";

    private static final Set<String> MQTT_PUBLISH_METHODS = Set.of("publish");
    private static final Set<String> MQTT_SUBSCRIBE_METHODS = Set.of("subscribe", "unsubscribe");
    private static final Set<String> KAFKA_SEND_METHODS = Set.of("send");
    private static final Set<String> KAFKA_SUBSCRIBE_METHODS = Set.of("subscribe");
    private static final Set<String> COLLECTION_FACTORY_METHODS =
            Set.of("of", "asList", "singletonList", "singleton", "unmodifiableList");
    private static final Set<String> FLUENT_TOPIC_SETTERS = Set.of("topic", "topicFilter");
    private static final String FLUENT_PUBLISH_ENTRY = "publishWith";
    private static final String FLUENT_SUBSCRIBE_ENTRY = "subscribeWith";

    /** Creates a resolver. */
    public MessagingCallSiteResolver() {}

    /**
     * Scans the type for call sites that act on the supplied tracked client fields.
     *
     * @param type           Spoon type to scan
     * @param trackedFields  field name → broker/role descriptor
     * @return list of findings, one per resolved (or unresolved) topic call site
     */
    public List<Finding> resolve(CtType<?> type, Map<String, TrackedField> trackedFields) {
        List<Finding> findings = new ArrayList<>();
        if (trackedFields.isEmpty()) return findings;

        for (CtInvocation<?> inv : type.getElements(new TypeFilter<>())) {
            collectFindings(inv, trackedFields, findings);
        }

        return findings;
    }

    private void collectFindings(CtInvocation<?> inv, Map<String, TrackedField> trackedFields, List<Finding> findings) {
        String name = inv.getExecutable() != null ? inv.getExecutable().getSimpleName() : null;
        if (name == null) return;

        // Direct call: target is a tracked field, method name is broker-specific.
        String fieldName = receiverFieldName(inv.getTarget(), trackedFields.keySet());
        if (fieldName != null && addDirectCallFindings(inv, name, trackedFields.get(fieldName), fieldName, findings)) {
            return;
        }

        // Fluent chain: anchor on topic()/topicFilter(), walk inward, only valid for MQTT.
        if (FLUENT_TOPIC_SETTERS.contains(name)) {
            Finding fluent = fluentFinding(inv, trackedFields);
            if (fluent != null) findings.add(fluent);
        }
    }

    private boolean addDirectCallFindings(
            CtInvocation<?> inv, String name, TrackedField tf, String fieldName, List<Finding> findings) {
        Finding direct = directCallFinding(inv, name, tf, fieldName);
        if (direct != null) {
            findings.add(direct);
            return true;
        }
        List<Finding> directList = directCallFindings(inv, name, tf, fieldName);
        if (!directList.isEmpty()) {
            findings.addAll(directList);
            return true;
        }
        return false;
    }

    private Finding directCallFinding(CtInvocation<?> inv, String methodName, TrackedField tf, String fieldName) {
        if (tf.broker() == MessagingBroker.MQTT) {
            if (MQTT_PUBLISH_METHODS.contains(methodName)) {
                return new Finding(fieldName, MessagingBroker.MQTT, Role.PRODUCER, resolveStringArg(inv, 0), line(inv));
            }
            if (MQTT_SUBSCRIBE_METHODS.contains(methodName)) {
                return new Finding(fieldName, MessagingBroker.MQTT, Role.CONSUMER, resolveStringArg(inv, 0), line(inv));
            }
        }
        if (tf.broker() == MessagingBroker.KAFKA) {
            if (tf.role() == Role.PRODUCER && KAFKA_SEND_METHODS.contains(methodName)) {
                String topic = resolveKafkaSendTopic(inv);
                return new Finding(fieldName, MessagingBroker.KAFKA, Role.PRODUCER, topic, line(inv));
            }
        }
        return null;
    }

    private List<Finding> directCallFindings(
            CtInvocation<?> inv, String methodName, TrackedField tf, String fieldName) {
        List<Finding> out = new ArrayList<>();
        if (tf.broker() == MessagingBroker.KAFKA
                && tf.role() == Role.CONSUMER
                && KAFKA_SUBSCRIBE_METHODS.contains(methodName)
                && !inv.getArguments().isEmpty()) {
            List<String> topics = resolveCollectionOfStrings(inv.getArguments().get(0));
            if (topics.isEmpty()) {
                out.add(new Finding(fieldName, MessagingBroker.KAFKA, Role.CONSUMER, UNRESOLVED, line(inv)));
            } else {
                for (String t : topics) {
                    out.add(new Finding(fieldName, MessagingBroker.KAFKA, Role.CONSUMER, t, line(inv)));
                }
            }
        }
        return out;
    }

    private Finding fluentFinding(CtInvocation<?> inv, Map<String, TrackedField> trackedFields) {
        if (inv.getArguments().isEmpty()) return null;
        Role role = null;
        CtExpression<?> cursor = inv.getTarget();
        while (cursor instanceof CtInvocation<?> chain) {
            String n;
            if (chain.getExecutable() != null) {
                n = chain.getExecutable().getSimpleName();
            } else {
                n = null;
            }
            if (FLUENT_PUBLISH_ENTRY.equals(n)) role = Role.PRODUCER;
            else if (FLUENT_SUBSCRIBE_ENTRY.equals(n)) role = Role.CONSUMER;
            cursor = chain.getTarget();
        }
        if (role == null) return null;

        String fieldName = receiverFieldName(cursor, trackedFields.keySet());
        if (fieldName == null) return null;
        TrackedField tf = trackedFields.get(fieldName);
        if (tf.broker() != MessagingBroker.MQTT) return null;

        String topic = resolveStringArg(inv, 0);
        return new Finding(fieldName, MessagingBroker.MQTT, role, topic, line(inv));
    }

    private String receiverFieldName(CtExpression<?> target, Set<String> trackedNames) {
        if (target instanceof CtFieldRead<?> fr && fr.getVariable() != null) {
            String n = fr.getVariable().getSimpleName();
            if (trackedNames.contains(n)) return n;
        }
        if (target instanceof CtVariableRead<?> vr && vr.getVariable() != null) {
            String n = vr.getVariable().getSimpleName();
            if (trackedNames.contains(n)) return n;
        }
        return null;
    }

    private String resolveStringArg(CtInvocation<?> inv, int index) {
        if (inv.getArguments().size() <= index) return UNRESOLVED;
        String resolved = resolveString(inv.getArguments().get(index));
        if (resolved != null) {
            return resolved;
        } else {
            return UNRESOLVED;
        }
    }

    private String resolveKafkaSendTopic(CtInvocation<?> sendInv) {
        if (sendInv.getArguments().isEmpty()) return UNRESOLVED;
        CtExpression<?> arg = sendInv.getArguments().get(0);
        if (arg instanceof CtConstructorCall<?> ctor && !ctor.getArguments().isEmpty()) {
            String resolved = resolveString(ctor.getArguments().get(0));
            if (resolved != null) {
                return resolved;
            } else {
                return UNRESOLVED;
            }
        }
        return UNRESOLVED;
    }

    private List<String> resolveCollectionOfStrings(CtExpression<?> expr) {
        List<String> out = new ArrayList<>();
        if (expr instanceof CtInvocation<?> inv) {
            String n;
            if (inv.getExecutable() != null) {
                n = inv.getExecutable().getSimpleName();
            } else {
                n = null;
            }
            if (n != null && COLLECTION_FACTORY_METHODS.contains(n)) {
                for (CtExpression<?> a : inv.getArguments()) {
                    String s = resolveString(a);
                    if (s != null) out.add(s);
                    else {
                        out.clear();
                        return out;
                    }
                }
            }
        }
        return out;
    }

    private String resolveString(CtExpression<?> expr) {
        if (expr instanceof CtLiteral<?> lit && lit.getValue() instanceof String s) return s;
        if (expr instanceof CtFieldRead<?> fr && fr.getVariable() != null) {
            CtField<?> declared = fr.getVariable().getFieldDeclaration();
            if (declared != null
                    && declared.getDefaultExpression() instanceof CtLiteral<?> lit
                    && lit.getValue() instanceof String s) return s;
        }
        if (expr instanceof CtVariableRead<?> vr && vr.getVariable() != null) {
            CtVariable<?> declared = vr.getVariable().getDeclaration();
            if (declared != null
                    && declared.getDefaultExpression() instanceof CtLiteral<?> lit
                    && lit.getValue() instanceof String s) return s;
        }
        return null;
    }

    private int line(CtElement el) {
        var pos = el.getPosition();
        if (pos != null && pos.isValidPosition()) {
            return pos.getLine();
        } else {
            return 0;
        }
    }

    /**
     * Information about a tracked client field — broker and role hint (PRODUCER/CONSUMER for Kafka, null for MQTT).
     *
     * @param broker the messaging broker resolved for this field
     * @param role   producer/consumer role hint; may be null for brokers where direction is not inferrable
     */
    public record TrackedField(MessagingBroker broker, Role role) {}

    /**
     * A resolved or unresolved call site referencing a tracked client field.
     *
     * @param fieldName name of the injected client field
     * @param broker    messaging broker for the call site
     * @param role      producer/consumer role
     * @param topic     resolved topic/channel name; null if not determinable
     * @param line      source line number of the call site
     */
    public record Finding(String fieldName, MessagingBroker broker, Role role, String topic, int line) {}

    /** Role determined from method name or fluent entry method. */
    public enum Role {
        /** Call site sends messages. */
        PRODUCER,
        /** Call site receives messages. */
        CONSUMER
    }

    private static final class TypeFilter<T extends CtInvocation<?>>
            extends spoon.reflect.visitor.filter.AbstractFilter<T> {
        @SuppressWarnings({"rawtypes", "unchecked"})
        TypeFilter() {
            super((Class) CtInvocation.class);
        }
    }
}
