package dev.dominikbreu.spoonmcp.model;

/**
 * Invocation site against outbound infrastructure whose callee is not a project component
 * (e.g. {@code java.nio.file.Files}, AWS S3 SDK, Azure Blob SDK). Materialised so the
 * data-flow tracer can emit a {@code FILE_OUTBOUND} or {@code OBJECT_STORAGE} sink even
 * though no {@link CallEdge} connects the caller to the SDK class.
 */
public class OutboundSinkSite {
    /** Stable identifier: {@code outbound:<componentId>#<method>:<index>}. */
    public String id;
    /** Sink kind — must be one of {@link DataFlowSink.Kind#FILE_OUTBOUND} or {@link DataFlowSink.Kind#OBJECT_STORAGE}. */
    public DataFlowSink.Kind kind;
    /** Component containing the call site. */
    public String componentId;
    /** Method containing the call site. */
    public String method;
    /** Qualified name of the callee's declaring type. */
    public String calleeQualifiedName;
    /** Simple name of the called method. */
    public String calleeMethod;
    /** Source location. */
    public SourceInfo source;

    /** Creates an empty site for JSON deserialization. */
    public OutboundSinkSite() {}
}
