package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.extractor.objectflow.ObjectFlowIndex;
import dev.dominikbreu.spoonmcp.extractor.objectflow.ReceiverTarget;
import dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndex;
import dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndexBuilder;
import dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceInjectionPoint;
import dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceType;
import dev.dominikbreu.spoonmcp.model.*;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.*;
import java.util.stream.Collectors;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtConditional;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Extracts directed method-call edges between architecture components by walking
 * {@link CtInvocation} nodes in each component's methods.
 *
 * <p>Only cross-component calls are recorded: calls on injected fields whose declared
 * type resolves to a known component. Intra-component private-method calls are ignored.
 * Interface-typed fields are resolved first by qualified name, then by simple name.
 *
 * <p>Must run after all components have been registered (after Pass 1 and dependency
 * extraction) so that the component-by-id map is complete.
 */
public class CallGraphExtractor {

    private static final Set<String> EVENT_BUS_TYPES = Set.of("EventBus");
    private static final Set<String> EMITTER_TYPES = Set.of("Emitter", "MutinyEmitter");

    private static final Set<String> SHARED_STATE_SIMPLE_TYPES = Set.of(
            "Map",
            "ConcurrentMap",
            "ConcurrentHashMap",
            "HashMap",
            "LinkedHashMap",
            "TreeMap",
            "List",
            "ArrayList",
            "LinkedList",
            "CopyOnWriteArrayList",
            "Set",
            "HashSet",
            "LinkedHashSet",
            "TreeSet",
            "ConcurrentSkipListSet",
            "Collection",
            "Queue",
            "Deque",
            "BlockingQueue",
            "ConcurrentLinkedQueue",
            "AtomicReference",
            "AtomicLong",
            "AtomicInteger",
            "AtomicBoolean");

    private static final Set<String> SHARED_STATE_NAME_SUFFIXES =
            Set.of("Cache", "State", "Store", "Buffer", "Queue", "Registry", "Snapshots", "Repository");

    private static final Set<String> SHARED_STATE_TYPE_DENYLIST = Set.of("Logger", "Log", "Slf4j", "Tracer");

    private static final Set<String> SHARED_STATE_TYPE_DENYLIST_PREFIXES = Set.of("Audit");

    private static final Set<String> FILE_OUTBOUND_PREFIXES = Set.of("java.nio.file.Files");

    private static final Set<String> OBJECT_STORAGE_PREFIXES =
            Set.of("software.amazon.awssdk.services.s3", "com.azure.storage");

    private static final Set<String> WRITE_METHODS = Set.of(
            "put",
            "putIfAbsent",
            "putAll",
            "computeIfAbsent",
            "compute",
            "merge",
            "replace",
            "add",
            "addAll",
            "addFirst",
            "addLast",
            "offer",
            "offerFirst",
            "offerLast",
            "push",
            "set",
            "lazySet",
            "getAndSet",
            "compareAndSet",
            "accumulateAndGet",
            "updateAndGet");

    private static final Set<String> READ_METHODS = Set.of(
            "get", "containsKey", "containsValue", "values", "keySet", "entrySet", "size", "isEmpty");

    private final ObjectFlowIndex objectFlowIndex;
    private final SourceFactIndex sourceFacts;

    /** Creates a call graph extractor using default resolution rules. */
    public CallGraphExtractor() {
        this(ObjectFlowIndex.empty(), null);
    }

    public CallGraphExtractor(ObjectFlowIndex objectFlowIndex) {
        this(objectFlowIndex, null);
    }

    public CallGraphExtractor(ObjectFlowIndex objectFlowIndex, SourceFactIndex sourceFacts) {
        this.objectFlowIndex = objectFlowIndex == null ? ObjectFlowIndex.empty() : objectFlowIndex;
        this.sourceFacts = sourceFacts;
    }

    private static Tracer tracer() {
        return GlobalOpenTelemetry.getTracer("dev.dominikbreu.spoonmcp");
    }

    /**
     * One-pass method scan: collects all invocations in source order and records, at
     * each invocation site, which local names have been reassigned (killed) prior to that point.
     * Replaces the prior {@code computeKilledSnapshots} + 4 separate {@code getElements}
     * traversals with a single walk of the method body.
     */
    private record MethodScan(
            List<CtInvocation<?>> invocations,
            Map<CtInvocation<?>, Set<String>> killedSnapshots,
            List<CtAssignment<?, ?>> assignments,
            List<CtFieldRead<?>> fieldReads) {}

