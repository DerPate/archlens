package dev.dominikbreu.archlens.model;

import dev.dominikbreu.archlens.model.ids.ComponentId;

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
    /** Classification of how the topic argument was derived. */
    public TopicArgKind topicArgKind;
    /** Index of the topic argument parameter, if PARAM_REF; otherwise -1. */
    public int topicArgParamIndex = -1;

    /** Creates an empty site for JSON deserialization. */
    public OutboundSinkSite() {}

    /**
     * Returns a shallow clone of this site with {@code topic} and {@code channel} set to {@code resolvedTopic}.
     * All other fields are copied as-is from this instance.
     *
     * @param resolvedTopic the resolved topic name, or null to preserve null values
     * @return a new OutboundSinkSite with the same fields except topic and channel
     */
    public OutboundSinkSite withTopic(String resolvedTopic) {
        OutboundSinkSite copy = new OutboundSinkSite();
        copy.id = this.id;
        copy.kind = this.kind;
        copy.componentId = this.componentId;
        copy.method = this.method;
        copy.calleeQualifiedName = this.calleeQualifiedName;
        copy.calleeMethod = this.calleeMethod;
        copy.broker = this.broker;
        copy.topic = resolvedTopic;
        copy.channel = resolvedTopic;
        copy.topicPropertyKey = this.topicPropertyKey;
        copy.payloadVarName = this.payloadVarName;
        copy.payloadType = this.payloadType;
        copy.linkEvidence = this.linkEvidence;
        copy.source = this.source;
        copy.topicArgKind = this.topicArgKind;
        copy.topicArgParamIndex = this.topicArgParamIndex;
        return copy;
    }
}
