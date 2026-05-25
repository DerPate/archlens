package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.workflow.WorkflowGraph;
import dev.dominikbreu.spoonmcp.workflow.WorkflowGraphBuilder;
import dev.dominikbreu.spoonmcp.workflow.WorkflowLink;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Builds ordered pipeline chains by traversing {@link DataFlowSink#linkedPathIds} forward
 * from root paths (paths with no incoming linker pointer).
 *
 * <p>A chain is a sequence of {@link Segment}s, where each segment carries the resolved
 * {@link DataFlowPath}, the {@link DataFlowSink} that linked the previous segment to this
 * one (null for the root), and the resolved {@link Entrypoint}.
 */
public class PipelineGraphBuilder {

    /** Creates a builder. */
    public PipelineGraphBuilder() {}

    /** One pipeline phase: a path plus the sink that bridged the previous phase. */
    public static final class Segment {
        /** The data-flow path for this phase. */
        public final DataFlowPath path;
        /** Sink in the previous segment that linked to this segment, or null for the root. */
        public final DataFlowSink incomingSink;
        /** Typed workflow link from the previous segment, or null for the root. */
        public final WorkflowLink incomingLink;
        /** Entrypoint that owns this phase's path. */
        public final Entrypoint entrypoint;

        /**
         * Creates a segment.
         *
         * @param path          data-flow path for this phase
         * @param incomingSink  sink from the previous segment, or null for the root
         * @param entrypoint    entrypoint that owns this path
         */
        public Segment(DataFlowPath path, DataFlowSink incomingSink, Entrypoint entrypoint) {
            this(path, incomingSink, null, entrypoint);
        }

        /**
         * Creates a segment with typed workflow-link metadata.
         *
         * @param path          data-flow path for this phase
         * @param incomingSink  sink from the previous segment, or null for the root
         * @param incomingLink  typed workflow link from the previous segment, or null for the root
         * @param entrypoint    entrypoint that owns this path
         */
        public Segment(DataFlowPath path, DataFlowSink incomingSink, WorkflowLink incomingLink, Entrypoint entrypoint) {
            this.path = path;
            this.incomingSink = incomingSink;
            this.incomingLink = incomingLink;
            this.entrypoint = entrypoint;
        }
    }

    /** Ordered list of segments forming a single pipeline. */
    public static final class Chain {
        /** Segments in traversal order; first element is always the root. */
        public final List<Segment> segments = new ArrayList<>();

        /** Creates an empty chain. */
        public Chain() {}
    }

    /**
     * Builds all chains reachable in the model. Each root path yields one or more chains
     * (one per fan-out branch). Cycles are broken when a path repeats in the current path stack.
     *
     * @param model     architecture model whose {@code dataFlowPaths} carry {@code linkedPathIds}
     * @param maxDepth  hard cap on segments per chain
     * @return chains in deterministic insertion order
     */
    public List<Chain> build(ArchitectureModel model, int maxDepth) {
        if (model == null || model.dataFlowPaths == null || model.dataFlowPaths.isEmpty()) {
            return List.of();
        }
        Tracer t = tracer();
        Span buildSpan = t.spanBuilder("pipeline.build").startSpan();
        try (Scope buildScope = buildSpan.makeCurrent()) {

            WorkflowGraph workflowGraph;
            Span wfSpan = t.spanBuilder("pipeline.workflow-graph").startSpan();
            try (Scope scopeWf = wfSpan.makeCurrent()) {
                workflowGraph = new WorkflowGraphBuilder().build(model);
                wfSpan.setAttribute("roots", (long) workflowGraph.rootPaths().size());
                wfSpan.setAttribute("links", (long) workflowGraph.totalLinks());
            } catch (RuntimeException e) {
                wfSpan.recordException(e);
                wfSpan.setStatus(StatusCode.ERROR, e.getMessage());
                throw e;
            } finally {
                wfSpan.end();
            }

            List<Chain> chains = new ArrayList<>();
            Span dfsSpan = t.spanBuilder("pipeline.dfs").startSpan();
            try (Scope scopeDfs = dfsSpan.makeCurrent()) {
                for (DataFlowPath p : workflowGraph.rootPaths()) {
                    extend(
                            new ArrayList<>(),
                            p,
                            null,
                            null,
                            workflowGraph,
                            chains,
                            maxDepth,
                            new LinkedHashSet<>(),
                            new LinkedHashSet<>());
                }
                dfsSpan.setAttribute("raw-chains", (long) chains.size());
            } catch (RuntimeException e) {
                dfsSpan.recordException(e);
                dfsSpan.setStatus(StatusCode.ERROR, e.getMessage());
                throw e;
            } finally {
                dfsSpan.end();
            }

            List<Chain> result;
            Span dedupSpan = t.spanBuilder("pipeline.dedup").startSpan();
            try (Scope scopeDedup = dedupSpan.makeCurrent()) {
                result = removeDuplicateChains(removePrefixChains(chains));
                dedupSpan.setAttribute("final-chains", (long) result.size());
            } catch (RuntimeException e) {
                dedupSpan.recordException(e);
                dedupSpan.setStatus(StatusCode.ERROR, e.getMessage());
                throw e;
            } finally {
                dedupSpan.end();
            }

            return result;
        } catch (RuntimeException e) {
            buildSpan.recordException(e);
            buildSpan.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            buildSpan.end();
        }
    }

    private static Tracer tracer() {
        return GlobalOpenTelemetry.getTracer("dev.dominikbreu.spoonmcp");
    }

    /**
     * Suppresses early-terminating fan-out branches. When a path fans out to branch A (terminal)
     * and branch B→C (deeper), the builder emits both (root,...,A) and (root,...,B,C). The
     * (root,...,A) chain is removed because its trunk (all segments except its own terminal) is
     * a leading subsequence of the longer chain, making it a redundant truncation.
     *
     * <p>Uses {@code prefixLen = size - 1} rather than {@code size}: at the fan-out point, branch
     * A and branch B diverge by definition, so a full-length prefix match would never fire. Only
     * the shared trunk (everything before the terminal) needs to match.
     */
    private List<Chain> removePrefixChains(List<Chain> chains) {
        int n = chains.size();
        List<List<String>> allIds = new ArrayList<>(n);
        for (Chain c : chains) allIds.add(segmentPathIds(c));

        List<Chain> result = new ArrayList<>(n);
        outer:
        for (int i = 0; i < n; i++) {
            List<String> idsI = allIds.get(i);
            int prefixLen = idsI.size() - 1;
            if (prefixLen == 0) {
                result.add(chains.get(i));
                continue;
            }
            for (int j = 0; j < n; j++) {
                if (j == i) continue;
                List<String> idsJ = allIds.get(j);
                if (idsJ.size() <= idsI.size()) continue;
                boolean isPrefix = true;
                for (int k = 0; k < prefixLen; k++) {
                    if (!idsI.get(k).equals(idsJ.get(k))) {
                        isPrefix = false;
                        break;
                    }
                }
                if (isPrefix) continue outer;
            }
            result.add(chains.get(i));
        }
        return result;
    }

    private List<Chain> removeDuplicateChains(List<Chain> chains) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Chain> result = new ArrayList<>();
        for (Chain c : chains) {
            if (seen.add(chainKey(c))) {
                result.add(c);
            }
        }
        return result;
    }

    private String chainKey(Chain c) {
        StringBuilder sb = new StringBuilder();
        for (Segment s : c.segments) {
            if (sb.length() > 0) sb.append('|');
            String epId = s.entrypoint != null ? s.entrypoint.id : (s.path != null ? s.path.entrypointId : "");
            sb.append(epId != null ? epId : "");
        }
        return sb.toString();
    }

    private List<String> segmentPathIds(Chain c) {
        List<String> ids = new ArrayList<>(c.segments.size());
        for (Segment s : c.segments) ids.add(s.path.id);
        return ids;
    }

    private void extend(
            List<Segment> prefix,
            DataFlowPath current,
            DataFlowSink incomingSink,
            WorkflowLink incomingLink,
            WorkflowGraph workflowGraph,
            List<Chain> out,
            int maxDepth,
            LinkedHashSet<String> stack,
            LinkedHashSet<String> epStack) {
        if (current == null) return;
        if (stack.contains(current.id)) {
            // path-level cycle — emit chain up to here
            emit(prefix, out);
            return;
        }
        // Entrypoint-level cycle: same entrypoint appearing twice means a STORE loop
        // (e.g. defaultStateCalculation → stateCache → defaultStateCalculation).
        String epId = current.entrypointId;
        if (epId != null && !prefix.isEmpty() && epStack.contains(epId)) {
            emit(prefix, out);
            return;
        }
        Segment seg = new Segment(
                current,
                incomingSink,
                incomingLink,
                workflowGraph.entrypointById().get(epId));
        List<Segment> nextPrefix = new ArrayList<>(prefix);
        nextPrefix.add(seg);

        if (nextPrefix.size() >= maxDepth) {
            emit(nextPrefix, out);
            return;
        }

        boolean fannedOut = false;
        LinkedHashSet<String> nextStack = new LinkedHashSet<>(stack);
        nextStack.add(current.id);
        LinkedHashSet<String> nextEpStack = new LinkedHashSet<>(epStack);
        if (epId != null) nextEpStack.add(epId);
        for (WorkflowLink link : workflowGraph.linksFrom(current.id)) {
            DataFlowPath next = workflowGraph.pathById().get(link.toPathId());
            if (next == null) continue;
            fannedOut = true;
            extend(
                    nextPrefix,
                    next,
                    incomingSink(current, link),
                    link,
                    workflowGraph,
                    out,
                    maxDepth,
                    nextStack,
                    nextEpStack);
        }
        if (!fannedOut) emit(nextPrefix, out);
    }

    private DataFlowSink incomingSink(DataFlowPath current, WorkflowLink link) {
        for (DataFlowSink sink : current.sinks) {
            if (sink.linkedPathIds == null || !sink.linkedPathIds.contains(link.toPathId())) continue;
            if (link.kind() == WorkflowLink.Kind.MESSAGING && sink.kind == DataFlowSink.Kind.MESSAGING) return sink;
            if (link.kind() == WorkflowLink.Kind.EVENT_BUS && sink.kind == DataFlowSink.Kind.EVENT_BUS) return sink;
            if (link.kind() == WorkflowLink.Kind.STATE_HANDOFF && sink.kind == DataFlowSink.Kind.STORE) return sink;
            if (link.kind() == WorkflowLink.Kind.PERSISTENCE_HANDOFF && sink.kind == DataFlowSink.Kind.PERSISTENCE)
                return sink;
        }
        return null;
    }

    private void emit(List<Segment> segments, List<Chain> out) {
        if (segments.size() < 2) return; // a chain of one isn't a pipeline
        Chain c = new Chain();
        c.segments.addAll(segments);
        out.add(c);
    }
}