    private static MethodScan scanMethod(CtMethod<?> method) {
        List<CtInvocation<?>> invocations = new ArrayList<>();
        Map<CtInvocation<?>, Set<String>> snapshots = new java.util.IdentityHashMap<>();
        List<CtAssignment<?, ?>> assignments = new ArrayList<>();
        List<CtFieldRead<?>> fieldReads = new ArrayList<>();
        Set<String> killed = new LinkedHashSet<>();
        for (var element : method.getElements(new TypeFilter<>(spoon.reflect.declaration.CtElement.class))) {
            if (element instanceof CtInvocation<?> inv) {
                snapshots.put(inv, new LinkedHashSet<>(killed));
                invocations.add(inv);
            } else if (element instanceof CtAssignment<?, ?> assign) {
                assignments.add(assign);
                if (assign.getAssignment() instanceof CtInvocation<?>
                        && assign.getAssigned() instanceof CtVariableWrite<?> vw) {
                    killed.add(vw.getVariable().getSimpleName());
                }
            } else if (element instanceof CtFieldRead<?> read) {
                fieldReads.add(read);
            } else if (element instanceof CtLocalVariable<?> lv
                    && lv.getDefaultExpression() instanceof CtInvocation<?>) {
                killed.add(lv.getSimpleName());
            }
        }
        return new MethodScan(invocations, snapshots, assignments, fieldReads);
    }

