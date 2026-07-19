package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.build.BuildModule;
import dev.dominikbreu.archlens.extractor.sourcefacts.SourceAnnotation;
import dev.dominikbreu.archlens.extractor.sourcefacts.SourceFactIndex;
import dev.dominikbreu.archlens.extractor.sourcefacts.SourceInvocation;
import dev.dominikbreu.archlens.extractor.sourcefacts.SourceLocation;
import dev.dominikbreu.archlens.extractor.sourcefacts.SourceMethod;
import dev.dominikbreu.archlens.extractor.sourcefacts.SourceType;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.PersistenceOperation;
import dev.dominikbreu.archlens.model.PersistenceUnitInfo;
import dev.dominikbreu.archlens.model.PersistenceUnitUsage;
import dev.dominikbreu.archlens.model.SourceInfo;
import dev.dominikbreu.archlens.model.TransactionPolicy;
import dev.dominikbreu.archlens.model.ids.AppId;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Extracts method-local JPA operations and effective framework transaction policies. */
public class TransactionPolicyExtractor {

    private static final Set<String> ENTITY_MANAGER_OPERATIONS =
            Set.of("persist", "merge", "remove", "find", "getReference", "refresh", "flush");

    /** Creates an extractor using the shared source-fact index. */
    public TransactionPolicyExtractor() {}

    /**
     * Extracts persistence operations and effective transaction policies for one module.
     *
     * @param facts module source facts
     * @param model architecture model to enrich
     * @param appId owning application/module
     */
    public void extract(SourceFactIndex facts, ArchitectureModel model, AppId appId, BuildModule module) {
        for (SourceType type : facts.types()) {
            Component component = component(model, type.qualifiedName(), appId);
            if (component == null) continue;
            List<SourceAnnotation> typeAnnotations = facts.annotations(type.id());
            SourceAnnotation inheritedTypePolicy = inheritedTypePolicy(facts, type);
            boolean beanManaged = isBeanManaged(typeAnnotations);
            for (SourceMethod method : facts.methods(type.id())) {
                extractPersistenceOperations(facts, method, component, model, appId);
                boolean programmaticApi = hasProgrammaticTransactionApi(facts.invocations(method.id()));
                TransactionPolicy policy = effectivePolicy(
                        facts.annotations(method.id()),
                        typeAnnotations,
                        inheritedTypePolicy,
                        method,
                        component,
                        appId,
                        beanManaged,
                        programmaticApi);
                if (policy != null) model.transactionPolicies.add(policy);
            }
        }
        new TransactionXmlPolicyResolver().apply(module, facts, model, appId);
    }

    private void extractPersistenceOperations(
            SourceFactIndex facts, SourceMethod method, Component component, ArchitectureModel model, AppId appId) {
        int index = 0;
        for (SourceInvocation invocation : facts.invocations(method.id())) {
            if (!ENTITY_MANAGER_OPERATIONS.contains(invocation.executableName())
                    || !isEntityManagerInvocation(invocation)) continue;
            PersistenceOperation operation = new PersistenceOperation();
            operation.id =
                    "persistence-operation:" + component.id.serialize() + "#" + method.signature() + ":" + index++;
            operation.appId = appId;
            operation.componentId = component.id;
            operation.methodName = method.name();
            operation.methodSignature = method.signature();
            operation.operation = invocation.executableName();
            operation.entityType = inferEntityType(invocation, method);
            operation.persistenceUnitName = persistenceUnitName(model, appId, component.id);
            operation.source = source(invocation.location(), "entity-manager-invocation", 1.0);
            model.persistenceOperations.add(operation);
        }
    }

