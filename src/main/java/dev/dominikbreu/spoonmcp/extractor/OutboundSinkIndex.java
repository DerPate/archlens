package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.OutboundSinkSite;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.MethodRef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OutboundSinkIndex {

    private final Map<MethodRef, List<OutboundSinkSite>> index;

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

    public List<OutboundSinkSite> sites(ComponentId componentId, String method) {
        return index.getOrDefault(new MethodRef(componentId, method), List.of());
    }
}
