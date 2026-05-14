package dev.dominikbreu.spoonmcp.cache;

import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import java.util.Locale;
import java.util.Objects;

/**
 * Scores graph components for workflow relevance without letting utility fan-in dominate.
 */
final class ArchitectureRelevanceScorer {

    private ArchitectureRelevanceScorer() {}

    static Relevance score(Component component, Metrics metrics) {
        int noiseScore = noiseScore(component);
        String role = infrastructureRole(component, noiseScore);
        int workflowBridgeScore = workflowBridgeScore(component, metrics);
        boolean businessRelevant = businessRelevant(component, noiseScore);
        boolean workflowRelevant = businessRelevant
                && (metrics.ownedEntrypointCount() > 0
                        || metrics.fanOut() > 0
                        || workflowBridgeScore > 0
                        || isEntrypointComponent(component));

        int structuralWeight = Math.min(metrics.fanIn(), 3) + metrics.fanOut() + (metrics.ownedEntrypointCount() * 4);
        int roleBonus = (workflowBridgeScore * 3) + (businessRelevant ? 2 : 0);
        int architecturalWeight = Math.max(0, structuralWeight + roleBonus - (noiseScore * 2));

        return new Relevance(
                workflowRelevant, businessRelevant, role, noiseScore, workflowBridgeScore, architecturalWeight);
    }

    private static int noiseScore(Component component) {
        if (component == null) {
            return 0;
        }
        int score = 0;
        if (component.type == ComponentType.UTILITY) {
            score += 3;
        } else if (component.type == ComponentType.UNKNOWN) {
            score += 2;
        }

        String name = lower(component.name);
        String qualifiedName = lower(component.qualifiedName);
        String packageName = packageName(qualifiedName);
        if (containsAny(name, "formatter", "parser", "mapper", "logger", "logging", "config", "configuration")) {
            score += 2;
        }
        if (containsAny(name, "dto", "request", "response", "properties", "constants")) {
            score += 1;
        }
        if (containsAny(packageName, ".util", ".utils", ".common", ".config", ".logging", ".dto", ".mapper")) {
            score += 1;
        }
        return score;
    }

    private static String infrastructureRole(Component component, int noiseScore) {
        if (component == null || component.type == null) {
            return "unknown";
        }
        if (component.type == ComponentType.UTILITY) {
            return "utility";
        }
        String name = lower(component.name);
        if (containsAny(name, "formatter")) return "formatter";
        if (containsAny(name, "parser")) return "parser";
        if (containsAny(name, "mapper")) return "mapper";
        if (containsAny(name, "logger", "logging")) return "logging";
        if (containsAny(name, "config", "configuration", "properties")) return "configuration";
        if (noiseScore >= 4) return "utility";

        return switch (component.type) {
            case REST_RESOURCE -> "entrypoint";
            case SERVICE, EJB_STATELESS, EJB_STATEFUL, EJB_SINGLETON -> "business-service";
            case REPOSITORY -> "repository";
            case ENTITY -> "domain-entity";
            case MESSAGE_DRIVEN_BEAN -> "message-consumer";
            case SCHEDULER -> "scheduler";
            case HTTP_CLIENT, REMOTE_SERVICE -> "external-client";
            case CDI_EVENT_CONSUMER, CDI_EVENT_PRODUCER -> "event-bus";
            case UTILITY -> "utility";
            case UNKNOWN -> "unknown";
        };
    }

    private static int workflowBridgeScore(Component component, Metrics metrics) {
        int score = 0;
        if (component != null && component.type == ComponentType.SCHEDULER) {
            score += 1;
        }
        score += metrics.stateReadCount() * 2;
        score += metrics.stateWriteCount() * 2;
        score += metrics.stateHandoffInCount() * 3;
        score += metrics.stateHandoffOutCount() * 3;
        return score;
    }

    private static boolean businessRelevant(Component component, int noiseScore) {
        if (component == null || component.type == null || noiseScore >= 4) {
            return false;
        }
        return switch (component.type) {
            case REST_RESOURCE,
                    SERVICE,
                    REPOSITORY,
                    ENTITY,
                    EJB_STATELESS,
                    EJB_STATEFUL,
                    EJB_SINGLETON,
                    MESSAGE_DRIVEN_BEAN,
                    SCHEDULER,
                    HTTP_CLIENT,
                    CDI_EVENT_CONSUMER,
                    CDI_EVENT_PRODUCER,
                    REMOTE_SERVICE -> true;
            case UTILITY, UNKNOWN -> false;
        };
    }

    private static boolean isEntrypointComponent(Component component) {
        return component != null
                && (component.type == ComponentType.REST_RESOURCE
                        || component.type == ComponentType.MESSAGE_DRIVEN_BEAN
                        || component.type == ComponentType.SCHEDULER);
    }

    private static boolean containsAny(String text, String... needles) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String lower(String text) {
        return Objects.toString(text, "").toLowerCase(Locale.ROOT);
    }

    private static String packageName(String qualifiedName) {
        int index = qualifiedName.lastIndexOf('.');
        return index > 0 ? qualifiedName.substring(0, index) : "";
    }

    record Metrics(
            int fanIn,
            int fanOut,
            int ownedEntrypointCount,
            int stateReadCount,
            int stateWriteCount,
            int stateHandoffInCount,
            int stateHandoffOutCount) {}

    record Relevance(
            boolean workflowRelevant,
            boolean businessRelevant,
            String infrastructureRole,
            int noiseScore,
            int workflowBridgeScore,
            int architecturalWeight) {}
}
