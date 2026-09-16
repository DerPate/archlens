package dev.dominikbreu.archlens.cache;

import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import java.util.Locale;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Scores graph components for workflow relevance without letting utility fan-in dominate.
 */
final class ArchitectureRelevanceScorer {

    /**
     * Infrastructure-role label and name keyword for configuration-style components (settings
     * holders, {@code @ConfigMapping}/properties classes).
     */
    private static final String CONFIGURATION = "configuration";

    /** Infrastructure-role label and name keyword for output-formatting helpers. */
    private static final String FORMATTER = "formatter";

    /** Infrastructure-role label and name keyword for logging support code. */
    private static final String LOGGING = "logging";

    /** Infrastructure-role label and name keyword for object-mapping helpers. */
    private static final String MAPPER = "mapper";

    /** Infrastructure-role label and name keyword for parsing helpers. */
    private static final String PARSER = "parser";

    /** Infrastructure-role label used for {@link ComponentType#UTILITY} components and general-purpose helpers. */
    private static final String UTILITY = "utility";

    private ArchitectureRelevanceScorer() {}

    static Relevance score(Component component, Metrics metrics) {
        int noiseScore = noiseScore(component);
        String role = infrastructureRole(component, noiseScore);
        boolean businessRelevant = businessRelevant(component, noiseScore);
        int workflowBridgeScore = workflowBridgeScore(component, metrics, businessRelevant);
        boolean workflowRelevant = businessRelevant
                && (metrics.ownedEntrypointCount() > 0
                        || metrics.fanOut() > 0
                        || workflowBridgeScore > 0
                        || isEntrypointComponent(component));

        int structuralWeight = Math.min(metrics.fanIn(), 3) + metrics.fanOut() + (metrics.ownedEntrypointCount() * 4);
        int roleBonus = (workflowBridgeScore * 3) + (businessRelevant ? 2 : 0);
        int architecturalWeight = Math.max(0, structuralWeight + roleBonus - (noiseScore * 2));
        ComponentClassifier.Classification classification = ComponentClassifier.classify(component, metrics);

        return new Relevance(
                workflowRelevant,
                businessRelevant,
                role,
                noiseScore,
                workflowBridgeScore,
                architecturalWeight,
                classification.primaryRole(),
                classification.supportRole(),
                classification.agentCategory(),
                classification.evidence());
    }

