package dev.dominikbreu.archlens.renderer;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.extractor.PipelineGraphBuilder.Chain;
import dev.dominikbreu.archlens.extractor.PipelineGraphBuilder.Segment;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.DataFlowSink;
import dev.dominikbreu.archlens.model.DataFlowStep;
import dev.dominikbreu.archlens.model.Entrypoint;
import dev.dominikbreu.archlens.model.ids.GraphNodeId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders a single pipeline {@link Chain} as a Mermaid {@code flowchart TD}.
 *
 * <p>Each segment becomes a vertical sequence of step nodes shaped by the component's
 * architectural role. Between segments, a boundary node is emitted whose shape and
 * style reflect the linking sink kind via {@link MermaidStyle} roles: STORE/PERSISTENCE
 * render as a cylinder, MESSAGING as a stadium shape, and EVENT_BUS as a double circle.
 */
public class MermaidPipelineRenderer {

    /** Creates a renderer with default styling. */
    public MermaidPipelineRenderer() {}

    /**
     * Renders {@code chain} as Mermaid text.
     *
     * @param chain pipeline chain to render
     * @param graph graph query interface for component lookups
     * @return Mermaid flowchart text
     */
    public String render(Chain chain, GraphQuery graph) {
        if (chain == null || chain.segments.isEmpty()) {
            return MermaidTemplateAdapters.flowchart(
                    "TD",
                    List.of(MermaidDocument.Statement.note("    ", "note", "no pipeline chain")),
                    new MermaidStyle.Tracker());
        }
        RenderState st = new RenderState();
        for (int segIdx = 0; segIdx < chain.segments.size(); segIdx++) {
            renderSegment(st, chain, segIdx, graph);
        }
        return assemble(st);
    }

    /** Mutable accumulator threaded through per-segment rendering. */
    private static final class RenderState {
        final List<MermaidDocument.Statement> nodes = new ArrayList<>();
        final List<MermaidDocument.Statement> edges = new ArrayList<>();
        final MermaidStyle.Tracker tracker = new MermaidStyle.Tracker();
        int boundaryCounter;
        String previousLastNode;
        String previousSinkLabel;
    }

    /**
     * Renders one pipeline {@link Segment}: an optional boundary node linking from the previous
     * segment, the segment's header node, its step chain, and any terminal sinks it reaches.
     *
     * <p>After rendering, records this segment's last step node and the label of the sink that
     * links to the next segment (if any) in {@code st}, so the next call to {@code renderSegment}
     * (via {@link #renderBoundary}) can draw the connecting edge.
     *
     * @param st     accumulator for nodes/edges/tracker state, mutated in place
     * @param chain  full pipeline chain, used to look up the next segment's incoming sink
     * @param segIdx index of the segment being rendered within {@code chain.segments}
     * @param graph  graph query interface for component lookups
     */
    private void renderSegment(RenderState st, Chain chain, int segIdx, GraphQuery graph) {
        Segment seg = chain.segments.get(segIdx);
        Entrypoint ep = seg.entrypoint;

        // Boundary node from previous segment (skip for first segment).
        if (seg.incomingSink != null) {
            renderBoundary(st, seg, ep, segIdx, graph);
        }
        String headerNodeId = renderHeader(st, seg, ep, segIdx, graph);
        Map<String, String> callerNodeIds = new HashMap<>();
        String previousNodeInSeg = renderSteps(st, seg, segIdx, headerNodeId, graph, callerNodeIds);
        renderTerminalSinks(st, chain, seg, segIdx, previousNodeInSeg, callerNodeIds);

        // Determine which sink (if any) links to the next segment.
        DataFlowSink linkOut =
                (segIdx + 1 < chain.segments.size()) ? chain.segments.get(segIdx + 1).incomingSink : null;
        st.previousLastNode = previousNodeInSeg;
        st.previousSinkLabel = linkOut != null && linkOut.method != null ? linkOut.method : "";
    }

