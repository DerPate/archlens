package dev.dominikbreu.spoonmcp.model;

import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import dev.dominikbreu.spoonmcp.model.ids.UseCaseId;
import java.util.ArrayList;
import java.util.List;

/**
 * A named business operation derived from a runtime entrypoint and its call chain.
 */
public class UseCase {
    /** Stable identifier: the bare originating entrypoint id. */
    public UseCaseId id;
    /** Human-readable name, either auto-derived or supplied via naming config. */
    public String name;
    /** Originating entrypoint identifier. */
    public EntrypointId entrypointId;
    /** Entrypoint trigger type. */
    public EntrypointType type;
    /** Channel name (messaging) or HTTP path (REST), or null when not applicable. */
    public String channelOrPath;
    /** Ordered component IDs visited during this use case's call chain. */
    public List<ComponentId> componentIds = new ArrayList<>();
    /**
     * Human-readable call chain steps, each formatted as
     * {@code ComponentA.methodX → ComponentB.methodY}.
     * Empty when no call-graph data is available (falls back to injection edges).
     */
    public List<String> methodChain = new ArrayList<>();

    /** Creates an empty use case for JSON deserialization. */
    public UseCase() {}
}
