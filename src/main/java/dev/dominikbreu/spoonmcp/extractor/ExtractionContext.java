package dev.dominikbreu.spoonmcp.extractor;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;
import spoon.reflect.declaration.CtType;

/** Mutable context passed through a single extraction pass to share indices and deduplication state. */
public final class ExtractionContext {

    /** The component index for the current extraction pass. */
    public final ComponentIndex components;

    private final IdentityHashMap<CtType<?>, Set<String>> sharedStateCache = new IdentityHashMap<>();
    private final Set<String> seenIds = new HashSet<>();

    /**
     * Creates an extraction context with the given component index.
     *
     * @param components the component index for this pass
     */
    public ExtractionContext(ComponentIndex components) {
        this.components = components;
    }

    /**
     * Returns the shared-state field set for the given type, computing it on first access.
     *
     * @param type the Spoon type element
     * @param builder function to compute the field set if not yet cached
     * @return the cached or freshly computed field set
     */
    public Set<String> sharedStateFieldsFor(CtType<?> type, Function<CtType<?>, Set<String>> builder) {
        return sharedStateCache.computeIfAbsent(type, builder);
    }

    /**
     * Records an id as seen and returns whether it was new.
     *
     * @param id the id to record
     * @return true if the id was not previously seen; false if it was a duplicate
     */
    public boolean addSeenId(String id) {
        return seenIds.add(id);
    }
}