    /**
     * Renders the boundary node that carries the value from the previous segment's sink into
     * {@code seg}, plus its two edges: an incoming edge from the previous segment's last node
     * (labeled with the sink method that produced it, omitted for the first segment) and an
     * outgoing edge into this segment's header node (labeled with the entrypoint name that
     * consumes it). The boundary's shape/style is chosen by {@link #boundaryRole} from
     * {@code seg.incomingSink.kind}, and its label by {@link #boundaryLabel}.
     *
     * @param st     accumulator for nodes/edges/tracker state, mutated in place
     * @param seg    segment whose {@code incomingSink} defines this boundary
     * @param ep     entrypoint that consumes the boundary value, used for the outgoing edge label
     * @param segIdx index of {@code seg}, used to target its header node id ({@code "S<segIdx>_0"})
     * @param graph  graph query interface used by {@link #boundaryLabel} to resolve owning components
     */
    private void renderBoundary(RenderState st, Segment seg, Entrypoint ep, int segIdx, GraphQuery graph) {
        st.boundaryCounter++;
        String boundaryId = "B" + st.boundaryCounter;
        String boundaryLabel = boundaryLabel(seg.incomingSink, graph);
        MermaidStyle.Role role = boundaryRole(seg.incomingSink.kind);
        st.nodes.add(MermaidDocument.Statement.node(st.tracker.node("    ", boundaryId, boundaryLabel, role)));
        if (st.previousLastNode != null) {
            st.edges.add(MermaidDocument.Statement.edge(MermaidDocument.Edge.labeled(
                    "    ",
                    st.previousLastNode,
                    boundaryId,
                    escape(st.previousSinkLabel == null ? "" : st.previousSinkLabel),
                    false)));
        }
        String consumeLabel = (ep != null && ep.name != null) ? ep.name : "";
        st.edges.add(MermaidDocument.Statement.edge(
                MermaidDocument.Edge.labeled("    ", boundaryId, "S" + segIdx + "_0", escape(consumeLabel), false)));
    }

    /**
     * Renders the header node for {@code seg}: the entry point into this segment, always assigned
     * id {@code "S<segIdx>_0"} so {@link #renderBoundary} and {@link #renderSteps} can address it
     * without threading the id back through {@code st}.
     *
     * <p>The header's component name is resolved in order of preference: the entrypoint's
     * component (looked up via {@code graph}), then the first step's component name when the
     * segment has steps, then the literal {@code "?"} when neither is available. Its label appends
     * the entrypoint method name (e.g. {@code "OrderService.handle"}) when {@code ep.name} is set,
     * and its shape/style come from {@link MermaidStyle#roleFor} on the resolved component's type.
     *
     * @param st          accumulator for nodes/edges/tracker state, mutated in place
     * @param seg         segment being rendered
     * @param ep          entrypoint owning this segment, may be null
     * @param segIdx      index of {@code seg}, used to build the header node id
     * @param graph       graph query interface for resolving the entrypoint's component
     * @return the header node id ({@code "S<segIdx>_0"})
     */
    private String renderHeader(RenderState st, Segment seg, Entrypoint ep, int segIdx, GraphQuery graph) {
        String headerNodeId = "S" + segIdx + "_0";
        GraphQuery.ComponentNode headerComp = null;
        if (ep != null && ep.componentId != null) {
            GraphQuery.GraphNode n = graph.component(ep.componentId);
            if (n instanceof GraphQuery.ComponentNode cn) headerComp = cn;
        }
        String headerComponentName;
        if (headerComp != null) {
            headerComponentName = headerComp.name();
        } else if (seg.path.steps.isEmpty()) {
            headerComponentName = "?";
        } else {
            headerComponentName = seg.path.steps.getFirst().componentName;
        }
        ComponentType headerType = headerComp != null ? headerComp.type() : null;
        String headerLabel = headerComponentName + (ep != null && ep.name != null ? "." + ep.name : "");
        MermaidStyle.Role role = MermaidStyle.roleFor(headerType);
        st.nodes.add(MermaidDocument.Statement.node(st.tracker.node("    ", headerNodeId, headerLabel, role)));
        return headerNodeId;
    }

