package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.OutboundSinkSite;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.MethodRef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Adjacency index for outbound sink sites, keyed by the producing component method. */
public final class OutboundSinkIndex {

    private final Map<MethodRef, List<OutboundSinkSite>> index;

    /**
     * Builds an outbound sink index from a collection of sink sites.
     *
     * @param sites the sink sites to index
     * @return the populated index
     */
    public static OutboundSinkIndex build(Collection<OutboundSinkSite> sites) {
        Map<MethodRef, List<OutboundSinkSite>> index = new HashMap<>();
        for (OutboundSinkSite site : sites) {
            index.computeIfAbsent(new MethodRef(site.componentId, site.method), k -> new ArrayList<>())
                    .add(site);
        }
        return new OutboundSinkIndex(index);
    }

    private OutboundSinkIndex(Map<MethodRef, List<OutboundSinkSite>> index) {
        this.index = index;
    }

    /**
     * Returns all outbound sink sites produced by the given component method.
     *
     * @param componentId the producing component
     * @param method the producing method name
     * @return the sink sites, or an empty list if none
     */
    public List<OutboundSinkSite> sites(ComponentId componentId, String method) {
        return index.getOrDefault(new MethodRef(componentId, method), List.of());
    }
}
