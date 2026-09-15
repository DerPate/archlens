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

    /** Simple type names recognized as a Vert.x-style event bus field, for call-kind and outbound classification. */
    private static final Set<String> EVENT_BUS_TYPES = Set.of("EventBus");

    /** Simple type names of Reactive Messaging emitter fields, classified as messaging outbound sends. */
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

    /** Field type names excluded from shared-state detection; logging/tracing fields are never shared state. */
    private static final Set<String> SHARED_STATE_TYPE_DENYLIST = Set.of("Logger", "Log", "Slf4j", "Tracer");

    /** Simple-type-name prefixes excluded from shared-state detection (e.g. {@code AuditLog}-style types). */
    private static final Set<String> SHARED_STATE_TYPE_DENYLIST_PREFIXES = Set.of("Audit");

    /** Callee qualified-name prefixes classified as file-system outbound writes. */
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

    /** Returns the OpenTelemetry tracer used to span the {@link #extract} pass. */
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

    /**
     * The control-flow branch (if/else, ternary, switch case, catch, finally) an invocation is
     * nested in, used to tag the resulting {@link CallEdge} with branch identity so that
     * different arms of the same branch don't collapse into a single edge.
     *
     * @param kind the kind of control-flow construct
     * @param branchGroupId id shared by every arm of the same branch (e.g. the same if/switch/try)
     * @param branchArmId id unique to this specific arm
     * @param branchLabel human-readable label for the arm (condition text, case value, exception type)
     * @param controlSource source location of the controlling construct
     */
    private record BranchContext(
            CallEdge.ControlFlowKind kind,
            String branchGroupId,
            String branchArmId,
            String branchLabel,
            SourceInfo controlSource) {}

    /**
     * Walks {@code method}'s body once in source order, collecting every invocation together
     * with a snapshot of which local names have already been reassigned ("killed") at that
     * point, plus the method's assignments and field reads. See {@link MethodScan}.
     *
     * @param method the method to scan
     * @return the collected invocations, kill snapshots, assignments, and field reads
     */
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

    /**
     * Finds {@code type}'s fields that look like mutable shared state: collection/atomic field
     * types, or a field/type name ending in a shared-state-ish suffix ({@code Cache}, {@code
     * State}, {@code Store}, …), excluding anything on the denylist (loggers, tracers, audit
     * types). Used to decide which field accesses are worth recording as {@link FieldAccess}
     * edges.
     *
     * @param type the type whose fields to scan
     * @return names of fields recognized as shared state
     */
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

    /**
     * Lowercases the first character, so a type-name suffix like {@code "Cache"} can be matched
     * against a field-name suffix like {@code "...cache"}.
     *
     * @param s the string to adjust
     * @return {@code s} with its first character lowercased
     */
    private static String lowerFirst(String s) {
        if (s.isEmpty()) {
            return s;
        } else {
            return Character.toLowerCase(s.charAt(0)) + s.substring(1);
        }
    }

    /**
     * Checks {@code simpleTypeName} against {@link #SHARED_STATE_TYPE_DENYLIST} and {@link
     * #SHARED_STATE_TYPE_DENYLIST_PREFIXES}.
     *
     * @param simpleTypeName the field's simple type name, or {@code null}
     * @return {@code true} if the type is excluded from shared-state detection
     */
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
        if (pos != null && pos.isValidPosition() && pos.getFile() != null) {
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

    /**
     * Returns the key-variable name for keyed-write methods ({@code put}, {@code set}, {@code
     * merge}, …). Only meaningful when there are at least 2 arguments; single-argument writes
     * ({@code add}, {@code offer}, {@code push}, …) have no separate key position.
     *
     * @param args the invocation's arguments
     * @return the key argument's local variable name, or {@code null} if not applicable
     */
    private static String firstVarReadName(List<spoon.reflect.code.CtExpression<?>> args) {
        if (args.size() < 2) return null;
        CtExpression<?> first = args.getFirst();
        if (first instanceof CtVariableRead<?> vr) {
            return vr.getVariable().getSimpleName();
        } else {
            return null;
        }
    }

    /**
     * Returns the value-variable name for write methods ({@code put}/{@code set}/{@code add}
     * etc.). Only the last argument is inspected — the value position — since falling back to
     * earlier arguments would confuse the key with the value for calls like {@code put(key,
     * someInvocation())}.
     *
     * @param args the invocation's arguments
     * @return the last argument's local variable name, or {@code null} if it isn't a variable read
     */
    private static String lastVarReadName(List<spoon.reflect.code.CtExpression<?>> args) {
        if (args.isEmpty()) return null;
        CtExpression<?> last = args.getLast();
        if (last instanceof CtVariableRead<?> vr) {
            return vr.getVariable().getSimpleName();
        } else {
            return null;
        }
    }

    /**
     * Same as {@link #lastVarReadName} but for a field read in the value position.
     *
     * @param args the invocation's arguments
     * @return the last argument's field name, or {@code null} if it isn't a field read
     */
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

    /**
     * Classifies a terminal accessor-chain method name as a shared-state read or write.
     *
     * @param terminalMethod the method name at the end of the accessor chain (e.g. {@code get}, {@code put})
     * @return {@link FieldAccess.Kind#WRITE}, {@link FieldAccess.Kind#READ}, or {@code null} if neither
     */
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

    /**
     * Resolves an accessor-chain target (e.g. {@code getCache()} in {@code getCache().get(k)})
     * to the shared-state field it returns, if the accessor is a getter-like method whose body
     * returns one of its declaring type's shared-state fields.
     *
     * @param accessor the accessor invocation being called on
     * @param ctx extraction context, used to resolve the accessor's declaring type's shared-state fields
     * @return the returned field's name, or {@code null} if the accessor doesn't resolve to one
     */
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
            // Only follow implicit/explicit `this` calls — anything else (field, local var, chained
            // call, static access, …) is not an intra-component self-dispatch.
            CtExpression<?> target = inv.getTarget();
            if (target != null && !(target instanceof spoon.reflect.code.CtThisAccess<?>)) continue;
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

    /**
     * Returns the simple (unqualified) name of a fully qualified type name.
     *
     * @param qualifiedName the fully qualified type name
     * @return the substring after the last {@code '.'}, or the whole string if there is none
     */
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

    /**
     * Copies branch identity from {@code branch} onto {@code edge}, if present.
     *
     * @param edge the call edge to tag
     * @param branch the branch context, or {@code null} if the call isn't inside a branch
     */
    private void applyBranchContext(CallEdge edge, BranchContext branch) {
        if (branch == null) return;
        edge.controlFlowKind = branch.kind();
        edge.branchGroupId = branch.branchGroupId();
        edge.branchArmId = branch.branchArmId();
        edge.branchLabel = branch.branchLabel();
        edge.controlSource = branch.controlSource();
    }

    /**
     * Walks up {@code invocation}'s ancestor chain to find the nearest enclosing control-flow
     * construct (if/else, ternary, switch case, catch, or finally) and builds its branch context.
     *
     * @param invocation the invocation to classify
     * @return the enclosing branch context, or {@code null} if the call isn't inside one
     */
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

    /**
     * Builds the branch context for an invocation inside an {@code if}'s then/else block.
     *
     * @param invocation the invocation being classified
     * @param ctIf the enclosing {@code if} statement
     * @return the then/else branch context, or {@code null} if {@code invocation} is in neither block
     */
    private BranchContext ifBranchContext(CtInvocation<?> invocation, CtIf ctIf) {
        SourceInfo source = buildControlSource(ctIf);
        String groupId = branchId("if", ctIf);
        String cond = conditionLabel(ctIf.getCondition());
        if (isWithin(invocation, ctIf.getThenStatement())) {
            return new BranchContext(
                    CallEdge.ControlFlowKind.IF_THEN, groupId, groupId + ":then", "if " + cond, source);
        }
        if (isWithin(invocation, ctIf.getElseStatement())) {
            return new BranchContext(
                    CallEdge.ControlFlowKind.IF_ELSE, groupId, groupId + ":else", "else: !(" + cond + ")", source);
        }
        return null;
    }

    /**
     * Builds the branch context for an invocation inside a ternary's then/else expression.
     *
     * @param invocation the invocation being classified
     * @param conditional the enclosing ternary expression
     * @return the then/else branch context, or {@code null} if {@code invocation} is in neither side
     */
    private BranchContext ternaryBranchContext(CtInvocation<?> invocation, CtConditional<?> conditional) {
        SourceInfo source = buildControlSource(conditional);
        String groupId = branchId("ternary", conditional);
        String cond = conditionLabel(conditional.getCondition());
        if (isWithin(invocation, conditional.getThenExpression())) {
            return new BranchContext(
                    CallEdge.ControlFlowKind.TERNARY_THEN, groupId, groupId + ":then", "if " + cond, source);
        }
        if (isWithin(invocation, conditional.getElseExpression())) {
            return new BranchContext(
                    CallEdge.ControlFlowKind.TERNARY_ELSE, groupId, groupId + ":else", "else: !(" + cond + ")", source);
        }
        return null;
    }

    /** Upper bound on stored branch-condition text; truncation here is lossy for every consumer. */
    private static final int MAX_CONDITION_LENGTH = 255;

    /**
     * Renders a branch condition as a label, truncated to {@link #MAX_CONDITION_LENGTH}.
     *
     * @param condition the condition expression, or {@code null}
     * @return the condition's source text (truncated), or {@code "?"} if {@code condition} is {@code null}
     */
    private static String conditionLabel(CtExpression<?> condition) {
        if (condition == null) return "?";
        String text = condition.toString();
        if (text.length() <= MAX_CONDITION_LENGTH) return text;
        return text.substring(0, MAX_CONDITION_LENGTH - 3) + "...";
    }

    /**
     * Builds the branch context for a {@code switch} case (or {@code default}) arm.
     *
     * @param ctCase the case (or default) arm the invocation belongs to
     * @return the case/default branch context for {@code ctCase}
     */
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

    /**
     * Builds the branch context for an invocation inside a {@code catch} block.
     *
     * @param ctCatch the catch clause the invocation is inside
     * @return the catch branch context, labeled with the caught exception's simple type name
     */
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

    /**
     * Builds the branch context for an invocation inside a {@code finally} block.
     *
     * @param ctTry the {@code try} statement owning the finally block
     * @return the finally branch context
     */
    private BranchContext finallyBranchContext(CtTry ctTry) {
        CtBlock<?> finalizer = ctTry.getFinalizer();
        SourceInfo source = buildControlSource(finalizer != null ? finalizer : ctTry);
        String groupId = branchId("try", ctTry);
        return new BranchContext(CallEdge.ControlFlowKind.FINALLY, groupId, groupId + ":finally", "finally", source);
    }

    /**
     * Builds a stable id identifying a branch group, so repeated extraction runs (and different
     * checkouts) produce the same id for the same source construct.
     *
     * @param kind the branch construct kind ({@code "if"}, {@code "ternary"}, {@code "switch"}, {@code "try"})
     * @param element the controlling construct (the if/conditional/switch/try element)
     * @return an id combining kind, owner type/method, file, and source coordinates
     */
    private static String branchId(String kind, CtElement element) {
        CtType<?> ownerType = parentOf(element, CtType.class);
        CtMethod<?> ownerMethod = parentOf(element, CtMethod.class);
        var pos = element.getPosition();
        return "branch:" + sanitizeIdSegment(kind) + ":" + sanitizeIdSegment(ownerTypeName(ownerType)) + "#"
                + sanitizeIdSegment(ownerMethodName(ownerMethod)) + ":" + stableFileSegment(sourceFileOf(pos)) + ":"
                + sourceCoordinates(pos);
    }

    /**
     * @param ownerType the enclosing type, or {@code null}
     * @return the type's qualified name, or {@link #UNKNOWN} if unavailable
     */
    private static String ownerTypeName(CtType<?> ownerType) {
        if (ownerType == null
                || ownerType.getQualifiedName() == null
                || ownerType.getQualifiedName().isBlank()) {
            return UNKNOWN;
        }
        return ownerType.getQualifiedName();
    }

    /**
     * @param ownerMethod the enclosing method, or {@code null}
     * @return the method's simple name, or {@link #UNKNOWN} if unavailable
     */
    private static String ownerMethodName(CtMethod<?> ownerMethod) {
        if (ownerMethod == null
                || ownerMethod.getSimpleName() == null
                || ownerMethod.getSimpleName().isBlank()) {
            return UNKNOWN;
        }
        return ownerMethod.getSimpleName();
    }

    /**
     * Renders a source position as a compact coordinate string for use in {@link #branchId}.
     *
     * @param pos the source position, or {@code null}/invalid
     * @return {@code "L<line>C<col>-L<endLine>C<endCol>@<start>-<end>"}, or a zero placeholder if unavailable
     */
    private static String sourceCoordinates(spoon.reflect.cu.SourcePosition pos) {
        if (pos == null || !pos.isValidPosition()) {
            return "L0C0-L0C0";
        }
        return "L" + pos.getLine() + "C" + pos.getColumn() + "-L" + pos.getEndLine() + "C" + pos.getEndColumn() + "@"
                + pos.getSourceStart() + "-" + pos.getSourceEnd();
    }

    /**
     * @param ctSwitch the owning switch, or {@code null}
     * @param ctCase the case to locate
     * @return {@code ctCase}'s index among its switch's cases, or {@code 0} if not found
     */
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

    /**
     * @param owner the owning try statement, or {@code null}
     * @param ctCatch the catch clause to locate
     * @return {@code ctCatch}'s index among its try's catch clauses, or {@code 0} if not found
     */
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

    /**
     * Extracts and sanitizes just the file name (not the full path) from a source file path, so
     * branch ids stay stable across machines and checkout locations.
     *
     * @param file the absolute source file path, or {@code null}/blank
     * @return the sanitized file name, or {@link #UNKNOWN}
     */
    private static String stableFileSegment(String file) {
        if (file == null || file.isBlank()) {
            return UNKNOWN;
        }
        String normalized = file.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return sanitizeIdSegment(name);
    }

    /**
     * Sanitizes arbitrary text into an id-safe segment (letters, digits, {@code . _ : -} only),
     * trimming stray leading/trailing dashes left by the substitution.
     *
     * @param value the raw text, or {@code null}/blank
     * @return the sanitized segment, or {@link #UNKNOWN}
     */
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

    /**
     * @param ctCase the case (or default) arm
     * @return the case's label expressions joined with {@code ", "}, or {@code "default"} if none
     */
    private static String caseLabel(CtCase<?> ctCase) {
        List<? extends CtExpression<?>> expressions = ctCase.getCaseExpressions();
        if (expressions == null || expressions.isEmpty()) {
            return "default";
        }
        return expressions.stream().map(Object::toString).collect(Collectors.joining(", "));
    }

    /**
     * @param candidate the element to test, or {@code null}
     * @param possibleAncestor the possible ancestor (or the element itself), or {@code null}
     * @return {@code true} if {@code possibleAncestor} is {@code candidate} or one of its ancestors
     */
    private static boolean isWithin(CtElement candidate, CtElement possibleAncestor) {
        if (candidate == null || possibleAncestor == null) return false;
        CtElement cursor = candidate;
        while (cursor != null) {
            if (cursor == possibleAncestor) return true;
            cursor = cursor.getParent();
        }
        return false;
    }

    /**
     * Walks up from {@code element} to find the nearest ancestor assignable to {@code type}.
     *
     * @param element the element to start from, or {@code null}
     * @param type the ancestor type to look for
     * @return the nearest matching ancestor, or {@code null} if none is found
     */
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

    /**
     * @param inv the invocation whose receiver to name
     * @return the receiver's local variable or field name, or {@code null} if it can't be resolved to one
     */
    private String resolveReceiverLocalName(CtInvocation<?> inv) {
        return receiverLocalName(inv.getTarget());
    }

    /**
     * Resolves an expression to the local variable or field name it reads, unwrapping chained
     * invocations (e.g. {@code getCache().get(k)} resolves through to {@code getCache}'s target).
     *
     * @param expression the receiver expression, or {@code null}
     * @return the resolved variable/field name, or {@code null} if it isn't a var/field read
     */
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

    /**
     * @param inv the invocation whose result may be assigned
     * @return the local variable or field name the call result is assigned to, or {@code null} if unused/discarded
     */
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

    /**
     * The outcome of classifying an invocation as an outbound data-flow sink.
     *
     * @param kind the sink kind (messaging, event bus, file, or object storage)
     * @param channel the resolved messaging channel name, or {@code null} if not applicable/derivable
     */
    private record OutboundClassification(DataFlowSink.Kind kind, String channel) {}

    /**
     * Classifies an invocation as an outbound sink, first by the callee's qualified name
     * ({@link #classifyOutboundCallee}), then by whether its target is a messaging/event-bus
     * field ({@link #classifyMessagingFieldTarget}).
     *
     * @param inv the invocation to classify
     * @param qn the callee method's declaring type's qualified name
     * @return the outbound classification, or {@code null} if the call isn't an outbound sink
     */
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

    /**
     * @param pos the source position, or {@code null}/invalid
     * @return the source file's absolute path, or {@link #UNKNOWN} if unavailable
     */
    private static String sourceFileOf(spoon.reflect.cu.SourcePosition pos) {
        return pos != null && pos.isValidPosition() && pos.getFile() != null
                ? pos.getFile().getAbsolutePath()
                : UNKNOWN;
    }

    /**
     * @param pos the source position, or {@code null}/invalid
     * @return the 1-based source line, or {@code 0} if unavailable
     */
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

    /**
     * Reads the channel name off a field's {@code @Channel("name")} annotation, if present.
     *
     * @param fr a read of the Emitter/EventBus field
     * @return the channel name (quotes stripped), or {@code null} if the field has no {@code @Channel} annotation
     */
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

    /**
     * Classifies a callee by its declaring type's qualified-name prefix into a file or
     * object-storage outbound sink.
     *
     * @param calleeQualifiedName the callee method's declaring type's qualified name
     * @return the matching sink kind, or {@code null} if the callee matches neither prefix set
     */
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

    /**
     * Checks whether the callee's return statements ultimately return one of the callee's own
     * shared-state fields or one of its parameters, meaning the call result carries tracked data
     * the {@code DataFlowTracer} should be able to follow through the caller's assignment.
     *
     * @param inv the invocation whose callee to inspect
     * @param ctx extraction context, used to resolve the callee type's shared-state fields
     * @return {@code true} if the callee's return value is traceable to tracked state
     */
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
            if (pos != null && pos.isValidPosition() && pos.getFile() != null) {
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

    /**
     * Finds the shared-state field name a return expression evaluates to: either the expression
     * itself is a direct field read, or one of the fields it contains (e.g. inside a ternary or
     * method chain) is a tracked shared-state field.
     *
     * @param expression the returned expression, or {@code null}
     * @param sharedStateFields the declaring type's known shared-state field names
     * @return the matching field's name, or {@code null} if none is found
     */
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

    /**
     * Maps each positional argument at the call site to the callee's parameter name, so the
     * {@code DataFlowTracer} can follow tracked values across the call boundary: direct variable
     * arguments map 1:1, literal arguments are recorded in {@code edge.resolvedLiteralArgs}, and
     * otherwise a variable name found anywhere inside the argument expression is used as a
     * best-effort ("synthetic") mapping.
     *
     * @param inv the call site
     * @param edge the call edge to populate with parameter mappings
     */
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

    /**
     * @param lit the literal to read
     * @return the literal's string form, or {@code ""} if its value is {@code null}
     */
    private static String literalValue(CtLiteral<?> lit) {
        return lit.getValue() == null ? "" : lit.getValue().toString();
    }

    /**
     * Resolves an argument expression to a literal string, either directly or by following a
     * variable read back to its constant field/local-variable initializer.
     *
     * @param arg the argument expression
     * @return the resolved literal string, or {@code null} if the argument isn't a resolvable constant
     */
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

    /**
     * @param read a read of a field reference
     * @return the field's constant literal initializer value, or {@code null} if it isn't a field
     *     reference or has no constant initializer
     */
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

    /**
     * @param read a read of a local variable
     * @return the variable's constant literal initializer value, or {@code null} if it isn't a
     *     local variable or has no constant initializer
     */
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

    /**
     * Recursively searches an expression for the first variable read it contains, descending
     * into ternaries (both branches), constructor call arguments, and chained invocation targets
     * and arguments. Used as a best-effort fallback when an argument isn't a direct variable
     * read, so a mapping can still be synthesized for the {@code DataFlowTracer}.
     *
     * @param expr the expression to search, or {@code null}
     * @return the first variable name found, or {@code null} if none is found
     */
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

    /**
     * @param args the expressions to search, in order
     * @return the first variable name {@link #findFirstVarRead} finds among {@code args}, or {@code null}
     */
    private static String firstVarReadInArgs(List<? extends CtExpression<?>> args) {
        for (CtExpression<?> a : args) {
            String r = findFirstVarRead(a);
            if (r != null) return r;
        }
        return null;
    }

    /**
     * Seeds an empty parameter-name list for every known entrypoint, keyed by
     * {@code "<componentId>#<methodName>"}. {@link #enrichEntrypointParameters} fills each list
     * in as matching methods are visited during the extraction pass.
     *
     * @param model the architecture model whose entrypoints to seed
     * @return a mutable map from entrypoint key to its (initially empty) parameter-name list
     */
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

    /**
     * Legacy call-kind resolution (used only by {@link #extractFromMethod}'s field-read
     * fallback path) based on the field's declared type.
     *
     * @param fieldRead the field read acting as the call receiver
     * @return {@code "event-bus"}/{@code "messaging"} for event-bus/emitter fields, else {@link #DIRECT}
     */
    private String resolveCallKind(CtFieldRead<?> fieldRead) {
        if (fieldRead.getType() == null) return DIRECT;
        String simple = fieldRead.getType().getSimpleName();
        if (EVENT_BUS_TYPES.contains(simple)) return "event-bus";
        if (EMITTER_TYPES.contains(simple)) return MESSAGING;
        return DIRECT;
    }

    /**
     * @param inv the call site
     * @return source info for a call edge, evidence label {@code "invocation"}
     */
    private SourceInfo buildSource(CtInvocation<?> inv) {
        var pos = inv.getPosition();
        String file;
        if (pos.isValidPosition() && pos.getFile() != null) {
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

    /**
     * @param element the controlling construct
     * @return source info for a branch context, evidence label {@code "control-flow"}
     */
    private SourceInfo buildControlSource(CtElement element) {
        var pos = element.getPosition();
        return new SourceInfo(sourceFileOf(pos), sourceLineOf(pos), "control-flow", 0.95);
    }
}
