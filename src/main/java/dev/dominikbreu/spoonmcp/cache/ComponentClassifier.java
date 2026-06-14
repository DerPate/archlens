package dev.dominikbreu.spoonmcp.cache;

import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Produces agent-facing component role metadata from source and graph evidence.
 */
final class ComponentClassifier {

    private ComponentClassifier() {}

    static Classification classify(Component component, ArchitectureRelevanceScorer.Metrics metrics) {
        if (component == null || component.type == null) {
            return new Classification("unknown", null, "low-signal", "type:unknown");
        }

        Evidence evidence = Evidence.from(component);
        String supportRole = supportRole(component, evidence);
        String primaryRole = primaryRole(component, supportRole);
        String agentCategory = agentCategory(metrics, primaryRole, supportRole);
        return new Classification(primaryRole, supportRole, agentCategory, evidence.format());
    }

    private static String primaryRole(Component component, String supportRole) {
        if (supportRole != null) {
            return "support";
        }
        return switch (component.type) {
            case REST_RESOURCE -> "entrypoint";
            case SERVICE, EJB_STATELESS, EJB_STATEFUL, EJB_SINGLETON -> "business-service";
            case REPOSITORY -> "data-access";
            case ENTITY -> "domain-model";
            case MESSAGE_DRIVEN_BEAN, SCHEDULER -> "entrypoint";
            case HTTP_CLIENT, REMOTE_SERVICE -> "integration";
            case CDI_EVENT_CONSUMER, CDI_EVENT_PRODUCER -> "integration";
            case UTILITY -> "support";
            case UNKNOWN -> "unknown";
        };
    }

    private static String agentCategory(
            ArchitectureRelevanceScorer.Metrics metrics, String primaryRole, String supportRole) {
        if (supportRole != null) {
            return "supporting-infrastructure";
        }
        return switch (primaryRole) {
            case "entrypoint" -> "boundary";
            case "data-access", "domain-model" -> "data";
            case "integration" -> "integration";
            case "business-service" -> metrics.fanOut() > 0 || metrics.ownedEntrypointCount() > 0
                    ? "core-workflow"
                    : "supporting-infrastructure";
            case "support" -> "supporting-infrastructure";
            default -> "low-signal";
        };
    }

    private static String supportRole(Component component, Evidence evidence) {
        String name = lower(component.name);
        String packageName = evidence.packageName();
        if (containsAny(packageName, "authorization", "security") && evidence.hasConfiguration()) {
            return "security-configuration";
        }
        if (containsAny(packageName, "redis") && containsAny(name, "lock", "registry")) {
            return "redis-lock";
        }
        if (containsAny(name, "flyway", "initializer", "migration")
                && containsAny(packageName, "infrastructure", "migration")) {
            return "migration-initializer";
        }
        if (containsAny(packageName, "multitenant", "tenant") && containsAny(name, "tenant")) {
            return "tenant-infrastructure";
        }
        if (evidence.hasConfiguration() || containsAny(name, "config", "configuration", "properties")) {
            return "configuration";
        }
        if (containsAny(packageName, "mapper", "mapping") || containsAny(name, "mapper")) {
            return "mapper";
        }
        if (containsAny(packageName, "converter") || containsAny(name, "converter")) {
            return "converter";
        }
        if (containsAny(packageName, "validation") || containsAny(name, "validator", "validation")) {
            return "validator";
        }
        if (containsAny(name, "application") && component.type == ComponentType.SERVICE) {
            return "application-bootstrap";
        }
        if (component.type == ComponentType.UTILITY || containsAny(packageName, ".util", ".utils")) {
            return "utility";
        }
        return null;
    }

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

    private static String lower(String value) {
        return Objects.toString(value, "").toLowerCase(Locale.ROOT);
    }

    record Classification(String primaryRole, String supportRole, String agentCategory, String evidence) {}

    private record Evidence(String packageName, List<String> reasons) {
        static Evidence from(Component component) {
            List<String> reasons = new ArrayList<>();
            String qualifiedName = lower(component.qualifiedName);
            String packageName = packageName(qualifiedName);
            reasons.add("type:" + component.type.name());
            if (StringUtils.isNotBlank(packageName)) {
                reasons.add("package:" + shortPackage(packageName));
            }
            if (StringUtils.isNotBlank(component.name)) {
                reasons.add("name:" + component.name);
            }
            component.stereotypes.forEach(stereotype -> reasons.add("stereotype:" + stereotype));
            return new Evidence(packageName, reasons);
        }

        boolean hasConfiguration() {
            return reasons.stream().anyMatch(reason -> reason.equals("stereotype:configuration"))
                    || packageName.contains("config")
                    || packageName.contains("configuration");
        }

        String format() {
            return String.join(",", reasons);
        }

        private static String packageName(String qualifiedName) {
            int index = qualifiedName.lastIndexOf('.');
            return index > 0 ? qualifiedName.substring(0, index) : "";
        }

        private static String shortPackage(String packageName) {
            if (packageName.contains("multitenant.config")) {
                return "multiTenant.config";
            }
            int index = packageName.lastIndexOf('.');
            return index >= 0 ? packageName.substring(index + 1) : packageName;
        }
    }
}
