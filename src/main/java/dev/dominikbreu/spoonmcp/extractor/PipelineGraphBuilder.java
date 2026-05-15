package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            this.path = path;
            this.incomingSink = incomingSink;
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
        Map<String, DataFlowPath> pathById = new HashMap<>();
        for (DataFlowPath p : model.dataFlowPaths) pathById.put(p.id, p);

        Map<String, Entrypoint> epById = new HashMap<>();
        for (Entrypoint e : model.entrypoints) epById.put(e.id, e);

        Set<String> hasIncoming = new HashSet<>();
        for (DataFlowPath p : model.dataFlowPaths) {
            for (DataFlowSink s : p.sinks) {
                if (s.linkedPathIds == null) continue;
                hasIncoming.addAll(s.linkedPathIds);
            }
        }

        List<Chain> chains = new ArrayList<>();
        for (DataFlowPath p : model.dataFlowPaths) {
            if (hasIncoming.contains(p.id)) continue;
            // Skip orphan roots: must have at least one outgoing link to be a pipeline.
            if (!hasAnyLink(p)) continue;
            extend(new ArrayList<>(), p, null, pathById, epById, chains, maxDepth, new LinkedHashSet<>());
        }
        return removePrefixChains(chains);
    }

    /**
     * Removes chains that terminate early at a fan-out node.
     *
     * <p>Chain ci is suppressed when there exists a strictly longer chain cj that shares the same
     * leading path IDs up to (but not including) ci's terminal segment. This covers the case where
     * one fan-out branch ends immediately while a sibling branch continues deeper: the short branch
     * is noise because the fan-out node itself already appears in the longer chain.
     */
    private List<Chain> removePrefixChains(List<Chain> chains) {
        List<Chain> result = new ArrayList<>(chains.size());
        outer:
        for (Chain ci : chains) {
            List<String> idsI = segmentPathIds(ci);
            // Compare only the prefix up to (not including) the terminal segment of ci.
            int prefixLen = idsI.size() - 1;
            if (prefixLen == 0) {
                result.add(ci);
                continue;
            }
            for (Chain cj : chains) {
                if (cj == ci) continue;
                if (cj.segments.size() <= ci.segments.size()) continue;
                List<String> idsJ = segmentPathIds(cj);
                boolean isPrefix = true;
                for (int k = 0; k < prefixLen; k++) {
                    if (!idsI.get(k).equals(idsJ.get(k))) {
                        isPrefix = false;
                        break;
                    }
                }
                if (isPrefix) continue outer;
            }
            result.add(ci);
        }
        return result;
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
            Map<String, DataFlowPath> pathById,
            Map<String, Entrypoint> epById,
            List<Chain> out,
            int maxDepth,
            LinkedHashSet<String> stack) {
        if (current == null) return;
        if (stack.contains(current.id)) {
            // cycle — emit chain up to here
            emit(prefix, out);
            return;
        }
        Segment seg = new Segment(current, incomingSink, epById.get(current.entrypointId));
        List<Segment> nextPrefix = new ArrayList<>(prefix);
        nextPrefix.add(seg);

        if (nextPrefix.size() >= maxDepth) {
            emit(nextPrefix, out);
            return;
        }

        boolean fannedOut = false;
        LinkedHashSet<String> nextStack = new LinkedHashSet<>(stack);
        nextStack.add(current.id);
        for (DataFlowSink s : current.sinks) {
            if (s.linkedPathIds == null || s.linkedPathIds.isEmpty()) continue;
            for (String nextId : s.linkedPathIds) {
                DataFlowPath next = pathById.get(nextId);
                if (next == null) continue;
                fannedOut = true;
                extend(nextPrefix, next, s, pathById, epById, out, maxDepth, nextStack);
            }
        }
        if (!fannedOut) emit(nextPrefix, out);
    }

    private void emit(List<Segment> segments, List<Chain> out) {
        if (segments.size() < 2) return; // a chain of one isn't a pipeline
        Chain c = new Chain();
        c.segments.addAll(segments);
        out.add(c);
    }

    private boolean hasAnyLink(DataFlowPath p) {
        for (DataFlowSink s : p.sinks) {
            if (s.linkedPathIds != null && !s.linkedPathIds.isEmpty()) return true;
        }
        return false;
    }
}