    /**
     * Renders the intra-segment step chain starting at {@code headerNodeId}, preferring the
     * recorded topology graph over the flat steps list when one is available.
     *
     * <p>Looks up recorded topology nodes/edges for {@code seg.path} via {@code graph}; when any
     * exist, delegates to {@link #renderStepsFromTopology} so branching/conditional structure is
     * preserved. Otherwise falls back to a straight-line rendering of {@code seg.path.steps},
     * which is the only structure available for paths recorded before topology capture existed or
     * for which topology capture did not apply.
     *
     * @param st             accumulator for nodes/edges/tracker state, mutated in place
     * @param seg            segment whose path is being rendered
     * @param segIdx         index of {@code seg}, used to build step node ids
     * @param headerNodeId   id of the segment's header node, the chain's starting point
     * @param graph          graph query interface for topology and component lookups
     * @param callerNodeIds  map from component id to the node id of the step that last called it,
     *                       populated here and consulted by {@link #renderTerminalSinks}
     * @return the node id of the last step rendered (or {@code headerNodeId} if there were none)
     */
    private String renderSteps(
            RenderState st,
            Segment seg,
            int segIdx,
            String headerNodeId,
            GraphQuery graph,
            Map<String, String> callerNodeIds) {
        List<GraphQuery.DataFlowNodeNode> topoNodes = graph.pathFlowNodes(GraphNodeId.of(seg.path.id.serialize()));
        if (!topoNodes.isEmpty()) {
            return renderStepsFromTopology(st, seg, segIdx, headerNodeId, graph, callerNodeIds, topoNodes);
        }
        // Fallback: flat steps list (paths without topology data).
        // Also deduplicate DFS back-tracking artifacts: DataFlowTracer records every visited
        // node including dead-end conditional branches, so the same component+method can
        // appear multiple times non-consecutively. Key = compId#method to allow different
        // methods on the same class (e.g. ingest vs processNonNullValue) to be distinct.
        String previousNodeInSeg = headerNodeId;
        String prevComponentKey = seg.path.steps.isEmpty() ? null : compKey(seg.path.steps.getFirst());
        Set<String> renderedStepKeys = new HashSet<>();
        if (!seg.path.steps.isEmpty()) {
            DataFlowStep first = seg.path.steps.getFirst();
            renderedStepKeys.add(compKey(first) + "#" + first.method);
            if (first.componentId != null) callerNodeIds.put(first.componentId.serialize(), headerNodeId);
        }
        for (int i = 1; i < seg.path.steps.size(); i++) {
            DataFlowStep step = seg.path.steps.get(i);
            String stepKey = compKey(step);
            if (stepKey != null && stepKey.equals(prevComponentKey)) continue;
            String dedupKey = stepKey + "#" + step.method;
            if (!renderedStepKeys.add(dedupKey)) continue;
            String nodeId = "S" + segIdx + "_" + i;
            GraphQuery.ComponentNode stepComp = null;
            if (step.componentId != null) {
                GraphQuery.GraphNode n = graph.component(step.componentId);
                if (n instanceof GraphQuery.ComponentNode cn) stepComp = cn;
            }
            ComponentType type = stepComp != null ? stepComp.type() : null;
            String label = step.componentName + "." + step.method;
            MermaidStyle.Role role = MermaidStyle.roleFor(type);
            st.nodes.add(MermaidDocument.Statement.node(st.tracker.node("    ", nodeId, label, role)));
            st.edges.add(MermaidDocument.Statement.edge(
                    MermaidDocument.Edge.labeled("    ", previousNodeInSeg, nodeId, escape(step.method), false)));
            previousNodeInSeg = nodeId;
            prevComponentKey = stepKey;
            if (step.componentId != null) callerNodeIds.put(step.componentId.serialize(), nodeId);
        }
        return previousNodeInSeg;
    }