    /**
     * Extracts call edges from the supplied Spoon model and appends them to
     * {@code model.callEdges}.
     *
     * @param ctModel Spoon model for a single Maven module
     * @param model   architecture model to update
     */
    public void extract(CtModel ctModel, ArchitectureModel model) {
        Span span = tracer().spanBuilder("callgraph.extract").startSpan();
        try (Scope scope = span.makeCurrent()) {
            ExtractionContext ctx = new ExtractionContext(ComponentIndex.build(model.components));
            model.callEdges.forEach(edge -> ctx.addSeenId(edge.id));
            model.outboundSinkSites.forEach(site -> ctx.addSeenId(site.id));
            model.fieldAccesses.forEach(access -> ctx.addSeenId(access.id));
            Map<String, List<String>> entrypointParams = buildEntrypointParamMap(model);

            for (CtType<?> type : ctModel.getAllTypes()) {
                String fromId = "comp:" + type.getQualifiedName();
                Component fromComp = ctx.components.get(fromId);
                if (fromComp == null) continue;

                Map<String, Component> fieldToComp = buildFieldMap(type, fromId, ctx);
                Set<String> sharedStateFields = ctx.sharedStateFieldsFor(type, this::buildSharedStateFieldSet);

                Set<String> ownMethodNames =
                        type.getMethods().stream().map(CtMethod::getSimpleName).collect(Collectors.toSet());

                for (CtMethod<?> method : type.getMethods()) {
                    MethodScan scan = scanMethod(method);
                    extractFromMethod(scan, method, fromComp, fieldToComp, model, ctx);
                    extractIntraComponentCalls(scan, method, fromComp, ownMethodNames, model, ctx);
                    extractFieldAccesses(scan, method, fromComp, sharedStateFields, model, ctx);
                    extractOutboundSinkSites(scan, method, fromComp, model, ctx);
                    enrichEntrypointParameters(method, fromId, entrypointParams, model);
                }
            }
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    private Set<String> buildSharedStateFieldSet(CtType<?> type) {
        Set<String> names = new HashSet<>();
        for (CtField<?> field : type.getFields()) {
            CtTypeReference<?> t = field.getType();
            if (t == null) continue;
            String simple = t.getSimpleName();
            String fieldName = field.getSimpleName();
            if (isSharedStateDenylisted(simple)) continue;
            if (SHARED_STATE_SIMPLE_TYPES.contains(simple)) {
                names.add(fieldName);
                continue;
            }
            for (String suffix : SHARED_STATE_NAME_SUFFIXES) {
                if (simple.endsWith(suffix) || fieldName.endsWith(lowerFirst(suffix))) {
                    names.add(fieldName);
                    break;
                }
            }
        }
        return names;
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static boolean isSharedStateDenylisted(String simpleTypeName) {
        if (simpleTypeName == null) return false;
        if (SHARED_STATE_TYPE_DENYLIST.contains(simpleTypeName)) return true;
        for (String prefix : SHARED_STATE_TYPE_DENYLIST_PREFIXES) {
            if (simpleTypeName.startsWith(prefix)) return true;
        }
        return false;
    }

    private void extractFieldAccesses(
            MethodScan scan,
            CtMethod<?> method,
            Component fromComp,
            Set<String> sharedStateFields,
            ArchitectureModel model,
            ExtractionContext ctx) {
        extractAccessorChainFieldAccesses(scan, method, fromComp, model, ctx);
        if (sharedStateFields.isEmpty()) return;
        String methodName = method.getSimpleName();

        for (CtAssignment<?, ?> assign : scan.assignments()) {
            CtExpression<?> assigned = assign.getAssigned();
            if (!(assigned instanceof CtFieldWrite<?> fw)) continue;
            String fieldName = fw.getVariable().getSimpleName();
            if (!sharedStateFields.contains(fieldName)) continue;
            CtExpression<?> rhs = assign.getAssignment();
            String srcVar =
                    (rhs instanceof CtVariableRead<?> vr) ? vr.getVariable().getSimpleName() : null;
            String srcField =
                    (rhs instanceof CtFieldRead<?> fr) ? fr.getVariable().getSimpleName() : null;
            model.fieldAccesses.add(buildAccess(
                    FieldAccess.Kind.WRITE, fromComp, methodName, fieldName, srcVar, srcField, assign.getPosition()));
        }

        for (CtInvocation<?> inv : scan.invocations()) {
            if (!(inv.getTarget() instanceof CtFieldAccess<?> fa)) continue;
            String fieldName = fa.getVariable().getSimpleName();
            if (!sharedStateFields.contains(fieldName)) continue;
            String invName = inv.getExecutable().getSimpleName();
            if (WRITE_METHODS.contains(invName)) {
                String srcVar = inv.getArguments().stream()
                        .filter(a -> a instanceof CtVariableRead<?>)
                        .map(a -> ((CtVariableRead<?>) a).getVariable().getSimpleName())
                        .findFirst()
                        .orElse(null);
                String srcField = inv.getArguments().stream()
                        .filter(a -> a instanceof CtFieldRead<?>)
                        .map(a -> ((CtFieldRead<?>) a).getVariable().getSimpleName())
                        .findFirst()
                        .orElse(null);
                model.fieldAccesses.add(buildAccess(
                        FieldAccess.Kind.WRITE, fromComp, methodName, fieldName, srcVar, srcField, inv.getPosition()));
            } else {
                model.fieldAccesses.add(buildAccess(
                        FieldAccess.Kind.READ, fromComp, methodName, fieldName, null, null, inv.getPosition()));
            }
        }

        for (CtFieldRead<?> read : scan.fieldReads()) {
            String fieldName = read.getVariable().getSimpleName();
            if (!sharedStateFields.contains(fieldName)) continue;
            if (read.getParent() instanceof CtInvocation<?> inv && inv.getTarget() == read) continue;
            if (read.getParent() instanceof CtFieldAccess<?> parentFa && parentFa.getTarget() == read) continue;
            model.fieldAccesses.add(buildAccess(
                    FieldAccess.Kind.READ, fromComp, methodName, fieldName, null, null, read.getPosition()));
        }
    }

    private FieldAccess buildAccess(
            FieldAccess.Kind kind,
            Component owner,
            String method,
            String fieldName,
            String sourceVar,
            String sourceField,
            spoon.reflect.cu.SourcePosition pos) {
        FieldAccess fa = new FieldAccess();
        fa.kind = kind;
        fa.componentId = owner.id;
        fa.fieldOwnerComponentId = owner.id;
        fa.fieldName = fieldName;
        fa.method = method;
        fa.sourceVarName = sourceVar;
        fa.sourceFieldName = sourceField;
        fa.id = "field:" + owner.id + "#" + method + "@" + fieldName + ":"
                + kind.name().toLowerCase();
        String file = (pos != null && pos.isValidPosition()) ? pos.getFile().getAbsolutePath() : "unknown";
        int line = (pos != null && pos.isValidPosition()) ? pos.getLine() : 0;
        fa.source = new SourceInfo(file, line, "field-access", 0.9);
        return fa;
    }

    private void extractAccessorChainFieldAccesses(
            MethodScan scan, CtMethod<?> method, Component fromComp, ArchitectureModel model, ExtractionContext ctx) {
        String methodName = method.getSimpleName();
        for (CtInvocation<?> inv : scan.invocations()) {
            String terminalMethod = inv.getExecutable().getSimpleName();
            FieldAccess.Kind kind;
            if (WRITE_METHODS.contains(terminalMethod)) {
                kind = FieldAccess.Kind.WRITE;
            } else if (READ_METHODS.contains(terminalMethod)) {
                kind = FieldAccess.Kind.READ;
            } else {
                continue;
            }
            if (!(inv.getTarget() instanceof CtInvocation<?> accessor)) {
                continue;
            }
            String fieldName = accessorReturnedSharedFieldName(accessor, ctx);
            if (fieldName == null) {
                continue;
            }
            for (ReceiverTarget target : objectFlowIndex.resolveReceiver(inv)) {
                Component owner = ctx.components.get(target.componentId());
                if (owner == null) {
                    continue;
                }
                FieldAccess access = buildAccessorAccess(kind, fromComp, methodName, owner, fieldName, inv);
                if (ctx.addSeenId(access.id)) {
                    model.fieldAccesses.add(access);
                }
            }
        }
    }

    private FieldAccess buildAccessorAccess(
            FieldAccess.Kind kind,
            Component fromComp,
            String methodName,
            Component fieldOwner,
            String fieldName,
            CtInvocation<?> invocation) {
        FieldAccess access = new FieldAccess();
        access.kind = kind;
        access.componentId = fromComp.id;
        access.fieldOwnerComponentId = fieldOwner.id;
        access.fieldName = fieldName;
        access.method = methodName;
        access.id = "field:" + fromComp.id + "#" + methodName + "@" + fieldOwner.id + "#" + fieldName + ":"
                + kind.name().toLowerCase() + ":object-flow";
        var pos = invocation.getPosition();
        String file = (pos != null && pos.isValidPosition()) ? pos.getFile().getAbsolutePath() : "unknown";
        int line = (pos != null && pos.isValidPosition()) ? pos.getLine() : 0;
        access.source = new SourceInfo(file, line, "field-access-via-object-flow", 0.82);
        return access;
    }

    private String accessorReturnedSharedFieldName(CtInvocation<?> accessor, ExtractionContext ctx) {
        var executable = accessor.getExecutable().getDeclaration();
        if (!(executable instanceof CtMethod<?> accessorMethod)) {
            return null;
        }
        CtType<?> owner = accessorMethod.getDeclaringType();
        if (owner == null) {
            return null;
        }
        Set<String> sharedStateFields = ctx.sharedStateFieldsFor(owner, this::buildSharedStateFieldSet);
        for (CtReturn<?> ret : accessorMethod.getElements(new TypeFilter<>(CtReturn.class))) {
            String fieldName = returnedSharedFieldName(ret.getReturnedExpression(), sharedStateFields);
            if (fieldName != null) {
                return fieldName;
            }
        }
        return null;
    }

    /**
     * Emits same-component call edges for method calls on implicit/explicit {@code this}
     * whose callee is declared in the same type. This lets the DataFlowTracer follow
     * chains like {@code dispatchAll → buildAndSend → BrokerClient.publish} even when
     * the intermediate hop is a private method that the field-read filter would skip.
     */
    private void extractIntraComponentCalls(
            MethodScan scan,
            CtMethod<?> method,
            Component fromComp,
            Set<String> ownMethodNames,
            ArchitectureModel model,
            ExtractionContext ctx) {
        String fromMethod = method.getSimpleName();
        for (CtInvocation<?> inv : scan.invocations()) {
            if (inv.getTarget() instanceof CtFieldRead<?>) continue; // cross-component, handled elsewhere
            if (inv.getTarget() instanceof CtTypeAccess<?>) continue; // static call
            String toMethod = inv.getExecutable().getSimpleName();
            if (!ownMethodNames.contains(toMethod)) continue;
            if (toMethod.equals(fromMethod)) continue; // ignore trivial self-call
            String edgeId = "call:" + fromComp.id + "#" + fromMethod + "->" + fromComp.id + "#" + toMethod;
            if (!ctx.addSeenId(edgeId)) continue;
            CallEdge edge = new CallEdge();
            edge.id = edgeId;
            edge.fromComponentId = fromComp.id;
            edge.fromMethod = fromMethod;
            edge.toComponentId = fromComp.id;
            edge.toMethod = toMethod;
            edge.callKind = "intra";
            edge.source = buildSource(inv);
            model.callEdges.add(edge);
        }
    }

    private Map<String, Component> buildFieldMap(CtType<?> type, String ownId, ExtractionContext ctx) {
        Map<String, Component> map = new HashMap<>();
        if (sourceFacts != null) {
            for (SourceInjectionPoint injection : sourceFacts.injectionPoints(SourceFactIndexBuilder.typeId(type.getQualifiedName()))) {
                if (injection.fieldName() == null || injection.targetType() == null) continue;
                Component target = resolveSourceFactType(injection.targetType(), ownId, ctx);
                if (target != null && !target.id.equals(ownId)) {
                    map.put(injection.fieldName(), target);
                }
            }
        }
        for (CtField<?> field : type.getFields()) {
            if (field.getType() == null) continue;
            Component target = sourceFacts == null
                    ? ctx.components.find(field.getType().getQualifiedName(), field.getType().getSimpleName())
                    : resolveSourceFactType(field.getType().getQualifiedName(), ownId, ctx);
            if (target != null && !target.id.equals(ownId)) {
                map.put(field.getSimpleName(), target);
            }
        }
        return map;
    }

    private Component resolveSourceFactType(String qualifiedName, String ownId, ExtractionContext ctx) {
        Component direct = ctx.components.find(qualifiedName, simpleName(qualifiedName));
        if (direct != null && !direct.id.equals(ownId)) {
            return direct;
        }
        if (sourceFacts == null) {
            return null;
        }

        List<Component> implementationComponents = sourceFacts.implementations(qualifiedName).stream()
                .map(SourceType::qualifiedName)
                .map(implementation -> ctx.components.find(implementation, simpleName(implementation)))
                .filter(Objects::nonNull)
                .filter(component -> !component.id.equals(ownId))
                .distinct()
                .toList();
        return implementationComponents.size() == 1 ? implementationComponents.get(0) : null;
    }

    private static String simpleName(String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        return dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1);
    }

    private void extractFromMethod(
            MethodScan scan,
            CtMethod<?> method,
            Component fromComp,
            Map<String, Component> fieldToComp,
            ArchitectureModel model,
            ExtractionContext ctx) {
        String fromMethod = method.getSimpleName();

        for (CtInvocation<?> inv : scan.invocations()) {
            List<ReceiverTarget> receiverTargets = objectFlowIndex.resolveReceiver(inv);
            if (!receiverTargets.isEmpty()) {
                for (ReceiverTarget target : receiverTargets) {
                    Component toComp = ctx.components.get(target.componentId());
                    if (toComp == null || toComp.id.equals(fromComp.id)) continue;
                    emitCallEdge(
                            inv,
                            fromComp,
                            fromMethod,
                            toComp,
                            target.methodName(),
                            target.evidence().name().toLowerCase(Locale.ROOT).replace('_', '-'),
                            target.confidence(),
                            target.expansionCapped(),
                            model,
                            ctx,
                            scan.killedSnapshots().get(inv));
                }
                continue;
            }
            if (!(inv.getTarget() instanceof CtFieldRead<?> fieldRead)) continue;

            String fieldName = fieldRead.getVariable().getSimpleName();
            Component toComp = fieldToComp.get(fieldName);
            if (toComp == null) continue;

            String toMethod = inv.getExecutable().getSimpleName();
            String callKind = resolveCallKind(fieldRead);
            emitCallEdge(
                    inv,
                    fromComp,
                    fromMethod,
                    toComp,
                    toMethod,
                    "legacy-field-read",
                    0.85,
                    false,
                    model,
                    ctx,
                    scan.killedSnapshots().get(inv),
                    callKind);
        }
    }

    private void emitCallEdge(
            CtInvocation<?> inv,
            Component fromComp,
            String fromMethod,
            Component toComp,
            String toMethod,
            String receiverEvidence,
            double receiverConfidence,
            boolean receiverExpansionCapped,
            ArchitectureModel model,
            ExtractionContext ctx,
            Set<String> killedSnapshot) {
        emitCallEdge(
                inv,
                fromComp,
                fromMethod,
                toComp,
                toMethod,
                receiverEvidence,
                receiverConfidence,
                receiverExpansionCapped,
                model,
                ctx,
                killedSnapshot,
                "direct");
    }

    private void emitCallEdge(
            CtInvocation<?> inv,
            Component fromComp,
            String fromMethod,
            Component toComp,
            String toMethod,
            String receiverEvidence,
            double receiverConfidence,
            boolean receiverExpansionCapped,
            ArchitectureModel model,
            ExtractionContext ctx,
            Set<String> killedSnapshot,
            String callKind) {
        String edgeId = "call:" + fromComp.id + "#" + fromMethod + "->" + toComp.id + "#" + toMethod;
        if (!ctx.addSeenId(edgeId)) return;
        CallEdge edge = new CallEdge();
        edge.id = edgeId;
        edge.fromComponentId = fromComp.id;
        edge.fromMethod = fromMethod;
        edge.toComponentId = toComp.id;
        edge.toMethod = toMethod;
        edge.callKind = callKind;
        edge.source = buildSource(inv);
        edge.receiverEvidence = receiverEvidence;
        edge.receiverLocalName = resolveReceiverLocalName(inv);
        edge.receiverConfidence = receiverConfidence;
        edge.ambiguous = "accessor-name-fallback".equals(receiverEvidence);
        edge.receiverExpansionCapped = receiverExpansionCapped;
        buildParamMapping(inv, edge);
        edge.assignedToVar = resolveAssignedToVar(inv);
        edge.returnsTracked = calleeReturnsTracked(inv, ctx);
        if (killedSnapshot != null) edge.killedTrackedNames.addAll(killedSnapshot);
        model.callEdges.add(edge);
        emitCallerSideFieldReadIfGetter(inv, fromComp, fromMethod, toComp, model, ctx);
    }

    private String resolveReceiverLocalName(CtInvocation<?> inv) {
        return receiverLocalName(inv.getTarget());
    }

    private String receiverLocalName(CtExpression<?> expression) {
        if (expression instanceof CtVariableRead<?> read && read.getVariable() != null) {
            return read.getVariable().getSimpleName();
        }
        if (expression instanceof CtFieldRead<?> read && read.getVariable() != null) {
            return read.getVariable().getSimpleName();
        }
        if (expression instanceof CtInvocation<?> invocation) {
            return receiverLocalName(invocation.getTarget());
        }
        return null;
    }

    private String resolveAssignedToVar(CtInvocation<?> inv) {
        var parent = inv.getParent();
        if (parent instanceof CtLocalVariable<?> lv) {
            return lv.getSimpleName();
        }
        if (parent instanceof CtAssignment<?, ?> assign && assign.getAssigned() instanceof CtVariableWrite<?> vw) {
            return vw.getVariable().getSimpleName();
        }
        return null;
    }

    private void extractOutboundSinkSites(
            MethodScan scan, CtMethod<?> method, Component fromComp, ArchitectureModel model, ExtractionContext ctx) {
        String methodName = method.getSimpleName();
        int index = 0;
        for (CtInvocation<?> inv : scan.invocations()) {
            var declaringType = inv.getExecutable().getDeclaringType();
            if (declaringType == null) continue;
            String qn = declaringType.getQualifiedName();
            if (qn == null || qn.isEmpty()) continue;

            DataFlowSink.Kind kind = classifyOutboundCallee(qn);
            String channel = null;
            if (kind == null) {
                String[] kindAndChannel = classifyMessagingFieldTarget(inv);
                if (kindAndChannel != null) {
                    kind = kindAndChannel[0].equals("messaging")
                            ? DataFlowSink.Kind.MESSAGING
                            : DataFlowSink.Kind.EVENT_BUS;
                    channel = kindAndChannel[1];
                }
            }
            if (kind == null) continue;

            String id = "outbound:" + fromComp.id + "#" + methodName + ":" + (index++);
            if (!ctx.addSeenId(id)) continue;

            OutboundSinkSite site = new OutboundSinkSite();
            site.id = id;
            site.kind = kind;
            site.channel = channel;
            site.componentId = fromComp.id;
            site.method = methodName;
            site.calleeQualifiedName = qn;
            site.calleeMethod = inv.getExecutable().getSimpleName();
            var pos = inv.getPosition();
            String file = (pos != null && pos.isValidPosition()) ? pos.getFile().getAbsolutePath() : "unknown";
            int line = (pos != null && pos.isValidPosition()) ? pos.getLine() : 0;
            site.source = new SourceInfo(file, line, "invocation", 0.85);
            model.outboundSinkSites.add(site);
        }
    }

    /** Returns [kindString, channelOrNull] when the invocation target is an Emitter/EventBus field, else null. */
    private static String[] classifyMessagingFieldTarget(CtInvocation<?> inv) {
        if (!(inv.getTarget() instanceof CtFieldRead<?> fr)) return null;
        var fieldType = fr.getVariable().getType();
        if (fieldType == null) return null;
        String simple = fieldType.getSimpleName();
        String kindStr;
        if (EMITTER_TYPES.contains(simple)) kindStr = "messaging";
        else if (EVENT_BUS_TYPES.contains(simple)) kindStr = "event-bus";
        else return null;
        String channel = extractChannelAnnotation(fr);
        return new String[] {kindStr, channel};
    }

    private static String extractChannelAnnotation(CtFieldRead<?> fr) {
        var fieldDecl = fr.getVariable().getFieldDeclaration();
        if (fieldDecl == null) return null;
        for (var ann : fieldDecl.getAnnotations()) {
            var annType = ann.getAnnotationType();
            if (annType == null) continue;
            if (!"Channel".equals(annType.getSimpleName())) continue;
            var val = ann.getValues().get("value");
            if (val != null) return val.toString().replaceAll("^\"|\"$", "");
        }
        return null;
    }

    private static DataFlowSink.Kind classifyOutboundCallee(String calleeQualifiedName) {
        for (String prefix : FILE_OUTBOUND_PREFIXES) {
            if (calleeQualifiedName.equals(prefix) || calleeQualifiedName.startsWith(prefix + ".")) {
                return DataFlowSink.Kind.FILE_OUTBOUND;
            }
        }
        for (String prefix : OBJECT_STORAGE_PREFIXES) {
            if (calleeQualifiedName.equals(prefix) || calleeQualifiedName.startsWith(prefix + ".")) {
                return DataFlowSink.Kind.OBJECT_STORAGE;
            }
        }
        return null;
    }

    private boolean calleeReturnsTracked(CtInvocation<?> inv, ExtractionContext ctx) {
        var executable = inv.getExecutable().getDeclaration();
        if (!(executable instanceof CtMethod<?> calleeMethod)) return false;
        if (calleeMethod.getBody() == null) return false;
        Set<String> paramNames = calleeMethod.getParameters().stream()
                .map(CtParameter::getSimpleName)
                .collect(Collectors.toSet());
        CtType<?> calleeType = calleeMethod.getDeclaringType();
        Set<String> calleeSharedState =
                calleeType != null ? ctx.sharedStateFieldsFor(calleeType, this::buildSharedStateFieldSet) : Set.of();
        for (CtReturn<?> ret : calleeMethod.getElements(new TypeFilter<>(CtReturn.class))) {
            CtExpression<?> ex = ret.getReturnedExpression();
            if (ex == null) continue;
            if (returnedSharedFieldName(ex, calleeSharedState) != null) return true;
            String name = findFirstVarRead(ex);
            if (name != null && paramNames.contains(name)) return true;
        }
        return false;
    }

    private void emitCallerSideFieldReadIfGetter(
            CtInvocation<?> inv,
            Component fromComp,
            String fromMethod,
            Component toComp,
            ArchitectureModel model,
            ExtractionContext ctx) {
        var executable = inv.getExecutable().getDeclaration();
        if (!(executable instanceof CtMethod<?> calleeMethod)) return;
        if (calleeMethod.getBody() == null) return;
        CtType<?> calleeType = calleeMethod.getDeclaringType();
        if (calleeType == null) return;
        Set<String> calleeSharedState = ctx.sharedStateFieldsFor(calleeType, this::buildSharedStateFieldSet);
        if (calleeSharedState.isEmpty()) return;
        for (CtReturn<?> ret : calleeMethod.getElements(new TypeFilter<>(CtReturn.class))) {
            CtExpression<?> ex = ret.getReturnedExpression();
            String fieldName = returnedSharedFieldName(ex, calleeSharedState);
            if (fieldName == null) continue;
            FieldAccess fa = new FieldAccess();
            fa.kind = FieldAccess.Kind.READ;
            fa.componentId = fromComp.id;
            fa.fieldOwnerComponentId = toComp.id;
            fa.fieldName = fieldName;
            fa.method = fromMethod;
            fa.id = "field:" + fromComp.id + "#" + fromMethod + "@" + toComp.id + "#" + calleeMethod.getSimpleName()
                    + ":" + fieldName + ":read:xcomp";
            var pos = inv.getPosition();
            String file = (pos != null && pos.isValidPosition()) ? pos.getFile().getAbsolutePath() : "unknown";
            int line = (pos != null && pos.isValidPosition()) ? pos.getLine() : 0;
            fa.source = new SourceInfo(file, line, "field-access-via-getter", 0.85);
            model.fieldAccesses.add(fa);
            return;
        }
    }

    private String returnedSharedFieldName(CtExpression<?> expression, Set<String> sharedStateFields) {
        if (expression == null || sharedStateFields.isEmpty()) {
            return null;
        }
        if (expression instanceof CtFieldRead<?> direct) {
            String fieldName = direct.getVariable().getSimpleName();
            return sharedStateFields.contains(fieldName) ? fieldName : null;
        }
        for (CtFieldRead<?> read : expression.getElements(new TypeFilter<>(CtFieldRead.class))) {
            String fieldName = read.getVariable().getSimpleName();
            if (sharedStateFields.contains(fieldName)) {
                return fieldName;
            }
        }
        return null;
    }

    private void buildParamMapping(CtInvocation<?> inv, CallEdge edge) {
        var executable = inv.getExecutable().getDeclaration();
        if (executable == null) return;
        List<CtParameter<?>> calleeParams = executable.getParameters();
        var args = inv.getArguments();
        for (int i = 0; i < args.size() && i < calleeParams.size(); i++) {
            String calleeParam = calleeParams.get(i).getSimpleName();
            CtExpression<?> arg = args.get(i);
            if (arg instanceof CtVariableRead<?> direct) {
                edge.paramMapping.put(direct.getVariable().getSimpleName(), calleeParam);
                String literal = resolveArgToLiteral(arg);
                if (literal != null) {
                    edge.resolvedLiteralArgs.put(calleeParam, literal);
                }
                continue;
            }
            String literal = resolveArgToLiteral(arg);
            if (literal != null) {
                edge.resolvedLiteralArgs.put(calleeParam, literal);
                continue;
            }
            String synthesised = findFirstVarRead(arg);
            if (synthesised != null) {
                edge.paramMapping.put(synthesised, calleeParam);
                edge.syntheticParamMappings.add(calleeParam);
            }
        }
    }

    private static String resolveArgToLiteral(CtExpression<?> arg) {
        if (arg instanceof CtLiteral<?> lit) {
            return lit.getValue() == null ? "" : lit.getValue().toString();
        }
        if (arg instanceof CtVariableRead<?> read && read.getVariable() instanceof CtFieldReference<?> ref) {
            try {
                CtField<?> decl = ref.getDeclaration();
                if (decl != null && decl.getDefaultExpression() instanceof CtLiteral<?> lit) {
                    return lit.getValue() == null ? "" : lit.getValue().toString();
                }
            } catch (Exception ignored) {}
        }
        if (arg instanceof CtVariableRead<?> read) {
            try {
                if (read.getVariable().getDeclaration() instanceof CtLocalVariable<?> local
                        && local.getDefaultExpression() instanceof CtLiteral<?> lit) {
                    return lit.getValue() == null ? "" : lit.getValue().toString();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String findFirstVarRead(CtExpression<?> expr) {
        if (expr == null) return null;
        if (expr instanceof CtVariableRead<?> vr) return vr.getVariable().getSimpleName();
        if (expr instanceof CtConditional<?> cond) {
            String t = findFirstVarRead(cond.getThenExpression());
            return t != null ? t : findFirstVarRead(cond.getElseExpression());
        }
        if (expr instanceof CtConstructorCall<?> ctor) {
            for (CtExpression<?> a : ctor.getArguments()) {
                String r = findFirstVarRead(a);
                if (r != null) return r;
            }
            return null;
        }
        if (expr instanceof CtInvocation<?> inv) {
            CtExpression<?> target = inv.getTarget();
            if (target != null) {
                String r = findFirstVarRead(target);
                if (r != null) return r;
            }
            for (CtExpression<?> a : inv.getArguments()) {
                String r = findFirstVarRead(a);
                if (r != null) return r;
            }
        }
        return null;
    }

    private Map<String, List<String>> buildEntrypointParamMap(ArchitectureModel model) {
        Map<String, List<String>> map = new HashMap<>();
        for (Entrypoint ep : model.entrypoints) {
            map.computeIfAbsent(ep.componentId + "#" + ep.name, k -> new ArrayList<>());
        }
        return map;
    }

    private void enrichEntrypointParameters(
            CtMethod<?> method, String compId, Map<String, List<String>> entrypointParams, ArchitectureModel model) {
        String key = compId + "#" + method.getSimpleName();
        if (!entrypointParams.containsKey(key)) return;
        List<String> names =
                method.getParameters().stream().map(CtParameter::getSimpleName).collect(Collectors.toList());
        model.entrypoints.stream()
                .filter(ep -> ep.componentId.equals(compId) && ep.name.equals(method.getSimpleName()))
                .filter(ep -> ep.parameters.isEmpty())
                .forEach(ep -> ep.parameters.addAll(names));
    }

    private String resolveCallKind(CtFieldRead<?> fieldRead) {
        if (fieldRead.getType() == null) return "direct";
        String simple = fieldRead.getType().getSimpleName();
        if (EVENT_BUS_TYPES.contains(simple)) return "event-bus";
        if (EMITTER_TYPES.contains(simple)) return "messaging";
        return "direct";
    }

    private SourceInfo buildSource(CtInvocation<?> inv) {
        var pos = inv.getPosition();
        String file = pos.isValidPosition() ? pos.getFile().getAbsolutePath() : "unknown";
        int line = pos.isValidPosition() ? pos.getLine() : 0;
        return new SourceInfo(file, line, "invocation", 0.95);
    }
}