    private TransactionPolicy effectivePolicy(
            List<SourceAnnotation> methodAnnotations,
            List<SourceAnnotation> typeAnnotations,
            SourceAnnotation inheritedTypePolicy,
            SourceMethod method,
            Component component,
            AppId appId,
            boolean beanManaged,
            boolean programmaticApi) {
        if (beanManaged && isEjb(component)) {
            return policy(
                    method,
                    component,
                    appId,
                    "ejb",
                    "PROGRAMMATIC",
                    "BEAN",
                    "bean-managed",
                    false,
                    true,
                    firstLocation(typeAnnotations),
                    0.95);
        }
        SourceAnnotation explicit = transactionAnnotation(methodAnnotations);
        String level = "method";
        if (explicit == null) {
            explicit = transactionAnnotation(typeAnnotations);
            level = "type";
        }
        if (explicit == null) {
            explicit = inheritedTypePolicy;
            level = "inherited-type";
        }
        if (explicit != null) {
            TransactionPolicy policy = fromAnnotation(explicit, method, component, appId, level);
            if ("inherited-type".equals(level)) {
                policy.limitations.add("inherited-policy-runtime-resolution");
                if (policy.source != null) policy.source.confidence = Math.min(policy.source.confidence, 0.8);
            }
            if (programmaticApi) markProgrammaticInteraction(policy);
            return policy;
        }
        if (isEjb(component)) {
            TransactionPolicy policy = policy(
                    method,
                    component,
                    appId,
                    "ejb",
                    "REQUIRED",
                    "REQUIRED",
                    "ejb-default",
                    true,
                    false,
                    component.source != null
                            ? new SourceLocation(component.source.file, component.source.line)
                            : SourceLocation.unknown(),
                    0.9);
            if (programmaticApi) markProgrammaticInteraction(policy);
            return policy;
        }
        if (programmaticApi) {
            TransactionPolicy policy = policy(
                    method,
                    component,
                    appId,
                    "programmatic",
                    "PROGRAMMATIC",
                    "PROGRAMMATIC",
                    "programmatic-api",
                    false,
                    true,
                    method.location(),
                    0.6);
            policy.limitations.add("scope-controlled-by-programmatic-api");
            return policy;
        }
        return null;
    }

    private static void markProgrammaticInteraction(TransactionPolicy policy) {
        policy.limitations.add("programmatic-transaction-api-inside-declarative-boundary");
        if (policy.source != null) policy.source.confidence = Math.min(policy.source.confidence, 0.6);
    }

    private static boolean hasProgrammaticTransactionApi(List<SourceInvocation> invocations) {
        return invocations.stream().anyMatch(invocation -> {
            String type = Objects.toString(invocation.executableDeclaringType(), "");
            String method = Objects.toString(invocation.executableName(), "");
            return type.contains("TransactionTemplate")
                    || type.contains("PlatformTransactionManager")
                    || type.endsWith(".UserTransaction")
                    || type.endsWith(".EntityTransaction")
                    || (("begin".equals(method) || "commit".equals(method) || "rollback".equals(method))
                            && type.toLowerCase(Locale.ROOT).contains("transaction"));
        });
    }

    private TransactionPolicy fromAnnotation(
            SourceAnnotation annotation, SourceMethod method, Component component, AppId appId, String level) {
        String qn = annotation.qualifiedName();
        String framework = qn.contains("springframework")
                ? "spring"
                : qn.contains(".ejb.") ? "ejb" : qn.startsWith("javax.") ? "javax" : "jakarta";
        String raw = firstValue(annotation.values(), "propagation", "value");
        String normalized = normalizePolicy(raw);
        TransactionPolicy policy = policy(
                method,
                component,
                appId,
                framework,
                normalized,
                Objects.toString(raw, "REQUIRED"),
                level,
                false,
                false,
                annotation.location(),
                1.0);
        policy.readOnly = booleanValue(annotation.values().get("readOnly"));
        policy.isolation = simpleEnum(annotation.values().get("isolation"));
        for (String key : List.of("rollbackFor", "noRollbackFor", "rollbackOn", "dontRollbackOn")) {
            String value = annotation.values().get(key);
            if (value != null) policy.rollbackRules.add(key + "=" + value);
        }
        if ("spring".equals(framework)) policy.limitations.add("proxy-semantics-runtime-dependent");
        return policy;
    }

    private TransactionPolicy policy(
            SourceMethod method,
            Component component,
            AppId appId,
            String framework,
            String normalized,
            String nativePolicy,
            String level,
            boolean defaulted,
            boolean programmatic,
            SourceLocation location,
            double confidence) {
        TransactionPolicy policy = new TransactionPolicy();
        policy.id = "transaction-boundary:" + component.id.serialize() + "#" + method.signature();
        policy.appId = appId;
        policy.componentId = component.id;
        policy.methodName = method.name();
        policy.methodSignature = method.signature();
        policy.framework = framework;
        policy.policy = normalized;
        policy.nativePolicy = nativePolicy;
        policy.declarationLevel = level;
        policy.defaulted = defaulted;
        policy.programmatic = programmatic;
        policy.source = source(location, defaulted ? "framework-default" : "annotation", confidence);
        return policy;
    }

