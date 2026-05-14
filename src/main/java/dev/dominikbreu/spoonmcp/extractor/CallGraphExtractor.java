package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
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
import spoon.reflect.declaration.CtType;
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

    /** Creates a call graph extractor using default resolution rules. */
    public CallGraphExtractor() {}

    /**
     * Extracts call edges from the supplied Spoon model and appends them to
     * {@code model.callEdges}.
     *
     * @param ctModel Spoon model for a single Maven module
     * @param model   architecture model to update
     */
    public void extract(CtModel ctModel, ArchitectureModel model) {
        Map<String, Component> byId = new HashMap<>();
        Map<String, Component> bySimpleName = new LinkedHashMap<>();
        for (Component c : model.components) {
            byId.put(c.id, c);
            bySimpleName.put(c.name, c);
        }

        Set<String> existingIds = model.callEdges.stream().map(e -> e.id).collect(Collectors.toSet());

        Map<String, List<String>> entrypointParams = buildEntrypointParamMap(model);

        for (CtType<?> type : ctModel.getAllTypes()) {
            String fromId = "comp:" + type.getQualifiedName();
            Component fromComp = byId.get(fromId);
            if (fromComp == null) continue;

            Map<String, Component> fieldToComp = buildFieldMap(type, fromId, byId, bySimpleName);
            Set<String> sharedStateFields = buildSharedStateFieldSet(type);

            Set<String> ownMethodNames =
                    type.getMethods().stream().map(CtMethod::getSimpleName).collect(Collectors.toSet());

            for (CtMethod<?> method : type.getMethods()) {
                extractFromMethod(method, fromComp, fieldToComp, model, existingIds);
                extractIntraComponentCalls(method, fromComp, ownMethodNames, model, existingIds);
                extractFieldAccesses(method, fromComp, sharedStateFields, model);
                extractOutboundSinkSites(method, fromComp, model);
                enrichEntrypointParameters(method, fromId, entrypointParams, model);
            }
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
            CtMethod<?> method, Component fromComp, Set<String> sharedStateFields, ArchitectureModel model) {
        if (sharedStateFields.isEmpty()) return;
        String methodName = method.getSimpleName();

        for (CtAssignment<?, ?> assign : method.getElements(new TypeFilter<>(CtAssignment.class))) {
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

        for (CtInvocation<?> inv : method.getElements(new TypeFilter<>(CtInvocation.class))) {
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

        for (CtFieldRead<?> read : method.getElements(new TypeFilter<>(CtFieldRead.class))) {
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

    /**
     * Emits same-component call edges for method calls on implicit/explicit {@code this}
     * whose callee is declared in the same type. This lets the DataFlowTracer follow
     * chains like {@code dispatchAll → buildAndSend → BrokerClient.publish} even when
     * the intermediate hop is a private method that the field-read filter would skip.
     */
    private void extractIntraComponentCalls(
            CtMethod<?> method,
            Component fromComp,
            Set<String> ownMethodNames,
            ArchitectureModel model,
            Set<String> existingIds) {
        String fromMethod = method.getSimpleName();
        for (CtInvocation<?> inv : method.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (inv.getTarget() instanceof CtFieldRead<?>) continue; // cross-component, handled elsewhere
            if (inv.getTarget() instanceof CtTypeAccess<?>) continue; // static call
            String toMethod = inv.getExecutable().getSimpleName();
            if (!ownMethodNames.contains(toMethod)) continue;
            if (toMethod.equals(fromMethod)) continue; // ignore trivial self-call
            String edgeId = "call:" + fromComp.id + "#" + fromMethod + "->" + fromComp.id + "#" + toMethod;
            if (!existingIds.add(edgeId)) continue;
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

    private Map<String, Component> buildFieldMap(
            CtType<?> type, String ownId, Map<String, Component> byId, Map<String, Component> bySimpleName) {
        Map<String, Component> map = new HashMap<>();
        for (CtField<?> field : type.getFields()) {
            if (field.getType() == null) continue;
            Component target = resolveType(
                    field.getType().getQualifiedName(), field.getType().getSimpleName(), byId, bySimpleName);
            if (target != null && !target.id.equals(ownId)) {
                map.put(field.getSimpleName(), target);
            }
        }
        return map;
    }

    private void extractFromMethod(
            CtMethod<?> method,
            Component fromComp,
            Map<String, Component> fieldToComp,
            ArchitectureModel model,
            Set<String> existingIds) {
        String fromMethod = method.getSimpleName();
        Map<CtInvocation<?>, Set<String>> killedSnapshots = computeKilledSnapshots(method);

        for (CtInvocation<?> inv : method.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!(inv.getTarget() instanceof CtFieldRead<?> fieldRead)) continue;

            String fieldName = fieldRead.getVariable().getSimpleName();
            Component toComp = fieldToComp.get(fieldName);
            if (toComp == null) continue;

            String toMethod = inv.getExecutable().getSimpleName();
            String callKind = resolveCallKind(fieldRead);
            String edgeId = "call:" + fromComp.id + "#" + fromMethod + "->" + toComp.id + "#" + toMethod;

            if (!existingIds.add(edgeId)) continue;

            CallEdge edge = new CallEdge();
            edge.id = edgeId;
            edge.fromComponentId = fromComp.id;
            edge.fromMethod = fromMethod;
            edge.toComponentId = toComp.id;
            edge.toMethod = toMethod;
            edge.callKind = callKind;
            edge.source = buildSource(inv);
            buildParamMapping(inv, edge);
            edge.assignedToVar = resolveAssignedToVar(inv);
            edge.returnsTracked = calleeReturnsTracked(inv);
            Set<String> snap = killedSnapshots.get(inv);
            if (snap != null) edge.killedTrackedNames.addAll(snap);
            model.callEdges.add(edge);
            emitCallerSideFieldReadIfGetter(inv, fromComp, fromMethod, toComp, model);
        }
    }

    /**
     * Walks the method body in source order and snapshots, at every CtInvocation, which
     * caller-method local names have been reassigned (via another invocation result or
     * cross-component call) prior to that point. The snapshot is consumed when emitting
     * the corresponding cross-component {@link CallEdge}.
     */
    private Map<CtInvocation<?>, Set<String>> computeKilledSnapshots(CtMethod<?> method) {
        Map<CtInvocation<?>, Set<String>> snapshots = new java.util.IdentityHashMap<>();
        Set<String> killed = new LinkedHashSet<>();
        for (var element : method.getElements(new TypeFilter<>(spoon.reflect.declaration.CtElement.class))) {
            if (element instanceof CtInvocation<?> inv) {
                snapshots.put(inv, new LinkedHashSet<>(killed));
            } else if (element instanceof CtAssignment<?, ?> assign
                    && assign.getAssignment() instanceof CtInvocation<?>
                    && assign.getAssigned() instanceof CtVariableWrite<?> vw) {
                killed.add(vw.getVariable().getSimpleName());
            } else if (element instanceof CtLocalVariable<?> lv
                    && lv.getDefaultExpression() instanceof CtInvocation<?>) {
                // a fresh local declaration shadows any prior name — same effect for tracker.
                killed.add(lv.getSimpleName());
            }
        }
        return snapshots;
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

    private void extractOutboundSinkSites(CtMethod<?> method, Component fromComp, ArchitectureModel model) {
        String methodName = method.getSimpleName();
        Set<String> existingIds =
                model.outboundSinkSites.stream().map(s -> s.id).collect(Collectors.toSet());
        int index = 0;
        for (CtInvocation<?> inv : method.getElements(new TypeFilter<>(CtInvocation.class))) {
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
            if (!existingIds.add(id)) continue;

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

    private boolean calleeReturnsTracked(CtInvocation<?> inv) {
        var executable = inv.getExecutable().getDeclaration();
        if (!(executable instanceof CtMethod<?> calleeMethod)) return false;
        if (calleeMethod.getBody() == null) return false;
        Set<String> paramNames = calleeMethod.getParameters().stream()
                .map(CtParameter::getSimpleName)
                .collect(Collectors.toSet());
        CtType<?> calleeType = calleeMethod.getDeclaringType();
        Set<String> calleeSharedState = calleeType != null ? buildSharedStateFieldSet(calleeType) : Set.of();
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
            CtInvocation<?> inv, Component fromComp, String fromMethod, Component toComp, ArchitectureModel model) {
        var executable = inv.getExecutable().getDeclaration();
        if (!(executable instanceof CtMethod<?> calleeMethod)) return;
        if (calleeMethod.getBody() == null) return;
        CtType<?> calleeType = calleeMethod.getDeclaringType();
        if (calleeType == null) return;
        Set<String> calleeSharedState = buildSharedStateFieldSet(calleeType);
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
            fa.id = "field:" + fromComp.id + "#" + fromMethod + "@" + toComp.id + "#"
                    + calleeMethod.getSimpleName() + ":" + fieldName + ":read:xcomp";
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
                continue;
            }
            String synthesised = findFirstVarRead(arg);
            if (synthesised != null) {
                edge.paramMapping.put(synthesised, calleeParam);
                edge.syntheticParamMappings.add(calleeParam);
            }
        }
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

    private Component resolveType(
            String qualifiedName, String simpleName, Map<String, Component> byId, Map<String, Component> bySimpleName) {
        Component c = byId.get("comp:" + qualifiedName);
        if (c != null) return c;
        return bySimpleName.get(simpleName);
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
