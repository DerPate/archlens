package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.OutboundSinkSite;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.MethodRef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Adjacency index for outbound sink sites, keyed by the producing component method. */
public final class OutboundSinkIndex {

    private final Map<MethodRef, List<OutboundSinkSite>> index;
    private final List<OutboundSinkSite> all;

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
        return new OutboundSinkIndex(index, List.copyOf(sites));
    }

    private OutboundSinkIndex(Map<MethodRef, List<OutboundSinkSite>> index, List<OutboundSinkSite> all) {
        this.index = index;
        this.all = all;
    }

    /**
     * Returns every indexed sink site, in insertion order.
     *
     * @return all sink sites
     */
    public List<OutboundSinkSite> all() {
        return all;
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
