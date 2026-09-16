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

    /**
     * JPA {@code EntityManager} method names recognized as persistence operations. Used by {@link
     * #extractPersistenceOperations} to decide, together with {@link #isEntityManagerInvocation},
     * whether an invocation's executable name qualifies as an entity-manager call worth recording
     * as a {@link PersistenceOperation}.
     */
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
     * @param module build module being analyzed
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

    /**
     * Scans a method's invocations for JPA {@code EntityManager} calls and records one {@link
     * PersistenceOperation} per match. An invocation qualifies only if its executable name is one of the
     * known entity-manager operations ({@code persist}, {@code merge}, {@code remove}, {@code find}, {@code
     * getReference}, {@code refresh}, {@code flush}) and {@link #isEntityManagerInvocation} also confirms the
     * receiver is an entity manager. Each recorded operation gets a unique id suffixed with a
     * zero-based, per-method occurrence index, an inferred entity type, the consumed argument expression, the
     * resolved persistence unit name, and a fixed-confidence (1.0) source derived from the invocation's
     * location.
     *
     * @param facts module source facts used to look up the method's invocations
     * @param method method being scanned for persistence calls
     * @param component owning component the operations are attributed to
     * @param model architecture model that accumulates the resulting {@link PersistenceOperation} entries
     * @param appId owning application/module
     */
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
            operation.argumentName = consumedArgumentName(invocation);
            operation.persistenceUnitName = persistenceUnitName(model, appId, component.id);
            operation.source = source(invocation.location(), "entity-manager-invocation", 1.0);
            model.persistenceOperations.add(operation);
        }
    }

    /**
     * Resolves the effective transaction policy for one method, applying precedence in this order: (1)
     * bean-managed EJB transaction demarcation (a type-level {@code TransactionManagement(BEAN)} on an EJB
     * component) always wins and yields a fixed {@code PROGRAMMATIC}/{@code BEAN} policy at 0.95 confidence,
     * regardless of any transaction annotation present; (2) an explicit {@code @Transactional} /
     * {@code @TransactionAttribute} annotation, looked up first on the method, then on the declaring type,
     * then on an implemented interface's inherited policy ({@code inheritedTypePolicy}) — the inherited case
     * is additionally marked with an {@code inherited-policy-runtime-resolution} limitation and its source
     * confidence is capped at 0.8, since interface-level annotations are not guaranteed to be honored by
     * every runtime; (3) for an EJB with no explicit annotation, the implicit {@code REQUIRED} EJB default at
     * 0.9 confidence; (4) otherwise, if the method uses a programmatic transaction API
     * ({@link #hasProgrammaticTransactionApi}), a synthesized {@code PROGRAMMATIC} policy at 0.6 confidence.
     * When an explicit or EJB-default policy coexists with programmatic transaction API usage, the policy is
     * additionally flagged via {@link #markProgrammaticInteraction}. Returns {@code null} when none of these
     * apply (no declarative or programmatic transaction boundary could be inferred).
     *
     * @param methodAnnotations annotations declared directly on the method
     * @param typeAnnotations annotations declared on the method's declaring type
     * @param inheritedTypePolicy transaction annotation inherited from an implemented interface, or {@code
     *     null} if none
     * @param method method the policy is being computed for
     * @param component owning component, used to determine EJB-ness and for id/source construction
     * @param appId owning application/module
     * @param beanManaged whether the declaring type is annotated for bean-managed transactions
     * @param programmaticApi whether the method also invokes a programmatic transaction API
     * @return the resolved {@link TransactionPolicy}, or {@code null} if no transaction boundary applies
     */
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

    /**
     * Flags a declarative transaction policy that coexists with programmatic transaction API usage: adds a
     * {@code programmatic-transaction-api-inside-declarative-boundary} limitation and caps the policy's
     * source confidence at 0.6, since the declarative boundary may be overridden or interfered with at
     * runtime.
     *
     * @param policy policy to mutate in place
     */
    private static void markProgrammaticInteraction(TransactionPolicy policy) {
        policy.limitations.add("programmatic-transaction-api-inside-declarative-boundary");
        if (policy.source != null) policy.source.confidence = Math.min(policy.source.confidence, 0.6);
    }

    /**
     * Detects whether any invocation in the list drives a transaction through a programmatic API rather than
     * a declarative annotation: a call on a type whose name contains {@code TransactionTemplate} or {@code
     * PlatformTransactionManager}, a call on a type ending in {@code .UserTransaction} or {@code
     * .EntityTransaction}, or a {@code begin}/{@code commit}/{@code rollback} call on any type whose name
     * (case-insensitive) contains "transaction".
     *
     * @param invocations method invocations to inspect
     * @return {@code true} if a programmatic transaction API call is present
     */
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

    /**
     * Builds a {@link TransactionPolicy} from an explicit transaction annotation. The framework is inferred
     * from the annotation's qualified name ({@code spring} for {@code springframework} types, {@code ejb} for
     * {@code .ejb.} types, {@code javax} for a {@code javax.} package, otherwise {@code jakarta}). The raw
     * propagation value is read from the {@code propagation} attribute, falling back to {@code value}, and
     * normalized via {@link #normalizePolicy}; the native (pre-normalization) value defaults to {@code
     * "REQUIRED"} when absent. The resulting policy always has {@code defaulted=false}, {@code
     * programmatic=false}, and 1.0 source confidence. {@code readOnly} and {@code isolation} are copied
     * from the annotation's attributes, and any of {@code rollbackFor}, {@code noRollbackFor}, {@code
     * rollbackOn}, {@code dontRollbackOn} present on the annotation are recorded as rollback rules. A Spring
     * annotation additionally gets a {@code proxy-semantics-runtime-dependent} limitation, since Spring's
     * declarative transactions rely on proxy-based AOP that self-invocation and non-public methods can
     * bypass.
     *
     * @param annotation the explicit transaction annotation to translate
     * @param method method the annotation was found on (or inherited for)
     * @param component owning component
     * @param appId owning application/module
     * @param level declaration level the annotation was resolved at ({@code "method"}, {@code "type"}, or
     *     {@code "inherited-type"})
     * @return the resulting {@link TransactionPolicy}
     */
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

    /**
     * Constructs a {@link TransactionPolicy} with its identity, framework, normalized/native policy values,
     * and source populated. The id is derived from the component and method signature; the source's
     * {@code derivedFrom} label is {@code "framework-default"} when {@code defaulted} is {@code true}, and
     * {@code "annotation"} otherwise.
     *
     * @param method method the policy applies to
     * @param component owning component
     * @param appId owning application/module
     * @param framework transaction framework the policy originates from (e.g. {@code "spring"}, {@code
     *     "ejb"})
     * @param normalized normalized propagation value (e.g. {@code "REQUIRES_NEW"})
     * @param nativePolicy the raw, un-normalized propagation value as declared or defaulted
     * @param level declaration level the policy was resolved at
     * @param defaulted whether the policy is an implicit framework default rather than an explicit
     *     declaration
     * @param programmatic whether the policy represents programmatic (as opposed to declarative) transaction
     *     management
     * @param location source location the policy is attributed to
     * @param confidence confidence score assigned to the policy's source
     * @return the populated {@link TransactionPolicy}
     */
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

    /**
     * Finds the first annotation in the list that declares a transaction boundary — {@code @Transactional}
     * (Spring/Jakarta, matched by qualified or simple name) or {@code @TransactionAttribute} (EJB) — in list
     * order, without regard to which is "more specific".
     *
     * @param annotations annotations to search
     * @return the first matching transaction annotation, or {@code null} if none is present
     */
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

    /**
     * Looks for a transaction annotation declared on a type that {@code type} implements. Every other known
     * type is checked as a candidate supertype by testing whether {@code type} appears in the candidate's
     * recorded implementations; the first candidate (in facts-index order) carrying a transaction annotation
     * wins. Used as the fallback source of policy when neither the method nor its declaring type carries an
     * explicit annotation.
     *
     * @param facts module source facts, used to enumerate types and their implementations/annotations
     * @param type type whose implemented interfaces are searched for an inherited transaction annotation
     * @return the inherited transaction annotation, or {@code null} if no implemented interface declares one
     */
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

    /**
     * Determines whether a type declares bean-managed transaction demarcation, i.e. carries a {@code
     * .TransactionManagement} annotation whose value normalizes (via {@link #normalizePolicy}) to {@code
     * "BEAN"}.
     *
     * @param annotations annotations declared on the type
     * @return {@code true} if bean-managed transaction demarcation is declared
     */
    private static boolean isBeanManaged(List<SourceAnnotation> annotations) {
        return annotations.stream()
                .anyMatch(annotation -> annotation.qualifiedName().endsWith(".TransactionManagement")
                        && "BEAN".equals(normalizePolicy(annotation.values().get("value"))));
    }

    /**
     * Determines whether an invocation targets a JPA {@code EntityManager}: its declaring type ends with
     * {@code .EntityManager} or is exactly {@code "EntityManager"}, or, as a fallback when the declaring type
     * is unresolved, its receiver expression (case-insensitively) contains {@code "entitymanager"}.
     *
     * @param invocation invocation to inspect
     * @return {@code true} if the invocation is an entity-manager call
     */
    private static boolean isEntityManagerInvocation(SourceInvocation invocation) {
        String type = Objects.toString(invocation.executableDeclaringType(), "");
        return type.endsWith(".EntityManager")
                || "EntityManager".equals(type)
                || Objects.toString(invocation.receiverExpression(), "")
                        .toLowerCase(Locale.ROOT)
                        .contains("entitymanager");
    }

    /**
     * Infers the entity type targeted by a persistence-operation invocation. Returns {@code null} if the
     * invocation has no arguments. If the first argument is a class literal (ends with {@code ".class"}),
     * returns the literal's type name with the suffix stripped. Otherwise, treats the first argument as a
     * reference to one of the enclosing method's parameters and, if it matches a parameter name, returns that
     * parameter's declared type; if it does not match any parameter, returns {@code null}.
     *
     * @param invocation invocation whose first argument is inspected
     * @param method enclosing method, used to resolve a parameter-name argument to its declared type
     * @return the inferred entity type name, or {@code null} if it cannot be determined
     */
    private static String inferEntityType(SourceInvocation invocation, SourceMethod method) {
        if (invocation.argumentExpressions().isEmpty()) return null;
        String first = invocation.argumentExpressions().getFirst();
        if (first.endsWith(".class")) return first.substring(0, first.length() - 6);
        int parameter = method.parameterNames().indexOf(first);
        return parameter >= 0 && parameter < method.parameterTypes().size()
                ? method.parameterTypes().get(parameter)
                : null;
    }

    /**
     * Identifies the argument expression that represents the entity/id instance consumed by a
     * persistence-operation invocation, for reporting purposes. Returns {@code null} if there are no
     * arguments. For {@code find}/{@code getReference} calls with more than one argument, returns the second
     * argument (the primary key), since the first is the entity class literal rather than an instance.
     * Otherwise returns the first argument, unless it is itself a class literal (ends with {@code ".class"}),
     * in which case there is no consumed instance and {@code null} is returned.
     *
     * @param invocation invocation whose consumed argument is identified
     * @return the consumed argument expression, or {@code null} if none applies
     */
    private static String consumedArgumentName(SourceInvocation invocation) {
        if (invocation.argumentExpressions().isEmpty()) return null;
        if (Set.of("find", "getReference").contains(invocation.executableName())
                && invocation.argumentExpressions().size() > 1) {
            return invocation.argumentExpressions().get(1);
        }
        String first = invocation.argumentExpressions().getFirst();
        return first.endsWith(".class") ? null : first;
    }

    /**
     * Resolves the persistence unit name to attribute a persistence operation to. Prefers a recorded {@link
     * PersistenceUnitUsage} for the exact component with a non-blank unit name. Failing that, falls back to
     * the sole {@link PersistenceUnitInfo} declared for the application, if the application declares exactly
     * one; otherwise the persistence unit is ambiguous and {@code null} is returned.
     *
     * @param model architecture model holding recorded persistence unit usages and definitions
     * @param appId owning application/module
     * @param componentId component to resolve a persistence unit usage for
     * @return the resolved persistence unit name, or {@code null} if it cannot be determined unambiguously
     */
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

    /**
     * Looks up the model component for a source type by matching both its owning module and qualified name.
     *
     * @param model architecture model to search
     * @param qualifiedName qualified name of the type to look up
     * @param appId owning application/module the component must belong to
     * @return the matching component, or {@code null} if none is found
     */
    private static Component component(ArchitectureModel model, String qualifiedName, AppId appId) {
        return model.components.stream()
                .filter(value -> appId.equals(value.module) && qualifiedName.equals(value.qualifiedName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Determines whether a component is an EJB, i.e. its type is one of {@code EJB_STATELESS}, {@code
     * EJB_STATEFUL}, {@code EJB_SINGLETON}, or {@code MESSAGE_DRIVEN_BEAN}.
     *
     * @param component component to check
     * @return {@code true} if the component is an EJB type
     */
    private static boolean isEjb(Component component) {
        return component.type == ComponentType.EJB_STATELESS
                || component.type == ComponentType.EJB_STATEFUL
                || component.type == ComponentType.EJB_SINGLETON
                || component.type == ComponentType.MESSAGE_DRIVEN_BEAN;
    }

    /**
     * Normalizes a raw propagation value to its canonical enum name via {@link #simpleEnum}. A {@code null},
     * blank, or {@code "DEFAULT"} value normalizes to {@code "REQUIRED"}. The no-underscore Jakarta/EJB
     * spellings {@code "REQUIRESNEW"} and {@code "NOTSUPPORTED"} are mapped to the underscored {@code
     * "REQUIRES_NEW"} and {@code "NOT_SUPPORTED"}; any other value passes through unchanged.
     *
     * @param raw raw propagation value, possibly {@code null}
     * @return the normalized, canonical propagation name
     */
    private static String normalizePolicy(String raw) {
        String value = simpleEnum(raw);
        if (value == null || value.isBlank() || "DEFAULT".equals(value)) return "REQUIRED";
        return switch (value) {
            case "REQUIRESNEW" -> "REQUIRES_NEW";
            case "NOTSUPPORTED" -> "NOT_SUPPORTED";
            default -> value;
        };
    }

    /**
     * Reduces a raw enum-valued annotation attribute (which may be quoted and/or fully qualified, e.g.
     * {@code "Propagation.REQUIRES_NEW"}) to its bare, upper-cased constant name (e.g. {@code
     * "REQUIRES_NEW"}): surrounding quotes are stripped, any prefix up to and including the last {@code '.'}
     * is dropped, and the remainder is upper-cased.
     *
     * @param raw raw enum attribute text, possibly {@code null}
     * @return the bare, upper-cased enum constant name, or {@code null} if {@code raw} is {@code null}
     */
    private static String simpleEnum(String raw) {
        if (raw == null) return null;
        String value = raw.replace("\"", "").strip();
        int dot = value.lastIndexOf('.');
        if (dot >= 0) value = value.substring(dot + 1);
        return value.replace("_", "_").toUpperCase(Locale.ROOT);
    }

    /**
     * Returns the first non-null value found among the given keys, checked in the order supplied.
     *
     * @param values annotation attribute values, keyed by attribute name
     * @param keys attribute names to check, in priority order
     * @return the first non-null value found, or {@code null} if none of the keys are present
     */
    private static String firstValue(Map<String, String> values, String... keys) {
        for (String key : keys) if (values.get(key) != null) return values.get(key);
        return null;
    }

    /**
     * Parses a raw annotation attribute string into a {@link Boolean}, preserving absence.
     *
     * @param value raw attribute text, possibly {@code null}
     * @return the parsed value, or {@code null} if {@code value} is {@code null}
     */
    private static Boolean booleanValue(String value) {
        return value == null ? null : Boolean.valueOf(value);
    }

    /**
     * Returns the source location of the first annotation in the list, used as the attribution point for
     * policies (such as bean-managed EJB transactions) that are not tied to a single annotation attribute.
     *
     * @param annotations annotations to take a location from
     * @return the first annotation's location, or {@link SourceLocation#unknown()} if the list is empty
     */
    private static SourceLocation firstLocation(List<SourceAnnotation> annotations) {
        return annotations.isEmpty()
                ? SourceLocation.unknown()
                : annotations.getFirst().location();
    }

    /**
     * Builds a {@link SourceInfo} from a location, clamping the line number to non-negative (so an unknown
     * or negative line is reported as line 0), together with the given provenance label and confidence.
     *
     * @param location source location the fact was derived from
     * @param derivedFrom label describing how the fact was derived (e.g. {@code "entity-manager-invocation"})
     * @param confidence confidence score for the derived fact
     * @return the constructed {@link SourceInfo}
     */
    private static SourceInfo source(SourceLocation location, String derivedFrom, double confidence) {
        return new SourceInfo(location.file(), Math.max(0, location.line()), derivedFrom, confidence);
    }
}