    /**
     * Renders {@code seg}'s step chain from recorded topology nodes/edges rather than the flat
     * steps list, so conditional branches and merges are drawn as the graph actually recorded
     * them instead of flattened into a single line.
     *
     * <p>Each topology node is mapped to a Mermaid node id in {@code nodeIdMap}: {@code "root"}
     * nodes alias to {@code headerNodeId} (the segment already rendered its header separately),
     * {@code "method"} nodes get a fresh id ({@code "S<segIdx>_N<nodeOrder>"}) and are rendered
     * with a shape/style from {@link MermaidStyle#roleFor}, and {@code "sink"} (and any unknown)
     * node kinds are skipped here because {@link #renderTerminalSinks} renders sinks separately
     * from {@code seg.path.sinks}. Topology edges that target a sink node are likewise skipped for
     * the same reason; remaining edges are drawn dashed when their {@code edgeKind} property is
     * {@code "conditional"}, carrying the edge's {@code label} property when present.
     *
     * @param st             accumulator for nodes/edges/tracker state, mutated in place
     * @param seg            segment whose path is being rendered
     * @param segIdx         index of {@code seg}, used to build step node ids
     * @param headerNodeId   id of the segment's header node, aliased to the topology root node
     * @param graph          graph query interface for topology edges and component lookups
     * @param callerNodeIds  map from component id to the node id of the step that last called it,
     *                       populated here and consulted by {@link #renderTerminalSinks}
     * @param nodes          topology nodes for {@code seg.path}, as returned by {@code graph}
     * @return the node id of the last {@code "method"} node rendered (or {@code headerNodeId} if
     *     the topology had none)
     */
    private String renderStepsFromTopology(
            RenderState st,
            Segment seg,
            int segIdx,
            String headerNodeId,
            GraphQuery graph,
            Map<String, String> callerNodeIds,
            List<GraphQuery.DataFlowNodeNode> nodes) {
        List<GraphQuery.GraphEdge> edges = graph.pathFlowEdges(GraphNodeId.of(seg.path.id.serialize()));

        Map<GraphNodeId, String> nodeIdMap = new HashMap<>();
        String lastMethodNodeId = headerNodeId;

        for (GraphQuery.DataFlowNodeNode dn : nodes) {
            switch (dn.nodeKind()) {
                case "root" -> {
                    nodeIdMap.put(dn.id(), headerNodeId);
                    if (dn.componentId() != null)
                        callerNodeIds.put(dn.componentId().serialize(), headerNodeId);
                }
                case "method" -> {
                    String mermaidId = "S" + segIdx + "_N" + dn.nodeOrder();
                    nodeIdMap.put(dn.id(), mermaidId);
                    GraphQuery.ComponentNode compNode = null;
                    if (dn.componentId() != null) {
                        GraphQuery.GraphNode n = graph.component(dn.componentId());
                        if (n instanceof GraphQuery.ComponentNode cn) compNode = cn;
                    }
                    ComponentType type = compNode != null ? compNode.type() : null;
                    String label = (dn.componentName() != null ? dn.componentName() : "?")
                            + (dn.method() != null ? "." + dn.method() : "");
                    MermaidStyle.Role role = MermaidStyle.roleFor(type);
                    st.nodes.add(MermaidDocument.Statement.node(st.tracker.node("    ", mermaidId, label, role)));
                    if (dn.componentId() != null)
                        callerNodeIds.put(dn.componentId().serialize(), mermaidId);
                    lastMethodNodeId = mermaidId;
                }
                default -> {
                    // "sink" and unknown node kinds: skip — handled by renderTerminalSinks
                }
            }
        }

        Set<GraphNodeId> sinkNodeIds = nodes.stream()
                .filter(n -> "sink".equals(n.nodeKind()))
                .map(dn -> dn.id())
                .collect(Collectors.toSet());

        for (GraphQuery.GraphEdge edge : edges) {
            if (sinkNodeIds.contains(edge.toId())) continue;
            String fromMermaid = nodeIdMap.get(edge.fromId());
            String toMermaid = nodeIdMap.get(edge.toId());
            if (fromMermaid == null || toMermaid == null) continue;

            Object rawKind = edge.properties().get("edgeKind");
            String edgeKind = rawKind instanceof String s ? s : null;
            Object rawLabel = edge.properties().get("label");
            String edgeLabel = rawLabel instanceof String s ? s : null;
            boolean conditional = "conditional".equals(edgeKind);
            String labelStr = edgeLabel != null && !edgeLabel.isBlank() ? edgeLabel : "";

            st.edges.add(MermaidDocument.Statement.edge(
                    MermaidDocument.Edge.conditional("    ", fromMermaid, toMermaid, escape(labelStr), conditional)));
        }

        return lastMethodNodeId;
    }

