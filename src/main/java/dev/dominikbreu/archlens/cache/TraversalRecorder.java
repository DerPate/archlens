package dev.dominikbreu.archlens.cache;

import java.util.ArrayList;
import java.util.List;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;

/**
 * Thread-local capture of Gremlin traversal step-chains, used by the standalone dashboard's
 * REPL to show what a tool call actually executed. Inactive by default — a single null check
 * per call site — so the real MCP path pays no cost.
 */
public final class TraversalRecorder {

    private static final ThreadLocal<List<String>> BUFFER = new ThreadLocal<>();

    private TraversalRecorder() {}

    /** Starts capturing on the current thread, replacing any prior buffer. */
    public static void enable() {
        BUFFER.set(new ArrayList<>());
    }

    /** Stops capturing on the current thread and discards any buffered traces. */
    public static void disable() {
        BUFFER.remove();
    }

    /** True when the current thread is capturing. */
    public static boolean isActive() {
        return BUFFER.get() != null;
    }

    /** Records the given traversal's step-chain text. No-op when not capturing. */
    public static void capture(Traversal<?, ?> traversal) {
        List<String> buffer = BUFFER.get();
        if (buffer != null) {
            buffer.add(traversal.toString());
        }
    }

    /** Returns the traces captured since the last {@link #enable()}, without clearing them. */
    public static List<String> captured() {
        List<String> buffer = BUFFER.get();
        return buffer == null ? List.of() : List.copyOf(buffer);
    }
}