    private static SourceAnnotation transactionAnnotation(List<SourceAnnotation> annotations) {
        return annotations.stream()
                .filter(annotation -> {
                    String qn = annotation.qualifiedName();
                    return qn.endsWith(".Transactional")
                            || qn.endsWith(".TransactionAttribute")
                            || "Transactional".equals(qn)
                            || "TransactionAttribute".equals(qn);
                })
                .findFirst()
                .orElse(null);
    }

    private static SourceAnnotation inheritedTypePolicy(SourceFactIndex facts, SourceType type) {
        for (SourceType candidate : facts.types()) {
            if (candidate.id().equals(type.id())) continue;
            boolean implemented = facts.implementations(candidate.qualifiedName()).stream()
                    .anyMatch(implementation -> implementation.id().equals(type.id()));
            if (!implemented) continue;
            SourceAnnotation annotation = transactionAnnotation(facts.annotations(candidate.id()));
            if (annotation != null) return annotation;
        }
        return null;
    }

    private static boolean isBeanManaged(List<SourceAnnotation> annotations) {
        return annotations.stream()
                .anyMatch(annotation -> annotation.qualifiedName().endsWith(".TransactionManagement")
                        && "BEAN".equals(normalizePolicy(annotation.values().get("value"))));
    }

    private static boolean isEntityManagerInvocation(SourceInvocation invocation) {
        String type = Objects.toString(invocation.executableDeclaringType(), "");
        return type.endsWith(".EntityManager")
                || "EntityManager".equals(type)
                || Objects.toString(invocation.receiverExpression(), "")
                        .toLowerCase(Locale.ROOT)
                        .contains("entitymanager");
    }

    private static String inferEntityType(SourceInvocation invocation, SourceMethod method) {
        if (invocation.argumentExpressions().isEmpty()) return null;
        String first = invocation.argumentExpressions().getFirst();
        if (first.endsWith(".class")) return first.substring(0, first.length() - 6);
        int parameter = method.parameterNames().indexOf(first);
        return parameter >= 0 && parameter < method.parameterTypes().size()
                ? method.parameterTypes().get(parameter)
                : null;
    }

    private static String persistenceUnitName(ArchitectureModel model, AppId appId, ComponentId componentId) {
        List<PersistenceUnitUsage> usages = model.persistenceUnitUsages.stream()
                .filter(usage -> appId.equals(usage.appId) && componentId.equals(usage.componentId))
                .toList();
        if (!usages.isEmpty()
                && !Objects.toString(usages.getFirst().unitName, "").isBlank()) return usages.getFirst().unitName;
        List<PersistenceUnitInfo> units = model.persistenceUnits.stream()
                .filter(unit -> appId.equals(unit.appId))
                .toList();
        return units.size() == 1 ? units.getFirst().name : null;
    }

    private static Component component(ArchitectureModel model, String qualifiedName, AppId appId) {
        return model.components.stream()
                .filter(value -> appId.equals(value.module) && qualifiedName.equals(value.qualifiedName))
                .findFirst()
                .orElse(null);
    }

    private static boolean isEjb(Component component) {
        return component.type == ComponentType.EJB_STATELESS
                || component.type == ComponentType.EJB_STATEFUL
                || component.type == ComponentType.EJB_SINGLETON
                || component.type == ComponentType.MESSAGE_DRIVEN_BEAN;
    }

    private static String normalizePolicy(String raw) {
        String value = simpleEnum(raw);
        if (value == null || value.isBlank() || "DEFAULT".equals(value)) return "REQUIRED";
        return switch (value) {
            case "REQUIRESNEW" -> "REQUIRES_NEW";
            case "NOTSUPPORTED" -> "NOT_SUPPORTED";
            default -> value;
        };
    }

    private static String simpleEnum(String raw) {
        if (raw == null) return null;
        String value = raw.replace("\"", "").strip();
        int dot = value.lastIndexOf('.');
        if (dot >= 0) value = value.substring(dot + 1);
        return value.replace("_", "_").toUpperCase(Locale.ROOT);
    }

    private static String firstValue(Map<String, String> values, String... keys) {
        for (String key : keys) if (values.get(key) != null) return values.get(key);
        return null;
    }

    private static Boolean booleanValue(String value) {
        return value == null ? null : Boolean.valueOf(value);
    }

    private static SourceLocation firstLocation(List<SourceAnnotation> annotations) {
        return annotations.isEmpty()
                ? SourceLocation.unknown()
                : annotations.getFirst().location();
    }

    private static SourceInfo source(SourceLocation location, String derivedFrom, double confidence) {
        return new SourceInfo(location.file(), Math.max(0, location.line()), derivedFrom, confidence);
    }
}