    /**
     * Identity key for the component that made a step's call, preferring the stable component id
     * and falling back to the display name only when no id was resolved. Used by the flat-steps
     * fallback in {@link #renderSteps} to detect consecutive re-visits of the same component.
     *
     * @param step data-flow step to key
     * @return {@code step.componentId} serialized, or {@code step.componentName} if the id is null
     */
    private static String compKey(DataFlowStep step) {
        return step.componentId != null ? step.componentId.serialize() : step.componentName;
    }

    /**
     * Renders the sinks in {@code seg.path.sinks} that terminate here rather than link onward to
     * the next segment: the sink that {@link #renderSegment} identified as the next segment's
     * {@code incomingSink} is excluded, since it is drawn as a boundary node instead.
     *
     * <p>Remaining sinks are deduplicated by {@link #sinkDedupKey} (component + method + kind +
     * channel/topic) via a {@link LinkedHashMap}, keeping insertion order and the first sink seen
     * for each key, so a value re-observed at the same call site during DFS tracing is drawn only
     * once. Each surviving sink gets its own terminal node (shape/style from {@link #terminalRole})
     * wired from the step that actually called it — looked up in {@code callerNodeIds} by
     * {@code s.callerComponentId} — falling back to {@code previousNodeInSeg} when the caller is
     * unknown or wasn't recorded as a step node.
     *
     * @param st                accumulator for nodes/edges/tracker state, mutated in place
     * @param chain             full pipeline chain, used to identify the next segment's linking sink
     * @param seg               segment whose sinks are being rendered
     * @param segIdx            index of {@code seg}, used to build terminal node ids
     * @param previousNodeInSeg fallback caller node when a sink's caller isn't in {@code callerNodeIds}
     * @param callerNodeIds     map from component id to the node id of the step that called it,
     *                          as populated by {@link #renderSteps}/{@link #renderStepsFromTopology}
     */
    private void renderTerminalSinks(
            RenderState st,
            Chain chain,
            Segment seg,
            int segIdx,
            String previousNodeInSeg,
            Map<String, String> callerNodeIds) {
        // Deduplicate sinks by (componentName, method, kind, channel/topic) and group by caller
        // so each distinct sink appears once, wired from the step that actually called it.
        // Key = dedup key; value = first sink seen with that key.
        LinkedHashMap<String, DataFlowSink> unique = new LinkedHashMap<>();
        for (DataFlowSink s : seg.path.sinks) {
            boolean isLink = (segIdx + 1 < chain.segments.size()) && chain.segments.get(segIdx + 1).incomingSink == s;
            if (isLink) continue;
            String dedupKey = sinkDedupKey(s);
            unique.putIfAbsent(dedupKey, s);
        }
        int terminalCounter = 0;
        for (DataFlowSink s : unique.values()) {
            terminalCounter++;
            String termId = "T" + segIdx + "_" + terminalCounter;
            String termLabel =
                    (s.componentName != null ? s.componentName : "?") + (s.method != null ? "." + s.method : "");
            MermaidStyle.Role role = terminalRole(s.kind);
            st.nodes.add(MermaidDocument.Statement.node(st.tracker.node("    ", termId, termLabel, role)));
            // Wire from the step that called this sink, falling back to the segment's last node.
            String callerNode = s.callerComponentId != null
                    ? callerNodeIds.getOrDefault(s.callerComponentId.serialize(), previousNodeInSeg)
                    : previousNodeInSeg;
            st.edges.add(MermaidDocument.Statement.edge(MermaidDocument.Edge.labeled(
                    "    ", callerNode, termId, escape(s.method == null ? "" : s.method), false)));
        }
    }

