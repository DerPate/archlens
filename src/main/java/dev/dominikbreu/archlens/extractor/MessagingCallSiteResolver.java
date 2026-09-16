package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.MessagingBroker;
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

    /** Sentinel topic value used whenever a topic argument cannot be statically resolved. */
    private static final String UNRESOLVED = "(unresolved)";

    /** Direct-call method names, per broker, that identify a producer-side (send/publish) call site. */
    private static final Set<String> MQTT_PUBLISH_METHODS = Set.of("publish");
    /**
     * Direct-call method names that identify a consumer-side call site; both subscribe and
     * unsubscribe are treated as consumer activity since either references the topic being consumed.
     */
    private static final Set<String> MQTT_SUBSCRIBE_METHODS = Set.of("subscribe", "unsubscribe");
    /** Kafka producer method name, matched only when the tracked field's role is {@code PRODUCER}. */
    private static final Set<String> KAFKA_SEND_METHODS = Set.of("send");
    /** Kafka consumer method name, matched only when the tracked field's role is {@code CONSUMER}. */
    private static final Set<String> KAFKA_SUBSCRIBE_METHODS = Set.of("subscribe");

    private static final Set<String> COLLECTION_FACTORY_METHODS =
            Set.of("of", "asList", "singletonList", "singleton", "unmodifiableList");
    /**
     * HiveMQ fluent-builder method names that carry the topic argument; used as the anchor point for
     * detecting and resolving a fluent publish/subscribe chain.
     */
    private static final Set<String> FLUENT_TOPIC_SETTERS = Set.of("topic", "topicFilter");
    /** Fluent-chain entry method name that marks the chain as a publish (producer) chain. */
    private static final String FLUENT_PUBLISH_ENTRY = "publishWith";
    /** Fluent-chain entry method name that marks the chain as a subscribe (consumer) chain. */
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

    /**
     * Classifies one invocation as either a direct call on a tracked field or the topic-setting
     * anchor of a fluent chain, and appends any resulting finding(s) to {@code findings}. Direct-call
     * matches are tried first and, if found, short-circuit the fluent-chain check for this invocation.
     *
     * @param inv            invocation under inspection
     * @param trackedFields  field name → broker/role descriptor
     * @param findings       accumulator that findings are appended to
     */
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

    /**
     * Tries both direct-call resolution strategies for one invocation, in order: the single-finding
     * path ({@link #directCallFinding}) covering MQTT publish/subscribe and Kafka send, then the
     * multi-finding path ({@link #directCallFindings}) covering Kafka subscribe with a topic
     * collection. Matches from either path are appended to {@code findings}.
     *
     * @param inv        invocation under inspection
     * @param name       simple name of the invoked method
     * @param tf         broker/role descriptor of the receiver field
     * @param fieldName  name of the receiver field
     * @param findings   accumulator that any matched finding(s) are appended to
     * @return true if either resolution path produced at least one finding, false if the invocation
     *     did not match a known direct-call pattern
     */
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

    /**
     * Resolves a direct call that yields at most one finding: an MQTT {@code publish} (producer) or
     * {@code subscribe}/{@code unsubscribe} (consumer) call, or a Kafka {@code send} call on a field
     * whose tracked role is {@code PRODUCER} (topic taken from the {@code ProducerRecord} argument via
     * {@link #resolveKafkaSendTopic}).
     *
     * @param inv         invocation under inspection
     * @param methodName  simple name of the invoked method
     * @param tf          broker/role descriptor of the receiver field
     * @param fieldName   name of the receiver field
     * @return the resolved finding, or null if the method name/broker/role combination does not
     *     match any single-finding direct-call pattern
     */
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

    /**
     * Resolves a direct call that can yield multiple findings: a Kafka {@code subscribe} call on a
     * field whose tracked role is {@code CONSUMER}, with at least one argument. The first argument is
     * resolved as a collection of topic strings via {@link #resolveCollectionOfStrings}; when none of
     * its elements resolve, a single {@link #UNRESOLVED} finding is produced instead of none, so the
     * call site is still reported.
     *
     * @param inv         invocation under inspection
     * @param methodName  simple name of the invoked method
     * @param tf          broker/role descriptor of the receiver field
     * @param fieldName   name of the receiver field
     * @return one finding per resolved topic (or a single unresolved finding), or an empty list if
     *     the invocation does not match this pattern at all
     */
    private List<Finding> directCallFindings(
            CtInvocation<?> inv, String methodName, TrackedField tf, String fieldName) {
        List<Finding> out = new ArrayList<>();
        if (tf.broker() == MessagingBroker.KAFKA
                && tf.role() == Role.CONSUMER
                && KAFKA_SUBSCRIBE_METHODS.contains(methodName)
                && !inv.getArguments().isEmpty()) {
            List<String> topics = resolveCollectionOfStrings(inv.getArguments().getFirst());
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

    /**
     * Resolves a HiveMQ fluent chain anchored at {@code inv}, a {@code topic()}/{@code topicFilter()}
     * call. Walks inward through the chain's target invocations (which may include intermediate
     * calls such as {@code toAsync()}/{@code toBlocking()}), recording {@code PRODUCER} if a
     * {@link #FLUENT_PUBLISH_ENTRY} call is seen or {@code CONSUMER} if a
     * {@link #FLUENT_SUBSCRIBE_ENTRY} call is seen, until it reaches a non-invocation receiver
     * expression. That receiver must be a tracked MQTT field for the chain to be reported.
     *
     * @param inv            the {@code topic()}/{@code topicFilter()} invocation anchoring the chain
     * @param trackedFields  field name → broker/role descriptor
     * @return the resolved finding, or null if the topic call has no arguments, no publish/subscribe
     *     entry method was found in the chain, the chain's receiver is not a tracked field, or the
     *     tracked field's broker is not MQTT
     */
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

    /**
     * Extracts the tracked field name that {@code target} refers to, if any. Accepts both a field
     * read ({@code this.client} or {@code client}) and a variable read (a local alias resolving to
     * the field), matching either against the supplied set of tracked names.
     *
     * @param target        receiver expression to inspect
     * @param trackedNames  names of the fields being tracked
     * @return the matching tracked field name, or null if {@code target} does not resolve to one
     */
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

    /**
     * Resolves the invocation argument at {@code index} to a string via {@link #resolveString}.
     *
     * @param inv    invocation whose argument is being resolved
     * @param index  zero-based argument index
     * @return the resolved string, or {@link #UNRESOLVED} if there is no argument at that index or it
     *     does not statically resolve to a string
     */
    private String resolveStringArg(CtInvocation<?> inv, int index) {
        if (inv.getArguments().size() <= index) return UNRESOLVED;
        String resolved = resolveString(inv.getArguments().get(index));
        if (resolved != null) {
            return resolved;
        } else {
            return UNRESOLVED;
        }
    }

    /**
     * Resolves the topic argument of a Kafka {@code producer.send(new ProducerRecord<>(topic, ...))}
     * call by unwrapping the {@code send} call's first argument as a constructor call and resolving
     * its own first argument as a string.
     *
     * @param sendInv the {@code send(...)} invocation
     * @return the resolved topic, or {@link #UNRESOLVED} if {@code send} has no arguments, its first
     *     argument is not a constructor call, that constructor call has no arguments, or its first
     *     argument does not statically resolve to a string
     */
    private String resolveKafkaSendTopic(CtInvocation<?> sendInv) {
        if (sendInv.getArguments().isEmpty()) return UNRESOLVED;
        CtExpression<?> arg = sendInv.getArguments().getFirst();
        if (arg instanceof CtConstructorCall<?> ctor && !ctor.getArguments().isEmpty()) {
            String resolved = resolveString(ctor.getArguments().getFirst());
            if (resolved != null) {
                return resolved;
            } else {
                return UNRESOLVED;
            }
        }
        return UNRESOLVED;
    }

    /**
     * Resolves {@code expr} as a collection literal built through one of the recognized factory
     * methods ({@code List.of}, {@code Arrays.asList}, {@code Collections.singletonList},
     * {@code Collections.singleton}, {@code Collections.unmodifiableList}), returning the string
     * value of every argument. Resolution is all-or-nothing: if any argument fails to resolve to a
     * string, the result is cleared and an empty list is returned rather than a partial one.
     *
     * @param expr expression expected to be a collection-factory call
     * @return the resolved topic strings, or an empty list if {@code expr} is not a recognized
     *     factory call or any of its arguments does not statically resolve to a string
     */
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

    /**
     * Attempts to statically resolve {@code expr} to a string value: a string literal directly, a
     * field read whose declaring field has a string literal initializer (covers
     * {@code static final String TOPIC = "..."}), or a local variable read whose declaration has a
     * string literal initializer.
     *
     * @param expr expression to resolve
     * @return the resolved string, or null if {@code expr} is not backed by a literal in any of the
     *     supported forms
     */
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

    /**
     * Returns the source line number of {@code el}.
     *
     * @param el element whose position is being read
     * @return the 1-based source line, or 0 if the element has no valid position
     */
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

    /**
     * Spoon element filter that matches every {@link CtInvocation} in a type, used by {@link
     * #resolve} to enumerate all method call sites for scanning. The raw/unchecked cast to {@code
     * Class<T>} is required because {@code spoon.reflect.visitor.filter.AbstractFilter} is
     * constructed from a {@code Class} literal that cannot itself carry the wildcard-generic
     * invocation type.
     *
     * @param <T> invocation type matched by this filter (always {@code CtInvocation<?>} in practice)
     */
    private static final class TypeFilter<T extends CtInvocation<?>>
            extends spoon.reflect.visitor.filter.AbstractFilter<T> {
        @SuppressWarnings({"rawtypes", "unchecked"})
        TypeFilter() {
            super((Class) CtInvocation.class);
        }
    }
}
