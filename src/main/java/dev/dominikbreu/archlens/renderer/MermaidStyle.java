package dev.dominikbreu.archlens.renderer;

import dev.dominikbreu.archlens.model.ComponentType;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Shared Mermaid visual vocabulary: theme header, semantic role palette and shapes,
 * and node-id sanitizing. All Mermaid renderers delegate their visual decisions here
 * so diagrams share one coherent look.
 */
final class MermaidStyle {

    /** Dependency kinds rendered as dashed (asynchronous) edges. */
    private static final Set<String> ASYNC_KINDS =
            Set.of("messaging", "jms", "kafka", "event", "cdi-event", "event-bus", "amqp");

    /** Semantic role of a diagram node; maps to one shape and one classDef. */
    enum Role {
        /** External caller initiating a flow. */
        CLIENT("client", "([", "])", "fill:#eceff1,stroke:#78909c,color:#263238"),
        /** REST resource or other inbound endpoint. */
        ENTRYPOINT("entrypoint", "([", "])", "fill:#bbdefb,stroke:#1e6bb8,color:#0d2a47"),
        /** Business service (incl. EJBs and remote services). */
        SERVICE("service", "(", ")", "fill:#b2dfdb,stroke:#00796b,color:#00352f"),
        /** Persistence repository / DAO. */
        REPOSITORY("repository", "[(", ")]", "fill:#c8e6c9,stroke:#2e7d32,color:#12300f"),
        /** Persistence entity / domain object. */
        ENTITY("entity", "[(", ")]", "fill:#dcedc8,stroke:#558b2f,color:#243c10"),
        /** Datastore or stateful store boundary. */
        STORE("store", "[(", ")]", "fill:#a5d6a7,stroke:#1b5e20,color:#0b3d12"),
        /** Message broker, channel, or message-driven consumer. */
        MESSAGING("messaging", "([", "])", "fill:#ffe0b2,stroke:#e65100,color:#4a2300"),
        /** In-process event producer/consumer or event bus. */
        EVENT("event", "((", "))", "fill:#e1bee7,stroke:#6a1b9a,color:#320d47"),
        /** Timer or scheduled job. */
        SCHEDULER("scheduler", "([", "])", "fill:#ffecb3,stroke:#ff8f00,color:#4d2c00"),
        /** Outbound HTTP/REST client. */
        HTTP_CLIENT("httpclient", "[/", "/]", "fill:#ffcdd2,stroke:#c62828,color:#4e1010"),
        /** External system outside the analyzed workspace. */
        EXTERNAL("external", "[/", "\\]", "fill:#cfd8dc,stroke:#546e7a,color:#1c262b,stroke-dasharray:4 3"),
        /** Logical container / layer grouping. */
        CONTAINER("container", "[", "]", "fill:#e8eaf6,stroke:#3949ab,color:#1a2038"),
        /** Neutral component with no more specific role. */
        COMPONENT("component", "[", "]", "fill:#f5f5f5,stroke:#9e9e9e,color:#212121");

        private final String css;
        private final String open;
        private final String close;
        private final String style;

        Role(String css, String open, String close, String style) {
            this.css = css;
            this.open = open;
            this.close = close;
            this.style = style;
        }

        /** CSS class name used in classDef/class statements. */
        String css() {
            return css;
        }
    }

    private MermaidStyle() {}

    /**
     * Theme init directive emitted as the first line of every diagram. Mid-tone fills
     * with dark strokes/text stay readable on both light and dark backgrounds.
     *
     * @return single-line {@code %%{init: ...}%%} directive terminated by a newline
     */
    static String header() {
        return "%%{init: {\"theme\": \"base\", \"themeVariables\": {"
                + "\"primaryColor\": \"#f5f5f5\", \"primaryBorderColor\": \"#9e9e9e\", "
                + "\"primaryTextColor\": \"#212121\", \"lineColor\": \"#607d8b\", "
                + "\"clusterBkg\": \"#fafafa\", \"clusterBorder\": \"#b0bec5\"}}}%%\n";
    }

