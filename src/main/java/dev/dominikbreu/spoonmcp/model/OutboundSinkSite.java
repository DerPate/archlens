package dev.dominikbreu.spoonmcp.model;

import dev.dominikbreu.spoonmcp.model.ids.ComponentId;

/**
 * Invocation site against outbound infrastructure whose callee is not a project component
 * (e.g. {@code java.nio.file.Files}, AWS S3 SDK, Azure Blob SDK, SmallRye Reactive Messaging
 * {@code Emitter}/{@code MutinyEmitter}, Vert.x {@code EventBus}). Materialised so the
 * data-flow tracer can emit a {@code FILE_OUTBOUND}, {@code OBJECT_STORAGE},
 * {@code MESSAGING}, or {@code EVENT_BUS} sink even though no {@link CallEdge} connects
 * the caller to the framework class.
 */
public class OutboundSinkSite {
    /** Stable identifier: {@code outbound:<componentId>#<method>:<index>}. */
    public String id;
    /** Sink kind — one of {@link DataFlowSink.Kind#FILE_OUTBOUND}, {@link DataFlowSink.Kind#OBJECT_STORAGE},
     * {@link DataFlowSink.Kind#MESSAGING}, or {@link DataFlowSink.Kind#EVENT_BUS}. */
    public DataFlowSink.Kind kind;
    /** Component containing the call site. */
    public ComponentId componentId;
    /** Method containing the call site. */
    public String method;
    /** Qualified name of the callee's declaring type. */
    public String calleeQualifiedName;
    /** Simple name of the called method. */
    public String calleeMethod;
    /** For MESSAGING/EVENT_BUS sites: the channel/topic name from {@code @Channel}, or null. */
    public String channel;
    /** Messaging broker for messaging sites when known. */
    public MessagingBroker broker;
    /** Normalized broker destination/topic. */
    public String topic;
    /** Spring config property key that supplied {@link #topic}, when known. */
    public String topicPropertyKey;
    /** Variable name used as the outbound payload argument, when known. */
    public String payloadVarName;
    /** Fully-qualified payload type, when source-derived. */
    public String payloadType;
    /** Short evidence label explaining how this outbound site was extracted. */
    public String linkEvidence;
    /** Source location. */
    public SourceInfo source;

    /** Creates an empty site for JSON deserialization. */
    public OutboundSinkSite() {}
}
