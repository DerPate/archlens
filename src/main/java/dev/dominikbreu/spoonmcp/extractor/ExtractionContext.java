package dev.dominikbreu.spoonmcp.extractor;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;
import spoon.reflect.declaration.CtType;

public final class ExtractionContext {

    public final ComponentIndex components;
    private final IdentityHashMap<CtType<?>, Set<String>> sharedStateCache = new IdentityHashMap<>();
    private final Set<String> seenIds = new HashSet<>();

    public ExtractionContext(ComponentIndex components) {
        this.components = components;
    }

    public Set<String> sharedStateFieldsFor(CtType<?> type, Function<CtType<?>, Set<String>> builder) {
        return sharedStateCache.computeIfAbsent(type, builder);
    }

    /** Returns true if id was not already seen; false if it was (duplicate). */
    public boolean addSeenId(String id) {
        return seenIds.add(id);
    }
}