    /**
     * Identity key for grouping equivalent terminal sinks in {@link #renderTerminalSinks}: sinks
     * with the same component, method, kind, and channel/topic are considered the same terminal
     * and rendered only once, even if the tracer recorded them from multiple call paths.
     *
     * <p>Falls back to {@code s.topic} when {@code s.channel} is null so messaging sinks recorded
     * only with a resolved topic (rather than a raw channel expression) still dedupe correctly.
     *
     * @param s sink to key
     * @return {@code "componentName|method|kind|channelOrTopic"}, with empty segments for null fields
     */
    private static String sinkDedupKey(DataFlowSink s) {
        return (s.componentName != null ? s.componentName : "")
                + "|" + (s.method != null ? s.method : "")
                + "|" + (s.kind != null ? s.kind.value() : "")
                + "|" + (s.channel != null ? s.channel : s.topic != null ? s.topic : "");
    }

    /**
     * Assembles the accumulated nodes and edges into final Mermaid flowchart text: all node
     * declarations first, then a blank line, then all edges, then a trailing blank line, wrapped
     * as a {@code flowchart TD} via {@link MermaidTemplateAdapters#flowchart}. Grouping nodes
     * before edges (rather than interleaving them in render order) keeps Mermaid's own layout
     * pass free to lay out edges independently of declaration order.
     *
     * @param st fully populated render state for the chain
     * @return complete Mermaid flowchart text
     */
    private String assemble(RenderState st) {
        List<MermaidDocument.Statement> statements = new ArrayList<>(st.nodes);
        statements.add(MermaidDocument.Statement.emptyLine());
        statements.addAll(st.edges);
        statements.add(MermaidDocument.Statement.emptyLine());
        return MermaidTemplateAdapters.flowchart("TD", statements, st.tracker);
    }

    /**
     * Chooses the shape/style role for a boundary node from the kind of sink that produced the
     * value crossing into the next segment. {@code STORE} and {@code PERSISTENCE} both render as
     * a cylinder ({@link MermaidStyle.Role#STORE}) since both represent data at rest between
     * segments; {@code MESSAGING} and {@code EVENT_BUS} get their own distinct shapes so the
     * diagram visually distinguishes synchronous handoffs from broker-mediated ones. Any other
     * kind falls back to {@code STORE} as a neutral boundary shape; in practice {@link Chain}
     * construction only ever assigns {@code STORE}, {@code PERSISTENCE}, {@code MESSAGING}, or
     * {@code EVENT_BUS} as an {@code incomingSink}, so the default case is unreachable in current
     * usage but kept for switch exhaustiveness.
     *
     * @param kind sink kind linking the previous segment to the next
     * @return shape/style role for the boundary node
     */
    private MermaidStyle.Role boundaryRole(DataFlowSink.Kind kind) {
        return switch (kind) {
            case STORE, PERSISTENCE -> MermaidStyle.Role.STORE;
            case MESSAGING -> MermaidStyle.Role.MESSAGING;
            case EVENT_BUS -> MermaidStyle.Role.EVENT;
            default -> MermaidStyle.Role.STORE;
        };
    }

    /**
     * Chooses the shape/style role for a terminal sink node, unlike {@link #boundaryRole} covering
     * every {@link DataFlowSink.Kind} since a terminal sink (unlike a boundary) can be any kind the
     * tracer records. {@code PERSISTENCE}, {@code OBJECT_STORAGE}, and {@code STORE} all render as
     * the same cylinder shape since each represents data coming to rest; {@code HTTP_OUTBOUND} and
     * {@code MESSAGING}/{@code EVENT_BUS} get shapes distinguishing outbound HTTP calls from
     * broker-mediated handoffs; {@code FILE_OUTBOUND} renders as a plain component box since no
     * dedicated file-sink shape exists. Any remaining kind (only {@code UNKNOWN}) falls back to
     * {@code STORE}.
     *
     * @param kind terminal sink's kind
     * @return shape/style role for the terminal node
     */
    private MermaidStyle.Role terminalRole(DataFlowSink.Kind kind) {
        return switch (kind) {
            case PERSISTENCE, OBJECT_STORAGE, STORE -> MermaidStyle.Role.STORE;
            case HTTP_OUTBOUND -> MermaidStyle.Role.HTTP_CLIENT;
            case MESSAGING -> MermaidStyle.Role.MESSAGING;
            case EVENT_BUS -> MermaidStyle.Role.EVENT;
            case FILE_OUTBOUND -> MermaidStyle.Role.COMPONENT;
            default -> MermaidStyle.Role.STORE;
        };
    }