    /**
     * Renders one shaped node line.
     *
     * @param indent leading whitespace
     * @param id sanitized node id
     * @param label raw label text (escaped here)
     * @param role semantic role deciding the shape
     * @return complete node line terminated by a newline
     */
    static String node(String indent, String id, String label, Role role) {
        return indent + id + role.open + "\"" + Mermaid.escapeLabel(label) + "\"" + role.close + "\n";
    }

    /**
     * Emits classDef lines for the given roles in enum order.
     *
     * @param used roles present in the diagram
     * @return classDef block, empty string when no roles are used
     */
    static String classDefs(Collection<Role> used) {
        if (used.isEmpty()) return "";
        EnumSet<Role> ordered = EnumSet.noneOf(Role.class);
        ordered.addAll(used);
        StringBuilder sb = new StringBuilder();
        for (Role r : ordered) {
            sb.append("    classDef ").append(r.css).append(' ').append(r.style).append('\n');
        }
        return sb.toString();
    }

    /**
     * Binds a node id to a role's class.
     *
     * @param nodeId sanitized node id
     * @param role role whose class to assign
     * @return one {@code class} line terminated by a newline
     */
    static String assign(String nodeId, Role role) {
        return "    class " + nodeId + " " + role.css + "\n";
    }

    /**
     * Sanitizes an arbitrary id into a Mermaid-safe identifier.
     *
     * @param id raw id (may be null)
     * @return identifier containing only {@code [A-Za-z0-9_]}
     */
    static String nid(String id) {
        return id == null ? "_" : id.replaceAll("[^A-Za-z0-9_]", "_");
    }

    /**
     * Maps a component's architectural type to its diagram role.
     *
     * @param type component type (may be null)
     * @return matching role, {@link Role#COMPONENT} as fallback
     */
    static Role roleFor(ComponentType type) {
        if (type == null) return Role.COMPONENT;
        return switch (type) {
            case REST_RESOURCE -> Role.ENTRYPOINT;
            case SERVICE, EJB_STATELESS, EJB_STATEFUL, EJB_SINGLETON, REMOTE_SERVICE -> Role.SERVICE;
            case REPOSITORY -> Role.REPOSITORY;
            case ENTITY -> Role.ENTITY;
            case MESSAGE_DRIVEN_BEAN -> Role.MESSAGING;
            case SCHEDULER -> Role.SCHEDULER;
            case HTTP_CLIENT -> Role.HTTP_CLIENT;
            case CDI_EVENT_CONSUMER, CDI_EVENT_PRODUCER -> Role.EVENT;
            default -> Role.COMPONENT;
        };
    }

    /**
     * Maps an external-system kind string to its diagram role.
     *
     * @param kind external system kind (may be null)
     * @return matching role, {@link Role#EXTERNAL} as fallback
     */
    static Role roleForExternalKind(String kind) {
        if (kind == null) return Role.EXTERNAL;
        return switch (kind.toUpperCase(Locale.ROOT)) {
            case "MESSAGE_BROKER" -> Role.MESSAGING;
            case "DATABASE", "DATASTORE" -> Role.STORE;
            default -> Role.EXTERNAL;
        };
    }

    /**
     * Whether a dependency kind represents asynchronous communication (dashed edge).
     *
     * @param kind dependency kind (may be null)
     * @return true for messaging/event kinds
     */
    static boolean isAsyncKind(String kind) {
        return kind != null && ASYNC_KINDS.contains(kind.toLowerCase(Locale.ROOT));
    }

    /** Accumulates role usage and class assignments while a renderer emits nodes. */
    static final class Tracker {
        private final EnumSet<Role> used = EnumSet.noneOf(Role.class);
        private final StringBuilder assigns = new StringBuilder();

        /** Creates an empty tracker. */
        Tracker() {}

        /**
         * Records that a node was rendered with the given role.
         *
         * @param nodeId sanitized node id
         * @param role role assigned to the node
         */
        void tag(String nodeId, Role role) {
            used.add(role);
            assigns.append(assign(nodeId, role));
        }

        /**
         * Styling footer to append after all nodes and edges.
         *
         * @return classDefs and class assignments; empty when nothing tagged
         */
        String footer() {
            if (used.isEmpty()) return "";
            return classDefs(used) + assigns;
        }
    }
}
