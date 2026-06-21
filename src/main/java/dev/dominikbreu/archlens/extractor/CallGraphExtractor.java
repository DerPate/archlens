package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.extractor.objectflow.ObjectFlowIndex;
import dev.dominikbreu.archlens.extractor.objectflow.ReceiverTarget;
import dev.dominikbreu.archlens.extractor.sourcefacts.SourceFactIndex;
import dev.dominikbreu.archlens.extractor.sourcefacts.SourceFactIndexBuilder;
import dev.dominikbreu.archlens.extractor.sourcefacts.SourceInjectionPoint;
import dev.dominikbreu.archlens.extractor.sourcefacts.SourceType;
import dev.dominikbreu.archlens.model.*;
import dev.dominikbreu.archlens.model.ids.FieldAccessId;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import java.util.*;
import java.util.stream.Collectors;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtAbstractSwitch;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCase;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtConditional;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtTry;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
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

    private static final String FIELD_PREFIX = "field:";

    private static final String UNKNOWN = "unknown";
    private static final String DIRECT = "direct";
    private static final String MESSAGING = "messaging";

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

    private static final Set<String> READ_METHODS =
            Set.of("get", "containsKey", "containsValue", "values", "keySet", "entrySet", "size", "isEmpty");

    private final ObjectFlowIndex objectFlowIndex;
    private final SourceFactIndex sourceFacts;

    /** Creates a call graph extractor using default resolution rules. */
    public CallGraphExtractor() {
        this(ObjectFlowIndex.empty(), null);
    }

    /**
     * Creates an extractor with the given object-flow index and no source-fact index.
     *
     * @param objectFlowIndex the object-flow index for receiver resolution
     */
    public CallGraphExtractor(ObjectFlowIndex objectFlowIndex) {
        this(objectFlowIndex, null);
    }

    /**
     * Creates an extractor with explicit object-flow and source-fact indices.
     *
     * @param objectFlowIndex the object-flow index for receiver resolution
     * @param sourceFacts the source-fact index, or {@code null} to skip source-fact lookups
     */
    public CallGraphExtractor(ObjectFlowIndex objectFlowIndex, SourceFactIndex sourceFacts) {
        this.objectFlowIndex = objectFlowIndex == null ? ObjectFlowIndex.empty() : objectFlowIndex;
        this.sourceFacts = sourceFacts;
    }

    private static Tracer tracer() {
        return GlobalOpenTelemetry.getTracer("dev.dominikbreu.archlens");
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

    private record BranchContext(
            CallEdge.ControlFlowKind kind,
            String branchGroupId,
            String branchArmId,
            String branchLabel,
            SourceInfo controlSource) {}

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
        try (var _ = span.makeCurrent()) {
            ExtractionContext ctx = new ExtractionContext(ComponentIndex.build(model.components));
            model.callEdges.forEach(edge -> ctx.addSeenId(edge.id));
            model.outboundSinkSites.forEach(site -> ctx.addSeenId(site.id));
            model.fieldAccesses.forEach(access -> ctx.addSeenId(access.id.serialize()));
            Map<String, List<String>> entrypointParams = buildEntrypointParamMap(model);

            for (CtType<?> type : ctModel.getAllTypes()) {
                dev.dominikbreu.archlens.model.ids.ComponentId fromId =
                        dev.dominikbreu.archlens.model.ids.ComponentId.of(type.getQualifiedName());
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
        if (s.isEmpty()) {
            return s;
        } else {
            return Character.toLowerCase(s.charAt(0)) + s.substring(1);
        }
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
        extractAssignmentFieldWrites(scan, methodName, fromComp, sharedStateFields, model);
        extractInvocationFieldAccesses(scan, methodName, fromComp, sharedStateFields, model);
        extractFieldReadAccesses(scan, methodName, fromComp, sharedStateFields, model);
    }

    private void extractAssignmentFieldWrites(
            MethodScan scan,
            String methodName,
            Component fromComp,
            Set<String> sharedStateFields,
            ArchitectureModel model) {
        for (CtAssignment<?, ?> assign : scan.assignments()) {
            CtExpression<?> assigned = assign.getAssigned();
            if (!(assigned instanceof CtFieldWrite<?> fw)) continue;
            String fieldName = fw.getVariable().getSimpleName();
            if (!sharedStateFields.contains(fieldName)) continue;
            CtExpression<?> rhs = assign.getAssignment();
            String srcVar =
                    rhs instanceof CtVariableRead<?> vr ? vr.getVariable().getSimpleName() : null;
            String srcField =
                    rhs instanceof CtFieldRead<?> fr ? fr.getVariable().getSimpleName() : null;
            model.fieldAccesses.add(buildAccess(
                    FieldAccess.Kind.WRITE, fromComp, methodName, fieldName, srcVar, srcField, assign.getPosition()));
        }
    }

    private void extractInvocationFieldAccesses(
            MethodScan scan,
            String methodName,
            Component fromComp,
            Set<String> sharedStateFields,
            ArchitectureModel model) {
        for (CtInvocation<?> inv : scan.invocations()) {
            if (!(inv.getTarget() instanceof CtFieldAccess<?> fa)) continue;
            String fieldName = fa.getVariable().getSimpleName();
            if (!sharedStateFields.contains(fieldName)) continue;
            String invName = inv.getExecutable().getSimpleName();
            if (WRITE_METHODS.contains(invName)) {
                // Use the last variable/field-read argument as the stored-value source.
                // For put(key, value), set(index, value), etc. the stored value is always
                // the last argument; findFirst() was incorrectly returning the key argument.
                String srcVar = lastVarReadName(inv.getArguments());
                String srcField = lastFieldReadName(inv.getArguments());
                String keyVar = firstVarReadName(inv.getArguments());
                model.fieldAccesses.add(buildAccess(
                        FieldAccess.Kind.WRITE,
                        fromComp,
                        methodName,
                        fieldName,
                        srcVar,
                        srcField,
                        keyVar,
                        inv.getPosition()));
            } else {
                model.fieldAccesses.add(buildAccess(
                        FieldAccess.Kind.READ, fromComp, methodName, fieldName, null, null, inv.getPosition()));
            }
        }
    }

    private void extractFieldReadAccesses(
            MethodScan scan,
            String methodName,
            Component fromComp,
            Set<String> sharedStateFields,
            ArchitectureModel model) {
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
        return buildAccess(kind, owner, method, fieldName, sourceVar, sourceField, null, pos);
    }

    private FieldAccess buildAccess(
            FieldAccess.Kind kind,
            Component owner,
            String method,
            String fieldName,
            String sourceVar,
            String sourceField,
            String keyVar,
            spoon.reflect.cu.SourcePosition pos) {
        FieldAccess fa = new FieldAccess();
        fa.kind = kind;
        fa.componentId = owner.id;
        fa.fieldBinding = new dev.dominikbreu.archlens.model.ids.FieldBinding.Own(fieldName);
        fa.method = method;
        fa.sourceVarName = sourceVar;
        fa.sourceFieldName = sourceField;
        fa.keyVarName = keyVar;
        fa.id = FieldAccessId.of(FIELD_PREFIX + owner.id.serialize() + "#" + method + "@" + fieldName + ":"
                + kind.name().toLowerCase());
        String file;
        if (pos != null && pos.isValidPosition()) {
            file = pos.getFile().getAbsolutePath();
        } else {
            file = UNKNOWN;
        }
        int line;
        if (pos != null && pos.isValidPosition()) {
            line = pos.getLine();
        } else {
            line = 0;
        }
        fa.source = new SourceInfo(file, line, "field-access", 0.9);
        return fa;
    }

    private static String firstVarReadName(List<spoon.reflect.code.CtExpression<?>> args) {
        // Returns the key variable name for keyed-write methods (put, set, merge, …).
        // Only meaningful when there are at least 2 arguments; single-argument writes
        // (add, offer, push, …) have no separate key position.
        if (args.size() < 2) return null;
        CtExpression<?> first = args.getFirst();
        if (first instanceof CtVariableRead<?> vr) {
            return vr.getVariable().getSimpleName();
        } else {
            return null;
        }
    }

    private static String lastVarReadName(List<spoon.reflect.code.CtExpression<?>> args) {
        if (args.isEmpty()) return null;
        // Only inspect the last argument (value position for put/set/add etc.).
        // Falling back to earlier arguments would confuse the key with the value
        // for calls like put(key, someInvocation()).
        CtExpression<?> last = args.getLast();
        if (last instanceof CtVariableRead<?> vr) {
            return vr.getVariable().getSimpleName();
        } else {
            return null;
        }
    }

    private static String lastFieldReadName(List<spoon.reflect.code.CtExpression<?>> args) {
        if (args.isEmpty()) return null;
        CtExpression<?> last = args.getLast();
        if (last instanceof spoon.reflect.code.CtFieldRead<?> fr) {
            return fr.getVariable().getSimpleName();
        } else {
            return null;
        }
    }

    private void extractAccessorChainFieldAccesses(
            MethodScan scan, CtMethod<?> method, Component fromComp, ArchitectureModel model, ExtractionContext ctx) {
        String methodName = method.getSimpleName();
        for (CtInvocation<?> inv : scan.invocations()) {
            FieldAccess.Kind kind = accessKind(inv.getExecutable().getSimpleName());
            if (kind == null) continue;
            if (!(inv.getTarget() instanceof CtInvocation<?> accessor)) continue;
            String fieldName = accessorReturnedSharedFieldName(accessor, ctx);
            if (fieldName == null) continue;
            recordAccessorAccesses(inv, kind, fromComp, methodName, fieldName, model, ctx);
        }
    }

    private static FieldAccess.Kind accessKind(String terminalMethod) {
        if (WRITE_METHODS.contains(terminalMethod)) return FieldAccess.Kind.WRITE;
        if (READ_METHODS.contains(terminalMethod)) return FieldAccess.Kind.READ;
        return null;
    }

    private void recordAccessorAccesses(
            CtInvocation<?> inv,
            FieldAccess.Kind kind,
            Component fromComp,
            String methodName,
            String fieldName,
            ArchitectureModel model,
            ExtractionContext ctx) {
        for (ReceiverTarget target : objectFlowIndex.resolveReceiver(inv)) {
            Component owner = ctx.components.get(
                    dev.dominikbreu.archlens.model.ids.ComponentId.deserialize(target.componentId()));
            if (owner == null) continue;
            FieldAccess access = buildAccessorAccess(kind, fromComp, methodName, owner, fieldName, inv);
            if (ctx.addSeenId(access.id.serialize())) {
                model.fieldAccesses.add(access);
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
        access.fieldBinding = new dev.dominikbreu.archlens.model.ids.FieldBinding.CrossComponent(
                new dev.dominikbreu.archlens.model.ids.FieldRef(fieldOwner.id, fieldName));
        access.method = methodName;
        access.id = FieldAccessId.of(
                FIELD_PREFIX + fromComp.id.serialize() + "#" + methodName + "@" + fieldOwner.id.serialize() + "#"
                        + fieldName + ":" + kind.name().toLowerCase() + ":object-flow");
        var pos = invocation.getPosition();
        access.source = new SourceInfo(sourceFileOf(pos), sourceLineOf(pos), "field-access-via-object-flow", 0.82);
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
            BranchContext branch = branchContext(inv);
            String edgeId = callEdgeId(fromComp, fromMethod, fromComp, toMethod, branch);
            if (!ctx.addSeenId(edgeId)) continue;
            CallEdge edge = new CallEdge();
            edge.id = edgeId;
            edge.fromComponentId = fromComp.id;
            edge.fromMethod = fromMethod;
            edge.toComponentId = fromComp.id;
            edge.toMethod = toMethod;
            edge.callKind = "intra";
            edge.source = buildSource(inv);
            applyBranchContext(edge, branch);
            model.callEdges.add(edge);
            buildParamMapping(inv, edge);
        }
    }

    private Map<String, Component> buildFieldMap(
            CtType<?> type, dev.dominikbreu.archlens.model.ids.ComponentId ownId, ExtractionContext ctx) {
        Map<String, Component> map = new HashMap<>();
        addInjectionFieldTargets(map, type, ownId, ctx);
        addDeclaredFieldTargets(map, type, ownId, ctx);
        return map;
    }

    private void addInjectionFieldTargets(
            Map<String, Component> map,
            CtType<?> type,
            dev.dominikbreu.archlens.model.ids.ComponentId ownId,
            ExtractionContext ctx) {
        if (sourceFacts == null) return;
        for (SourceInjectionPoint injection :
                sourceFacts.injectionPoints(SourceFactIndexBuilder.typeId(type.getQualifiedName()))) {
            if (injection.fieldName() == null || injection.targetType() == null) continue;
            Component target = resolveSourceFactType(injection.targetType(), ownId, ctx);
            if (target != null && !target.id.equals(ownId)) {
                map.put(injection.fieldName(), target);
            }
        }
    }

    private void addDeclaredFieldTargets(
            Map<String, Component> map,
            CtType<?> type,
            dev.dominikbreu.archlens.model.ids.ComponentId ownId,
            ExtractionContext ctx) {
        for (CtField<?> field : type.getFields()) {
            if (field.getType() == null) continue;
            Component target = sourceFacts == null
                    ? ctx.components.find(
                            field.getType().getQualifiedName(), field.getType().getSimpleName())
                    : resolveSourceFactType(field.getType().getQualifiedName(), ownId, ctx);
            if (target != null && !target.id.equals(ownId)) {
                map.put(field.getSimpleName(), target);
            }
        }
    }

    private Component resolveSourceFactType(
            String qualifiedName, dev.dominikbreu.archlens.model.ids.ComponentId ownId, ExtractionContext ctx) {
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
        if (implementationComponents.size() == 1) {
            return implementationComponents.getFirst();
        } else {
            return null;
        }
    }

    private static String simpleName(String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        if (dot < 0) {
            return qualifiedName;
        } else {
            return qualifiedName.substring(dot + 1);
        }
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
                    Component toComp = ctx.components.get(
                            dev.dominikbreu.archlens.model.ids.ComponentId.deserialize(target.componentId()));
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
                DIRECT);
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
        BranchContext branch = branchContext(inv);
        String edgeId = callEdgeId(fromComp, fromMethod, toComp, toMethod, branch);
        if (!ctx.addSeenId(edgeId)) return;
        CallEdge edge = new CallEdge();
        edge.id = edgeId;
        edge.fromComponentId = fromComp.id;
        edge.fromMethod = fromMethod;
        edge.toComponentId = toComp.id;
        edge.toMethod = toMethod;
        edge.callKind = callKind;
        edge.source = buildSource(inv);
        applyBranchContext(edge, branch);
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

    private static String callEdgeId(
            Component fromComp, String fromMethod, Component toComp, String toMethod, BranchContext branch) {
        String edgeId =
                "call:" + fromComp.id.serialize() + "#" + fromMethod + "->" + toComp.id.serialize() + "#" + toMethod;
        if (branch == null) {
            return edgeId;
        }
        return edgeId + "@arm:" + sanitizeIdSegment(branch.branchArmId());
    }

    private void applyBranchContext(CallEdge edge, BranchContext branch) {
        if (branch == null) return;
        edge.controlFlowKind = branch.kind();
        edge.branchGroupId = branch.branchGroupId();
        edge.branchArmId = branch.branchArmId();
        edge.branchLabel = branch.branchLabel();
        edge.controlSource = branch.controlSource();
    }

    private BranchContext branchContext(CtInvocation<?> invocation) {
        CtElement cursor = invocation;
        while (cursor != null) {
            CtElement parent = cursor.getParent();
            if (parent instanceof CtIf ctIf) {
                BranchContext context = ifBranchContext(invocation, ctIf);
                if (context != null) return context;
            } else if (parent instanceof CtConditional<?> conditional) {
                BranchContext context = ternaryBranchContext(invocation, conditional);
                if (context != null) return context;
            } else if (parent instanceof CtCase<?> ctCase) {
                return switchBranchContext(ctCase);
            } else if (parent instanceof CtCatch ctCatch) {
                return catchBranchContext(ctCatch);
            } else if (parent instanceof CtTry ctTry) {
                CtBlock<?> finalizer = ctTry.getFinalizer();
                if (isWithin(invocation, finalizer)) {
                    return finallyBranchContext(ctTry);
                }
            }
            cursor = parent;
        }
        return null;
    }

    private BranchContext ifBranchContext(CtInvocation<?> invocation, CtIf ctIf) {
        SourceInfo source = buildControlSource(ctIf);
        String groupId = branchId("if", ctIf);
        String cond = conditionLabel(ctIf.getCondition());
        if (isWithin(invocation, ctIf.getThenStatement())) {
            return new BranchContext(CallEdge.ControlFlowKind.IF_THEN, groupId, groupId + ":then", "if " + cond, source);
        }
        if (isWithin(invocation, ctIf.getElseStatement())) {
            return new BranchContext(CallEdge.ControlFlowKind.IF_ELSE, groupId, groupId + ":else", "else: !(" + cond + ")", source);
        }
        return null;
    }

    private BranchContext ternaryBranchContext(CtInvocation<?> invocation, CtConditional<?> conditional) {
        SourceInfo source = buildControlSource(conditional);
        String groupId = branchId("ternary", conditional);
        String cond = conditionLabel(conditional.getCondition());
        if (isWithin(invocation, conditional.getThenExpression())) {
            return new BranchContext(CallEdge.ControlFlowKind.TERNARY_THEN, groupId, groupId + ":then", "if " + cond, source);
        }
        if (isWithin(invocation, conditional.getElseExpression())) {
            return new BranchContext(CallEdge.ControlFlowKind.TERNARY_ELSE, groupId, groupId + ":else", "else: !(" + cond + ")", source);
        }
        return null;
    }

    private static String conditionLabel(CtExpression<?> condition) {
        if (condition == null) return "?";
        String text = condition.toString();
        return text.length() > 55 ? text.substring(0, 52) + "..." : text;
    }

    private BranchContext switchBranchContext(CtCase<?> ctCase) {
        CtAbstractSwitch<?> ctSwitch = parentOf(ctCase, CtAbstractSwitch.class);
        CtElement control = ctSwitch != null ? ctSwitch : ctCase;
        SourceInfo source = buildControlSource(control);
        String groupId = branchId("switch", control);
        String label = caseLabel(ctCase);
        CallEdge.ControlFlowKind kind = "default".equals(label)
                ? CallEdge.ControlFlowKind.SWITCH_DEFAULT
                : CallEdge.ControlFlowKind.SWITCH_CASE;
        String armKind = kind == CallEdge.ControlFlowKind.SWITCH_DEFAULT ? "default" : "case";
        int ordinal = caseOrdinal(ctSwitch, ctCase);
        return new BranchContext(
                kind, groupId, groupId + ":" + armKind + ":" + ordinal + ":" + sanitizeIdSegment(label), label, source);
    }

    private BranchContext catchBranchContext(CtCatch ctCatch) {
        CtTry owner = parentOf(ctCatch, CtTry.class);
        SourceInfo source = buildControlSource(ctCatch);
        String groupId = branchId("try", owner != null ? owner : ctCatch);
        String exceptionName = UNKNOWN;
        if (ctCatch.getParameter() != null && ctCatch.getParameter().getType() != null) {
            exceptionName = ctCatch.getParameter().getType().getSimpleName();
        }
        String label = "catch " + exceptionName;
        int ordinal = catchOrdinal(owner, ctCatch);
        return new BranchContext(
                CallEdge.ControlFlowKind.CATCH,
                groupId,
                groupId + ":catch:" + ordinal + ":" + sanitizeIdSegment(exceptionName),
                label,
                source);
    }

    private BranchContext finallyBranchContext(CtTry ctTry) {
        CtBlock<?> finalizer = ctTry.getFinalizer();
        SourceInfo source = buildControlSource(finalizer != null ? finalizer : ctTry);
        String groupId = branchId("try", ctTry);
        return new BranchContext(CallEdge.ControlFlowKind.FINALLY, groupId, groupId + ":finally", "finally", source);
    }

    private static String branchId(String kind, CtElement element) {
        CtType<?> ownerType = parentOf(element, CtType.class);
        CtMethod<?> ownerMethod = parentOf(element, CtMethod.class);
        var pos = element.getPosition();
        return "branch:" + sanitizeIdSegment(kind) + ":" + sanitizeIdSegment(ownerTypeName(ownerType)) + "#"
                + sanitizeIdSegment(ownerMethodName(ownerMethod)) + ":" + stableFileSegment(sourceFileOf(pos)) + ":"
                + sourceCoordinates(pos);
    }

    private static String ownerTypeName(CtType<?> ownerType) {
        if (ownerType == null
                || ownerType.getQualifiedName() == null
                || ownerType.getQualifiedName().isBlank()) {
            return UNKNOWN;
        }
        return ownerType.getQualifiedName();
    }

    private static String ownerMethodName(CtMethod<?> ownerMethod) {
        if (ownerMethod == null
                || ownerMethod.getSimpleName() == null
                || ownerMethod.getSimpleName().isBlank()) {
            return UNKNOWN;
        }
        return ownerMethod.getSimpleName();
    }

    private static String sourceCoordinates(spoon.reflect.cu.SourcePosition pos) {
        if (pos == null || !pos.isValidPosition()) {
            return "L0C0-L0C0";
        }
        return "L" + pos.getLine() + "C" + pos.getColumn() + "-L" + pos.getEndLine() + "C" + pos.getEndColumn() + "@"
                + pos.getSourceStart() + "-" + pos.getSourceEnd();
    }

    private static int caseOrdinal(CtAbstractSwitch<?> ctSwitch, CtCase<?> ctCase) {
        if (ctSwitch == null || ctSwitch.getCases() == null) {
            return 0;
        }
        List<? extends CtCase<?>> cases = ctSwitch.getCases();
        for (int i = 0; i < cases.size(); i++) {
            if (cases.get(i) == ctCase) {
                return i;
            }
        }
        return 0;
    }

    private static int catchOrdinal(CtTry owner, CtCatch ctCatch) {
        if (owner == null || owner.getCatchers() == null) {
            return 0;
        }
        List<CtCatch> catchers = owner.getCatchers();
        for (int i = 0; i < catchers.size(); i++) {
            if (catchers.get(i) == ctCatch) {
                return i;
            }
        }
        return 0;
    }

    private static String stableFileSegment(String file) {
        if (file == null || file.isBlank()) {
            return UNKNOWN;
        }
        String normalized = file.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return sanitizeIdSegment(name);
    }

    private static String sanitizeIdSegment(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        String sanitized = value.trim()
                .replaceAll("[^A-Za-z0-9._:-]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        return sanitized.isBlank() ? UNKNOWN : sanitized;
    }

    private static String caseLabel(CtCase<?> ctCase) {
        List<? extends CtExpression<?>> expressions = ctCase.getCaseExpressions();
        if (expressions == null || expressions.isEmpty()) {
            return "default";
        }
        return expressions.stream().map(Object::toString).collect(Collectors.joining(", "));
    }

    private static boolean isWithin(CtElement candidate, CtElement possibleAncestor) {
        if (candidate == null || possibleAncestor == null) return false;
        CtElement cursor = candidate;
        while (cursor != null) {
            if (cursor == possibleAncestor) return true;
            cursor = cursor.getParent();
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <T extends CtElement> T parentOf(CtElement element, Class<T> type) {
        CtElement cursor = element == null ? null : element.getParent();
        while (cursor != null) {
            if (type.isInstance(cursor)) {
                return (T) cursor;
            }
            cursor = cursor.getParent();
        }
        return null;
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

            OutboundClassification classification = classifyOutbound(inv, qn);
            if (classification == null) continue;

            String id = "outbound:" + fromComp.id.serialize() + "#" + methodName + ":" + (index++);
            if (!ctx.addSeenId(id)) continue;

            model.outboundSinkSites.add(buildOutboundSite(id, classification, fromComp, methodName, qn, inv));
        }
    }

    private record OutboundClassification(DataFlowSink.Kind kind, String channel) {}

    private OutboundClassification classifyOutbound(CtInvocation<?> inv, String qn) {
        DataFlowSink.Kind kind = classifyOutboundCallee(qn);
        if (kind != null) return new OutboundClassification(kind, null);
        String[] kindAndChannel = classifyMessagingFieldTarget(inv);
        if (kindAndChannel == null) return null;
        DataFlowSink.Kind resolved =
                MESSAGING.equals(kindAndChannel[0]) ? DataFlowSink.Kind.MESSAGING : DataFlowSink.Kind.EVENT_BUS;
        return new OutboundClassification(resolved, kindAndChannel[1]);
    }

    private OutboundSinkSite buildOutboundSite(
            String id,
            OutboundClassification classification,
            Component fromComp,
            String methodName,
            String qn,
            CtInvocation<?> inv) {
        OutboundSinkSite site = new OutboundSinkSite();
        site.id = id;
        site.kind = classification.kind();
        site.channel = classification.channel();
        site.componentId = fromComp.id;
        site.method = methodName;
        site.calleeQualifiedName = qn;
        site.calleeMethod = inv.getExecutable().getSimpleName();
        var pos = inv.getPosition();
        site.source = new SourceInfo(sourceFileOf(pos), sourceLineOf(pos), "invocation", 0.85);
        return site;
    }

    private static String sourceFileOf(spoon.reflect.cu.SourcePosition pos) {
        return pos != null && pos.isValidPosition() ? pos.getFile().getAbsolutePath() : UNKNOWN;
    }

    private static int sourceLineOf(spoon.reflect.cu.SourcePosition pos) {
        return pos != null && pos.isValidPosition() ? pos.getLine() : 0;
    }

    /** Returns [kindString, channelOrNull] when the invocation target is an Emitter/EventBus field, else null. */
    private static String[] classifyMessagingFieldTarget(CtInvocation<?> inv) {
        if (!(inv.getTarget() instanceof CtFieldRead<?> fr)) return null;
        var fieldType = fr.getVariable().getType();
        if (fieldType == null) return null;
        String simple = fieldType.getSimpleName();
        String kindStr;
        if (EMITTER_TYPES.contains(simple)) kindStr = MESSAGING;
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
            if (val != null) return val.toString().replaceAll("^\"", "").replaceAll("\"$", "");
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
        Set<String> calleeSharedState;
        if (calleeType != null) {
            calleeSharedState = ctx.sharedStateFieldsFor(calleeType, this::buildSharedStateFieldSet);
        } else {
            calleeSharedState = Set.of();
        }
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
            fa.fieldBinding = new dev.dominikbreu.archlens.model.ids.FieldBinding.CrossComponent(
                    new dev.dominikbreu.archlens.model.ids.FieldRef(toComp.id, fieldName));
            fa.method = fromMethod;
            fa.id = FieldAccessId.of(FIELD_PREFIX + fromComp.id.serialize() + "#" + fromMethod + "@"
                    + toComp.id.serialize() + "#" + calleeMethod.getSimpleName() + ":" + fieldName + ":read:xcomp");
            var pos = inv.getPosition();
            String file;
            if (pos != null && pos.isValidPosition()) {
                file = pos.getFile().getAbsolutePath();
            } else {
                file = UNKNOWN;
            }
            int line;
            if (pos != null && pos.isValidPosition()) {
                line = pos.getLine();
            } else {
                line = 0;
            }
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
            if (sharedStateFields.contains(fieldName)) {
                return fieldName;
            } else {
                return null;
            }
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

    private static String literalValue(CtLiteral<?> lit) {
        return lit.getValue() == null ? "" : lit.getValue().toString();
    }

    private static String resolveArgToLiteral(CtExpression<?> arg) {
        if (arg instanceof CtLiteral<?> lit) {
            return literalValue(lit);
        }
        if (arg instanceof CtVariableRead<?> read) {
            String fromField = fieldReferenceLiteral(read);
            return fromField != null ? fromField : localVariableLiteral(read);
        }
        return null;
    }

    private static String fieldReferenceLiteral(CtVariableRead<?> read) {
        if (!(read.getVariable() instanceof CtFieldReference<?> ref)) return null;
        try {
            CtField<?> decl = ref.getDeclaration();
            if (decl != null && decl.getDefaultExpression() instanceof CtLiteral<?> lit) {
                return literalValue(lit);
            }
        } catch (Exception _) {
        }
        return null;
    }

    private static String localVariableLiteral(CtVariableRead<?> read) {
        try {
            if (read.getVariable().getDeclaration() instanceof CtLocalVariable<?> local
                    && local.getDefaultExpression() instanceof CtLiteral<?> lit) {
                return literalValue(lit);
            }
        } catch (Exception _) {
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
            return firstVarReadInArgs(ctor.getArguments());
        }
        if (expr instanceof CtInvocation<?> inv) {
            String fromTarget = inv.getTarget() != null ? findFirstVarRead(inv.getTarget()) : null;
            return fromTarget != null ? fromTarget : firstVarReadInArgs(inv.getArguments());
        }
        return null;
    }

    private static String firstVarReadInArgs(List<? extends CtExpression<?>> args) {
        for (CtExpression<?> a : args) {
            String r = findFirstVarRead(a);
            if (r != null) return r;
        }
        return null;
    }

    private Map<String, List<String>> buildEntrypointParamMap(ArchitectureModel model) {
        Map<String, List<String>> map = new HashMap<>();
        for (Entrypoint ep : model.entrypoints) {
            map.computeIfAbsent(ep.componentId.serialize() + "#" + ep.name, k -> new ArrayList<>());
        }
        return map;
    }

    private void enrichEntrypointParameters(
            CtMethod<?> method,
            dev.dominikbreu.archlens.model.ids.ComponentId compId,
            Map<String, List<String>> entrypointParams,
            ArchitectureModel model) {
        String key = compId.serialize() + "#" + method.getSimpleName();
        if (!entrypointParams.containsKey(key)) return;
        List<String> names =
                method.getParameters().stream().map(CtParameter::getSimpleName).toList();
        model.entrypoints.stream()
                .filter(ep -> ep.componentId.equals(compId) && ep.name.equals(method.getSimpleName()))
                .filter(ep -> ep.parameters.isEmpty())
                .forEach(ep -> ep.parameters.addAll(names));
    }

    private String resolveCallKind(CtFieldRead<?> fieldRead) {
        if (fieldRead.getType() == null) return DIRECT;
        String simple = fieldRead.getType().getSimpleName();
        if (EVENT_BUS_TYPES.contains(simple)) return "event-bus";
        if (EMITTER_TYPES.contains(simple)) return MESSAGING;
        return DIRECT;
    }

    private SourceInfo buildSource(CtInvocation<?> inv) {
        var pos = inv.getPosition();
        String file;
        if (pos.isValidPosition()) {
            file = pos.getFile().getAbsolutePath();
        } else {
            file = UNKNOWN;
        }
        int line;
        if (pos.isValidPosition()) {
            line = pos.getLine();
        } else {
            line = 0;
        }
        return new SourceInfo(file, line, "invocation", 0.95);
    }

    private SourceInfo buildControlSource(CtElement element) {
        var pos = element.getPosition();
        return new SourceInfo(sourceFileOf(pos), sourceLineOf(pos), "control-flow", 0.95);
    }
}