    /**
     * Builds the label text shown on a boundary node, using whichever field of {@code sink}
     * best names the crossing for that sink's kind: {@code STORE} sinks delegate to
     * {@link #storeBoundaryLabel} for an {@code Owner.field} label since a shared field is only
     * meaningful together with its owning component; {@code MESSAGING}/{@code EVENT_BUS} sinks use
     * the channel name (falling back to the literal {@code "channel"} when unresolved) since that
     * is what identifies the crossing to a reader; {@code PERSISTENCE} sinks prefer the entity type
     * over the component name since the entity is what's actually being handed off; any other kind
     * falls back to the sink's component name, or its kind's wire value if even that is unknown.
     *
     * @param sink  sink whose crossing is being labeled
     * @param graph graph query interface, used by {@link #storeBoundaryLabel} to resolve the
     *              field-owning component
     * @return raw label text for the boundary node; {@link MermaidStyle.Tracker#node} escapes it
     *     when the node is emitted, so this method does not escape its own return value
     */
    private String boundaryLabel(DataFlowSink sink, GraphQuery graph) {
        return switch (sink.kind) {
            case STORE -> storeBoundaryLabel(sink, graph);
            case MESSAGING, EVENT_BUS -> sink.channel != null ? sink.channel : "channel";
            case PERSISTENCE -> sink.entityType != null ? sink.entityType : nonNullComponentName(sink);
            default -> sink.componentName != null ? sink.componentName : sink.kind.value();
        };
    }

    /**
     * Builds an {@code Owner.field} label for a {@code STORE} boundary, resolving the owning
     * component by {@code sink.fieldOwnerComponentId} via {@code graph} rather than
     * {@code sink.componentName}, since the field owner (where the value is actually held) can
     * differ from the component that wrote to it. Falls back to {@link #nonNullComponentName} for
     * the owner name and to {@code "?"} for the field name when either is unresolved.
     *
     * @param sink  {@code STORE}-kind sink describing the shared field
     * @param graph graph query interface used to resolve the field-owning component
     * @return {@code "OwnerName.fieldName"} label text
     */
    private String storeBoundaryLabel(DataFlowSink sink, GraphQuery graph) {
        GraphQuery.ComponentNode owner = null;
        if (sink.fieldOwnerComponentId != null) {
            GraphQuery.GraphNode n = graph.component(sink.fieldOwnerComponentId);
            if (n instanceof GraphQuery.ComponentNode cn) owner = cn;
        }
        String ownerName = owner != null ? owner.name() : nonNullComponentName(sink);
        return ownerName + "." + (sink.fieldName != null ? sink.fieldName : "?");
    }

    /**
     * Null-safe accessor for a sink's component display name, used wherever a label needs a
     * guaranteed non-null placeholder instead of propagating null into string concatenation.
     *
     * @param sink sink to read the component name from
     * @return {@code sink.componentName}, or {@code "?"} when it is null
     */
    private static String nonNullComponentName(DataFlowSink sink) {
        return sink.componentName != null ? sink.componentName : "?";
    }

    /**
     * Escapes text for safe inclusion inside a Mermaid node/edge label, delegating to
     * {@link Mermaid#escapeLabel}.
     *
     * @param s raw label text
     * @return Mermaid-safe escaped text
     */
    private String escape(String s) {
        return Mermaid.escapeLabel(s);
    }
}
