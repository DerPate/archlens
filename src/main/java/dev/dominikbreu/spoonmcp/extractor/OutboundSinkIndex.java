package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.OutboundSinkSite;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OutboundSinkIndex {

    private final Map<String, List<OutboundSinkSite>> index;

    public static OutboundSinkIndex build(Collection<OutboundSinkSite> sites) {
        Map<String, List<OutboundSinkSite>> index = new HashMap<>();
        for (OutboundSinkSite site : sites) {
            index.computeIfAbsent(key(site.componentId, site.method), k -> new ArrayList<>())
                    .add(site);
        }
        return new OutboundSinkIndex(index);
    }

    private OutboundSinkIndex(Map<String, List<OutboundSinkSite>> index) {
        this.index = index;
    }

    public List<OutboundSinkSite> sites(String componentId, String method) {
        return index.getOrDefault(key(componentId, method), List.of());
    }

    private static String key(String componentId, String method) {
        return componentId + "#" + method;
    }
}