    /**
     * Scores how much a component looks like incidental plumbing rather than business logic, so
     * that fan-in/fan-out from utility-shaped code can be discounted in {@link #score}.
     *
     * <p>Points accumulate from independent, additive signals: a {@link ComponentType#UTILITY}
     * type contributes the most (3), a {@link ComponentType#UNKNOWN} type or a name that matches
     * a well-known infrastructure keyword (formatter, parser, mapper, logger, config) each
     * contribute the same mid weight (2), a name that matches a data-carrier/constant keyword
     * (DTO, request, response, properties, constants) contributes a lower weight (1), and a
     * package that lives under a conventional infrastructure namespace (util, common, config,
     * logging, dto, mapper) contributes one more point (1). A {@code null} component is treated
     * as neutral (no noise) since there is no evidence either way.
     *
     * @param component the component to inspect, or {@code null}
     * @return the accumulated noise score; higher means more likely to be non-business plumbing
     */
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
        if (containsAny(name, FORMATTER, PARSER, MAPPER, "logger", LOGGING, "config", CONFIGURATION)) {
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

    /**
     * Assigns a human-readable infrastructure/architecture role label to a component, preferring
     * specific name-based keyword matches (formatter, parser, mapper, logger, config) over the
     * generic {@link ComponentType}, since a service named e.g. {@code OrderMapper} is more useful
     * to describe as a mapper than as a business service. Components with a high {@code noiseScore}
     * that did not already match a keyword are folded into {@link #UTILITY} rather than left with
     * a misleadingly specific structural role. Everything else falls back to a fixed mapping from
     * {@link ComponentType} to role label.
     *
     * @param component the component to classify, or {@code null}
     * @param noiseScore the component's precomputed {@link #noiseScore(Component)}, used as a
     *     tie-breaker toward {@link #UTILITY} when no keyword matched
     * @return the infrastructure role label; {@code "unknown"} if the component or its type is
     *     {@code null}
     */
    private static String infrastructureRole(Component component, int noiseScore) {
        if (component == null || component.type == null) {
            return "unknown";
        }
        if (component.type == ComponentType.UTILITY) {
            return UTILITY;
        }
        String name = lower(component.name);
        if (containsAny(name, FORMATTER)) return FORMATTER;
        if (containsAny(name, PARSER)) return PARSER;
        if (containsAny(name, MAPPER)) return MAPPER;
        if (containsAny(name, "logger", LOGGING)) return LOGGING;
        if (containsAny(name, "config", CONFIGURATION, "properties")) return CONFIGURATION;
        if (noiseScore >= 4) return UTILITY;

        return switch (component.type) {
            case REST_RESOURCE -> "entrypoint";
            case SERVICE, EJB_STATELESS, EJB_STATEFUL, EJB_SINGLETON -> "business-service";
            case REPOSITORY -> "repository";
            case ENTITY -> "domain-entity";
            case MESSAGE_DRIVEN_BEAN -> "message-consumer";
            case SCHEDULER -> "scheduler";
            case HTTP_CLIENT, REMOTE_SERVICE -> "external-client";
            case CDI_EVENT_CONSUMER, CDI_EVENT_PRODUCER -> "event-bus";
            case UTILITY -> UTILITY;
            case UNKNOWN -> "unknown";
        };
    }

    /**
     * Scores how much a component acts as a bridge between workflow steps: a scheduler that kicks
     * off work, or a component that reads/writes state across component boundaries or explicitly
     * hands off state to another component. Cross-component state reads/writes count for one point
     * each, while explicit state handoffs (a stronger, intentional signal of workflow continuation)
     * count for three points each in both directions.
     *
     * <p>When the component is not otherwise {@link #businessRelevant}, the score is capped at 2 so
     * that heavy state traffic through infrastructure/utility code cannot alone make it look like a
     * workflow bridge.
     *
     * @param component the component to inspect, or {@code null}
     * @param metrics the component's graph-derived metrics
     * @param businessRelevant whether the component is otherwise considered business-relevant, as
     *     computed by {@link #businessRelevant(Component, int)}
     * @return the workflow-bridge score, capped at 2 when {@code businessRelevant} is {@code false}
     */
    private static int workflowBridgeScore(Component component, Metrics metrics, boolean businessRelevant) {
        int score = 0;
        if (component != null && component.type == ComponentType.SCHEDULER) {
            score += 1;
        }
        score += metrics.crossStateReadCount();
        score += metrics.crossStateWriteCount();
        score += metrics.stateHandoffInCount() * 3;
        score += metrics.stateHandoffOutCount() * 3;
        if (businessRelevant) {
            return score;
        } else {
            return Math.min(score, 2);
        }
    }

    /**
     * Decides whether a component's declared type is the kind that carries business/domain
     * behavior, independent of how it scores structurally. Endpoints, services, EJBs, repositories,
     * entities, message/event handlers, schedulers, and remote/HTTP clients all count as business
     * relevant because they participate in application workflows; utility and unknown components do
     * not. A component whose {@code noiseScore} is already high (4 or more) is treated as not
     * business relevant regardless of type, since strong naming/package evidence of plumbing
     * outweighs the nominal component type.
     *
     * @param component the component to inspect, or {@code null}
     * @param noiseScore the component's precomputed {@link #noiseScore(Component)}
     * @return {@code true} if the component's type and noise level indicate business relevance
     */
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

    /**
     * Decides whether a component's type is itself an entrypoint kind (REST resource, message
     * consumer, or scheduler), regardless of whether the graph has recorded any owned entrypoint
     * vertices for it. This lets {@link #score} still mark a component as workflow-relevant even
     * when {@code ownedEntrypointCount} and {@code fanOut} are both zero, e.g. a REST resource with
     * no outgoing dependencies.
     *
     * @param component the component to inspect, or {@code null}
     * @return {@code true} if the component's type is {@code REST_RESOURCE}, {@code
     *     MESSAGE_DRIVEN_BEAN}, or {@code SCHEDULER}
     */
    private static boolean isEntrypointComponent(Component component) {
        return component != null
                && (component.type == ComponentType.REST_RESOURCE
                        || component.type == ComponentType.MESSAGE_DRIVEN_BEAN
                        || component.type == ComponentType.SCHEDULER);
    }

    /**
     * Checks whether {@code text} contains any of the given substrings, treating a blank or
     * {@code null} {@code text} as matching nothing.
     *
     * @param text the (already lower-cased) text to search, or {@code null}/blank
     * @param needles the candidate substrings to look for
     * @return {@code true} if {@code text} is non-blank and contains at least one needle
     */
    private static boolean containsAny(String text, String... needles) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Normalizes text for case-insensitive keyword matching, treating {@code null} as the empty
     * string so callers never need a null check.
     *
     * @param text the text to normalize, or {@code null}
     * @return {@code text} lower-cased with {@link Locale#ROOT}, or {@code ""} if {@code text} is
     *     {@code null}
     */
    private static String lower(String text) {
        return Objects.toString(text, "").toLowerCase(Locale.ROOT);
    }

    /**
     * Extracts the package portion of a dotted qualified name by truncating at the last {@code '.'}.
     * Callers pass an already-{@link #lower(String) lower}-cased, non-{@code null} qualified name,
     * so no null handling is needed here.
     *
     * @param qualifiedName the fully-qualified (lower-cased) component name
     * @return the substring before the last {@code '.'}, or {@code ""} if {@code qualifiedName} has
     *     no {@code '.'} or starts with one
     */
    private static String packageName(String qualifiedName) {
        int index = qualifiedName.lastIndexOf('.');
        if (index > 0) {
            return qualifiedName.substring(0, index);
        } else {
            return "";
        }
    }

    /**
     * Graph-derived structural and data-flow counts for one component, computed by the caller from
     * the projected dependency graph and fed into {@link #score}.
     *
     * @param fanIn number of components that depend on this one
     * @param fanOut number of components this one depends on
     * @param ownedEntrypointCount number of entrypoints (REST resources, message consumers,
     *     schedulers, ...) that start at this component
     * @param stateReadCount total number of state-read edges out of this component; captured for
     *     completeness but not currently used by {@link #score}
     * @param stateWriteCount total number of state-write edges out of this component; captured for
     *     completeness but not currently used by {@link #score}
     * @param crossStateReadCount number of state-read edges out of this component that cross into a
     *     different component
     * @param crossStateWriteCount number of state-write edges out of this component that cross into
     *     a different component
     * @param stateHandoffInCount number of explicit state-handoff edges into this component from
     *     another component
     * @param stateHandoffOutCount number of explicit state-handoff edges out of this component to
     *     another component
     */
    record Metrics(
            int fanIn,
            int fanOut,
            int ownedEntrypointCount,
            int stateReadCount,
            int stateWriteCount,
            int crossStateReadCount,
            int crossStateWriteCount,
            int stateHandoffInCount,
            int stateHandoffOutCount) {}

    /**
     * The full result of scoring one component: whether it matters for workflow tracing and
     * agent-facing reporting, plus the intermediate signals that produced that verdict.
     *
     * @param workflowRelevant {@code true} if the component is business relevant and shows
     *     evidence of participating in a workflow (owns an entrypoint, has fan-out, bridges
     *     workflow steps, or is itself an entrypoint-shaped type)
     * @param businessRelevant {@code true} if the component's type carries business/domain
     *     behavior, as computed by {@link #businessRelevant(Component, int)}
     * @param infrastructureRole the human-readable infrastructure/architecture role label from
     *     {@link #infrastructureRole(Component, int)}
     * @param noiseScore the component's {@link #noiseScore(Component)}, indicating how much it
     *     looks like incidental plumbing rather than business logic
     * @param workflowBridgeScore the component's {@link #workflowBridgeScore(Component, Metrics,
     *     boolean)}, indicating how much it bridges workflow steps via state reads/writes/handoffs
     * @param architecturalWeight the overall relevance weight, combining structural fan-in/fan-out,
     *     owned entrypoints, workflow-bridge score, and business relevance, discounted by
     *     {@code noiseScore}
     * @param primaryRole the component's primary architectural role from {@link
     *     ComponentClassifier}
     * @param supportRole the component's support-role classification from {@link
     *     ComponentClassifier}, or {@code null} if it is not a support component
     * @param agentCategory the agent-facing category grouping from {@link ComponentClassifier}
     * @param classificationEvidence the formatted evidence string explaining {@link
     *     ComponentClassifier}'s classification decision
     */
    record Relevance(
            boolean workflowRelevant,
            boolean businessRelevant,
            String infrastructureRole,
            int noiseScore,
            int workflowBridgeScore,
            int architecturalWeight,
            String primaryRole,
            String supportRole,
            String agentCategory,
            String classificationEvidence) {}
}
