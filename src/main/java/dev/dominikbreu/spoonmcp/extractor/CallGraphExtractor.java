package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.CtModel;

import java.util.*;
import java.util.stream.Collectors;

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
    private static final Set<String> EMITTER_TYPES   = Set.of("Emitter", "MutinyEmitter");

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
        Map<String, Component> byId         = new HashMap<>();
        Map<String, Component> bySimpleName = new LinkedHashMap<>();
        for (Component c : model.components) {
            byId.put(c.id, c);
            bySimpleName.put(c.name, c);
        }

        Set<String> existingIds = model.callEdges.stream()
            .map(e -> e.id)
            .collect(Collectors.toSet());

        Map<String, List<String>> entrypointParams = buildEntrypointParamMap(model);

        for (CtType<?> type : ctModel.getAllTypes()) {
            String fromId = "comp:" + type.getQualifiedName();
            Component fromComp = byId.get(fromId);
            if (fromComp == null) continue;

            Map<String, Component> fieldToComp = buildFieldMap(type, fromId, byId, bySimpleName);

            for (CtMethod<?> method : type.getMethods()) {
                extractFromMethod(method, fromComp, fieldToComp, model, existingIds);
                enrichEntrypointParameters(method, fromId, entrypointParams, model);
            }
        }
    }

    private Map<String, Component> buildFieldMap(CtType<?> type, String ownId,
                                                  Map<String, Component> byId,
                                                  Map<String, Component> bySimpleName) {
        Map<String, Component> map = new HashMap<>();
        for (CtField<?> field : type.getFields()) {
            if (field.getType() == null) continue;
            Component target = resolveType(field.getType().getQualifiedName(),
                                           field.getType().getSimpleName(),
                                           byId, bySimpleName);
            if (target != null && !target.id.equals(ownId)) {
                map.put(field.getSimpleName(), target);
            }
        }
        return map;
    }

    private void extractFromMethod(CtMethod<?> method, Component fromComp,
                                    Map<String, Component> fieldToComp,
                                    ArchitectureModel model, Set<String> existingIds) {
        String fromMethod = method.getSimpleName();

        for (CtInvocation<?> inv : method.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!(inv.getTarget() instanceof CtFieldRead<?> fieldRead)) continue;

            String fieldName = fieldRead.getVariable().getSimpleName();
            Component toComp = fieldToComp.get(fieldName);
            if (toComp == null) continue;

            String toMethod  = inv.getExecutable().getSimpleName();
            String callKind  = resolveCallKind(fieldRead);
            String edgeId    = "call:" + fromComp.id + "#" + fromMethod
                             + "->" + toComp.id + "#" + toMethod;

            if (!existingIds.add(edgeId)) continue;

            CallEdge edge = new CallEdge();
            edge.id              = edgeId;
            edge.fromComponentId = fromComp.id;
            edge.fromMethod      = fromMethod;
            edge.toComponentId   = toComp.id;
            edge.toMethod        = toMethod;
            edge.callKind        = callKind;
            edge.source          = buildSource(inv);
            edge.paramMapping    = buildParamMapping(inv);
            model.callEdges.add(edge);
        }
    }

    private Map<String, String> buildParamMapping(CtInvocation<?> inv) {
        Map<String, String> mapping = new LinkedHashMap<>();
        var executable = inv.getExecutable().getDeclaration();
        if (executable == null) return mapping;
        List<CtParameter<?>> calleeParams = executable.getParameters();
        var args = inv.getArguments();
        for (int i = 0; i < args.size() && i < calleeParams.size(); i++) {
            if (args.get(i) instanceof CtVariableRead<?> varRead) {
                mapping.put(varRead.getVariable().getSimpleName(),
                            calleeParams.get(i).getSimpleName());
            }
        }
        return mapping;
    }

    private Map<String, List<String>> buildEntrypointParamMap(ArchitectureModel model) {
        Map<String, List<String>> map = new HashMap<>();
        for (Entrypoint ep : model.entrypoints) {
            map.computeIfAbsent(ep.componentId + "#" + ep.name, k -> new ArrayList<>());
        }
        return map;
    }

    private void enrichEntrypointParameters(CtMethod<?> method, String compId,
                                             Map<String, List<String>> entrypointParams,
                                             ArchitectureModel model) {
        String key = compId + "#" + method.getSimpleName();
        if (!entrypointParams.containsKey(key)) return;
        List<String> names = method.getParameters().stream()
            .map(CtParameter::getSimpleName)
            .collect(Collectors.toList());
        model.entrypoints.stream()
            .filter(ep -> ep.componentId.equals(compId) && ep.name.equals(method.getSimpleName()))
            .filter(ep -> ep.parameters.isEmpty())
            .forEach(ep -> ep.parameters.addAll(names));
    }

    private Component resolveType(String qualifiedName, String simpleName,
                                   Map<String, Component> byId,
                                   Map<String, Component> bySimpleName) {
        Component c = byId.get("comp:" + qualifiedName);
        if (c != null) return c;
        return bySimpleName.get(simpleName);
    }

    private String resolveCallKind(CtFieldRead<?> fieldRead) {
        if (fieldRead.getType() == null) return "direct";
        String simple = fieldRead.getType().getSimpleName();
        if (EVENT_BUS_TYPES.contains(simple)) return "event-bus";
        if (EMITTER_TYPES.contains(simple))   return "messaging";
        return "direct";
    }

    private SourceInfo buildSource(CtInvocation<?> inv) {
        var pos = inv.getPosition();
        String file = pos.isValidPosition() ? pos.getFile().getAbsolutePath() : "unknown";
        int    line = pos.isValidPosition() ? pos.getLine() : 0;
        return new SourceInfo(file, line, "invocation", 0.95);
    }
}
