package dev.dominikbreu.archlens.cache;

import dev.dominikbreu.archlens.extractor.PipelineGraphBuilder;
import dev.dominikbreu.archlens.extractor.PipelineGraphBuilder.Chain;
import dev.dominikbreu.archlens.extractor.PipelineGraphBuilder.Segment;
import dev.dominikbreu.archlens.model.AppEntry;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.CallEdge;
import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.Container;
import dev.dominikbreu.archlens.model.DataFlowBranch;
import dev.dominikbreu.archlens.model.DataFlowBranchArm;
import dev.dominikbreu.archlens.model.DataFlowEdge;
import dev.dominikbreu.archlens.model.DataFlowNode;
import dev.dominikbreu.archlens.model.DataFlowPath;
import dev.dominikbreu.archlens.model.DataFlowSink;
import dev.dominikbreu.archlens.model.DataFlowStep;
import dev.dominikbreu.archlens.model.Dependency;
import dev.dominikbreu.archlens.model.DeploymentEntry;
import dev.dominikbreu.archlens.model.Entrypoint;
import dev.dominikbreu.archlens.model.ExternalSystem;
import dev.dominikbreu.archlens.model.FieldAccess;
import dev.dominikbreu.archlens.model.InterfaceEntry;
import dev.dominikbreu.archlens.model.RuntimeFlow;
import dev.dominikbreu.archlens.model.RuntimeFlowStep;
import dev.dominikbreu.archlens.model.SourceInfo;
import dev.dominikbreu.archlens.model.ids.GraphNodeId;
import dev.dominikbreu.archlens.workflow.WorkflowLink;
import dev.dominikbreu.archlens.workflow.WorkflowLinker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;

/**
 * Projects an {@link ArchitectureModel} into a {@link GraphStore}.
 *
 * <p>Called exactly once per {@code index_workspace} invocation, then discarded.
 * No tool or renderer references this class; they use {@link GraphQuery} instead.</p>
 */
class GraphProjector {

    private static final String SOURCE = "source";
    private static final String TECHNOLOGY = "technology";
    private static final String BROKER = "broker";
    private static final String TOPIC = "topic";
    private static final String COMPONENT_ID = "componentId";
    private static final String DERIVED_FROM = "derivedFrom";
    private static final String SOURCE_FILE = "sourceFile";
    private static final String SOURCE_LINE = "sourceLine";
    private static final String SINK_MARKER = ":sink:";
    private static final String METHOD = "method";
    private static final String FIELD_NAME = "fieldName";
    private static final String FIELD_OWNER_COMPONENT_ID = "fieldOwnerComponentId";
    private static final String VIA_FIELD = "viaField";
    private static final String VIA_CHANNEL = "viaChannel";
    private static final String CONFIDENCE = "confidence";
    private static final String REL_STARTS_AT = "STARTS_AT";
    private static final String REL_DEPENDS_ON = "DEPENDS_ON";
    private static final String REL_WRITES_STATE = "WRITES_STATE";
    private static final String REL_READS_STATE = "READS_STATE";
    private static final String REL_STATE_HANDOFF = "STATE_HANDOFF";

    private GraphStore store;
    private ArchitectureModel model;

    /** Projects the full architecture model into the store, rebuilding it from scratch. */
    void project(ArchitectureModel sourceModel, GraphStore target) {
        this.store = target;
        this.store.clear();
        this.model = sourceModel;
        if (sourceModel == null) {
            return;
        }

        sourceModel.applications.forEach(this::addApplication);
        sourceModel.components.forEach(this::addComponent);
        sourceModel.entrypoints.forEach(this::addEntrypoint);
        sourceModel.interfaces.forEach(this::addInterface);
        sourceModel.containers.forEach(this::addContainer);
        sourceModel.deployments.forEach(this::addDeployment);
        sourceModel.externalSystems.forEach(this::addExternalSystem);
        sourceModel.runtimeFlows.forEach(this::addRuntimeFlow);

        sourceModel.applications.forEach(app -> app.componentIds.forEach(componentId -> addEdge(
                app.id.serialize(), componentId.serialize(), "OWNS", Map.of(SOURCE, "application.componentIds"))));
        sourceModel.entrypoints.forEach(entrypoint -> {
            if (entrypoint.componentId != null) {
                addEdge(
                        entrypoint.id.serialize(),
                        entrypoint.componentId.serialize(),
                        REL_STARTS_AT,
                        Map.of(SOURCE, "entrypoint.componentId"));
            }
        });
        sourceModel.interfaces.forEach(interfaceEntry -> {
            if (interfaceEntry.componentId != null) {
                addEdge(
                        interfaceEntry.id,
                        interfaceEntry.componentId.serialize(),
                        "EXPOSES",
                        Map.of(SOURCE, "interface.componentId"));
            }
        });
        sourceModel.containers.forEach(container -> container.componentIds.forEach(componentId ->
                addEdge(container.id, componentId.serialize(), "CONTAINS", Map.of(SOURCE, "container.componentIds"))));
        sourceModel.deployments.forEach(deployment -> deployment.appIds.forEach(
                appId -> addEdge(deployment.id, appId.serialize(), "DEPLOYS", Map.of(SOURCE, "deployment.appIds"))));
        sourceModel.dependencies.forEach(dependency -> addEdge(
                dependency.fromId.serialize(),
                dependency.toId.serialize(),
                REL_DEPENDS_ON,
                dependencyProperties(dependency)));
        addCallEdges(sourceModel);
        addFieldAccessEdges(sourceModel);
        sourceModel.runtimeFlows.forEach(this::addRuntimeFlowEdges);
        sourceModel.dataFlowPaths.forEach(this::addDataFlowPath);
        sourceModel.dataFlowPaths.forEach(this::addDataFlowEdges);
        linkDataFlowSinkReaders(sourceModel);
        addWorkflowLinks(sourceModel);
        addPipelineChains(sourceModel);
        computeDerivedProperties();
        this.store.projected = true;
    }

    private void addApplication(AppEntry app) {
        Vertex vertex = addVertex(app.id.serialize(), "Application", app.name);
        set(vertex, "kind", "application");
        set(vertex, "rootPath", app.rootPath);
        set(vertex, TECHNOLOGY, app.technology);
        set(vertex, "packagingType", app.packagingType);
        set(vertex, "role", app.role);
        set(vertex, "parentAppId", app.parentAppId != null ? app.parentAppId.serialize() : null);
    }

    private void addComponent(Component component) {
        Vertex vertex = addVertex(component.id.serialize(), "Component", component.name);
        set(vertex, "kind", "component");
        setLower(vertex, "type", component.type != null ? component.type.name() : null);
        setLower(vertex, "componentType", component.type != null ? component.type.name() : null);
        set(vertex, "qualifiedName", component.qualifiedName);
        set(vertex, "packageName", packageName(component.qualifiedName));
        set(vertex, "simpleName", component.name);
        set(vertex, "module", component.module != null ? component.module.serialize() : null);
        set(vertex, TECHNOLOGY, component.technology);
        set(vertex, "stereotypes", String.join(",", component.stereotypes));
        setSource(vertex, component.source);
    }

    private void addEntrypoint(Entrypoint entrypoint) {
        Vertex vertex = addVertex(entrypoint.id.serialize(), "Entrypoint", entrypoint.name);
        set(vertex, "kind", "entrypoint");
        setLower(vertex, "type", entrypoint.type != null ? entrypoint.type.name() : null);
        setLower(vertex, "entrypointType", entrypoint.type != null ? entrypoint.type.name() : null);
        set(vertex, "httpMethod", entrypoint.httpMethod);
        set(vertex, "path", entrypoint.path);
        set(vertex, "channelName", entrypoint.channelName);
        setLower(vertex, BROKER, entrypoint.broker != null ? entrypoint.broker.name() : null);
        set(vertex, TOPIC, entrypoint.topic);
        set(vertex, "parameters", String.join(",", entrypoint.parameters));
        set(vertex, "protocol", protocolFor(entrypoint));
        set(vertex, COMPONENT_ID, entrypoint.componentId != null ? entrypoint.componentId.serialize() : null);
        setSource(vertex, entrypoint.source);
    }

    private void addInterface(InterfaceEntry interfaceEntry) {
        Vertex vertex = addVertex(interfaceEntry.id, "Interface", interfaceEntry.name);
        set(vertex, "kind", "interface");
        setLower(vertex, "type", interfaceEntry.type);
        setLower(vertex, "interfaceType", interfaceEntry.type);
        set(vertex, "path", interfaceEntry.path);
        set(vertex, COMPONENT_ID, interfaceEntry.componentId != null ? interfaceEntry.componentId.serialize() : null);
        set(vertex, "module", interfaceEntry.module != null ? interfaceEntry.module.serialize() : null);
        set(vertex, TECHNOLOGY, interfaceEntry.technology);
        setLower(vertex, BROKER, interfaceEntry.broker != null ? interfaceEntry.broker.name() : null);
        set(vertex, TOPIC, interfaceEntry.topic);
        set(vertex, "externalServiceName", interfaceEntry.externalServiceName);
        setSource(vertex, interfaceEntry.source);
    }

    private void addContainer(Container container) {
        Vertex vertex = addVertex(container.id, "Container", container.name);
        set(vertex, "kind", "container");
        set(vertex, "appId", container.appId != null ? container.appId.serialize() : null);
        set(vertex, TECHNOLOGY, container.technology);
        set(vertex, DERIVED_FROM, container.derivedFrom);
    }

    private void addDeployment(DeploymentEntry deployment) {
        Vertex vertex = addVertex(deployment.id, "Deployment", deployment.name);
        set(vertex, "kind", "deployment");
        setLower(vertex, "type", deployment.type);
        setLower(vertex, "deploymentType", deployment.type);
        set(vertex, SOURCE, deployment.source);
        set(vertex, "ports", String.join(",", deployment.ports));
        set(vertex, "dependsOn", String.join(",", deployment.dependsOn));
        set(vertex, "roles", String.join(",", deployment.roles));
        set(vertex, "hosts", String.join(",", deployment.hosts));
    }

    private void addExternalSystem(ExternalSystem externalSystem) {
        Vertex vertex = addVertex(externalSystem.id, "ExternalSystem", externalSystem.name);
        set(vertex, "kind", "externalSystem");
        setLower(vertex, "type", externalSystem.kind);
        setLower(vertex, "externalSystemKind", externalSystem.kind);
        set(vertex, TECHNOLOGY, externalSystem.technology);
    }

    private void addRuntimeFlow(RuntimeFlow flow) {
        Vertex vertex = addVertex(flow.id, "RuntimeFlow", flow.id);
        set(vertex, "kind", "runtimeFlow");
        set(vertex, "entrypointId", flow.entrypointId != null ? flow.entrypointId.serialize() : null);
        set(vertex, "stepCount", flow.steps.size());
    }

    private void addRuntimeFlowEdges(RuntimeFlow flow) {
        addEdge(
                flow.id,
                flow.entrypointId != null ? flow.entrypointId.serialize() : "",
                "STARTED_BY",
                Map.of(SOURCE, "runtimeFlow.entrypointId"));
        Map<String, String> stepByCompId = new LinkedHashMap<>();
        for (RuntimeFlowStep step : flow.steps) {
            String stepId = flow.id + ":step:" + step.order;
            Vertex stepVertex = addVertex(stepId, "RuntimeFlowStep", step.componentName);
            set(stepVertex, "kind", "runtimeFlowStep");
            set(stepVertex, "flowId", flow.id);
            set(stepVertex, "order", step.order);
            set(stepVertex, COMPONENT_ID, step.componentId != null ? step.componentId.serialize() : null);
            setLower(stepVertex, "componentType", step.componentType);
            set(stepVertex, "via", step.via);
            addEdge(flow.id, stepId, "HAS_STEP", Map.of("order", step.order));
            addEdge(
                    stepId,
                    step.componentId != null ? step.componentId.serialize() : "",
                    "VISITS",
                    Map.of("via", Objects.toString(step.via, "")));
            if (step.componentId != null) {
                stepByCompId.putIfAbsent(step.componentId.serialize(), stepId);
            }
        }
        for (RuntimeFlow.FlowEdge edge : flow.edges) {
            if (edge.fromId == null || edge.toId == null) continue;
            String fromStep = stepByCompId.get(edge.fromId.serialize());
            String toStep = stepByCompId.get(edge.toId.serialize());
            if (fromStep != null && toStep != null && !fromStep.equals(toStep)) {
                Map<String, Object> props = new HashMap<>();
                props.put("label", Objects.toString(edge.label, ""));
                props.put("flowId", flow.id);
                props.put("fromComponentId", edge.fromId.serialize());
                props.put("toComponentId", edge.toId.serialize());
                addEdge(fromStep, toStep, "FLOW_CALLS", props);
            }
        }
    }

    private void addDataFlowPath(DataFlowPath path) {
        Vertex vertex = addVertex(path.id.serialize(), "DataFlowPath", path.id.serialize());
        set(vertex, "kind", "dataFlowPath");
        set(vertex, "entrypointId", path.entrypointId != null ? path.entrypointId.serialize() : null);
        set(vertex, "trackedParam", path.trackedParam);
        set(vertex, "stepCount", path.steps.size());
        set(vertex, "sinkCount", path.sinks.size());
    }

    private void addCallEdges(ArchitectureModel sourceModel) {
        for (CallEdge callEdge : sourceModel.callEdges) {
            if (callEdge.fromComponentId == null || callEdge.toComponentId == null) {
                continue;
            }
            Map<String, Object> props = new HashMap<>();
            props.put("fromMethod", Objects.toString(callEdge.fromMethod, ""));
            props.put("toMethod", Objects.toString(callEdge.toMethod, ""));
            props.put("callKind", Objects.toString(callEdge.callKind, ""));
            props.put(SOURCE, "call_graph");
            if (callEdge.receiverEvidence != null) {
                props.put("receiverEvidence", callEdge.receiverEvidence);
            }
            if (callEdge.receiverLocalName != null) {
                props.put("receiverLocalName", callEdge.receiverLocalName);
            }
            props.put("receiverConfidence", callEdge.receiverConfidence);
            props.put("ambiguous", callEdge.ambiguous);
            props.put("receiverExpansionCapped", callEdge.receiverExpansionCapped);
            props.put("paramMapping", formatMapping(callEdge.paramMapping, "->"));
            props.put("resolvedLiteralArgs", formatMapping(callEdge.resolvedLiteralArgs, "="));
            props.put("syntheticParamMappings", String.join(",", callEdge.syntheticParamMappings));
            props.put("assignedToVar", callEdge.assignedToVar);
            props.put("returnsTracked", callEdge.returnsTracked);
            props.put("killedTrackedNames", String.join(",", callEdge.killedTrackedNames));
            if (callEdge.source != null) {
                props.put(SOURCE_FILE, Objects.toString(callEdge.source.file, ""));
                props.put(SOURCE_LINE, callEdge.source.line);
            }
            addEdge(callEdge.fromComponentId.serialize(), callEdge.toComponentId.serialize(), "CALLS", props);
        }
    }

    private void addDataFlowEdges(DataFlowPath path) {
        String epVertexId = path.entrypointId != null ? path.entrypointId.serialize() : "";
        addEdge(
                epVertexId,
                path.id.serialize(),
                "ORIGINATES",
                Map.of("trackedParam", Objects.toString(path.trackedParam, "")));

        for (int i = 0; i < path.steps.size(); i++) {
            DataFlowStep step = path.steps.get(i);
            String stepId = path.id.serialize() + ":dfstep:" + i;
            Vertex stepVertex = addVertex(stepId, "DataFlowStep", step.componentName + "." + step.method);
            set(stepVertex, "stepIndex", step.index);
            set(stepVertex, COMPONENT_ID, step.componentId != null ? step.componentId.serialize() : null);
            set(stepVertex, "componentName", step.componentName);
            set(stepVertex, METHOD, step.method);
            set(stepVertex, "localName", step.localName);
            set(stepVertex, "pathId", path.id.serialize());
            addEdge(path.id.serialize(), stepId, "HAS_DATA_STEP", Map.of("stepIndex", step.index));
        }

        for (int i = 0; i < path.sinks.size(); i++) {
            DataFlowSink sink = path.sinks.get(i);
            String sinkId = path.id.serialize() + SINK_MARKER + i;
            addSinkVertex(sinkId, path, sink);
            addEdge(
                    path.id.serialize(),
                    sinkId,
                    "REACHES",
                    Map.of("sinkKind", sink.kind != null ? sink.kind.value() : ""));
            addSinkTargetEdge(sinkId, sink);
        }
        addDataFlowTopology(path);
    }

    private void addDataFlowTopology(DataFlowPath path) {
        String pathId = path.id.serialize();
        for (int nodeIdx = 0; nodeIdx < path.flowNodes.size(); nodeIdx++) {
            DataFlowNode node = path.flowNodes.get(nodeIdx);
            String nodeVertexId = flowNodeId(path, node.id);
            Vertex vertex = addVertex(nodeVertexId, "DataFlowNode", nodeLabel(node));
            set(vertex, "kind", "dataFlowNode");
            set(vertex, "pathId", pathId);
            set(vertex, "flowNodeId", node.id);
            set(vertex, "nodeOrder", nodeIdx);
            set(vertex, "nodeKind", node.kind != null ? node.kind.name().toLowerCase(Locale.ROOT) : null);
            set(vertex, COMPONENT_ID, node.componentId != null ? node.componentId.serialize() : null);
            set(vertex, "componentName", node.componentName);
            set(vertex, METHOD, node.method);
            set(vertex, "localName", node.localName);
            setSource(vertex, node.source);
            addEdge(pathId, nodeVertexId, "HAS_FLOW_NODE", Map.of("nodeKind", Objects.toString(node.kind, "")));
        }
        for (DataFlowEdge edge : path.flowEdges) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("edgeKind", edge.kind != null ? edge.kind.name().toLowerCase(Locale.ROOT) : "");
            props.put("branchId", edge.branchId);
            props.put("branchArmId", edge.branchArmId);
            props.put("label", edge.label);
            addEdge(flowNodeId(path, edge.fromNodeId), flowNodeId(path, edge.toNodeId), "FLOW_EDGE", props);
        }
        for (DataFlowBranch branch : path.branches) {
            String branchVertexId = branchId(path, branch.id);
            Vertex vertex = addVertex(branchVertexId, "DataFlowBranch", branch.id);
            set(vertex, "kind", "dataFlowBranch");
            set(vertex, "pathId", pathId);
            set(vertex, "branchId", branch.id);
            set(vertex, "branchKind", branch.kind != null ? branch.kind.name().toLowerCase(Locale.ROOT) : null);
            setSource(vertex, branch.source);
            addEdge(pathId, branchVertexId, "HAS_BRANCH", Map.of("branchKind", Objects.toString(branch.kind, "")));
            for (DataFlowBranchArm arm : branch.arms) {
                String armVertexId = branchArmId(path, arm.id);
                Vertex armVertex = addVertex(armVertexId, "DataFlowBranchArm", arm.label);
                set(armVertex, "kind", "dataFlowBranchArm");
                set(armVertex, "pathId", pathId);
                set(armVertex, "branchId", arm.branchId);
                set(armVertex, "branchArmId", arm.id);
                set(armVertex, "label", arm.label);
                set(armVertex, "entryNodeId", arm.entryNodeId);
                addEdge(
                        branchVertexId,
                        armVertexId,
                        "HAS_BRANCH_ARM",
                        Map.of("label", Objects.toString(arm.label, "")));
                addEdge(armVertexId, flowNodeId(path, arm.entryNodeId), "ARM_STARTS_AT", Map.of());
            }
        }
        for (int i = 0; i < path.sinks.size(); i++) {
            DataFlowSink sink = path.sinks.get(i);
            String sinkId = pathId + SINK_MARKER + i;
            path.flowNodes.stream()
                    .filter(node -> node.kind == DataFlowNode.Kind.SINK
                            && Objects.equals(node.componentId, sink.componentId)
                            && Objects.equals(node.method, sink.method))
                    .findFirst()
                    .ifPresent(node -> addEdge(flowNodeId(path, node.id), sinkId, "REACHES_NODE", Map.of()));
        }
    }

    private static String nodeLabel(DataFlowNode node) {
        String component = Objects.toString(node.componentName, "");
        String method = Objects.toString(node.method, "");
        if (!component.isBlank() && !method.isBlank()) return component + "." + method;
        if (!component.isBlank()) return component;
        return method;
    }

    private static String flowNodeId(DataFlowPath path, String nodeId) {
        return path.id.serialize() + ":node:" + nodeId;
    }

    private static String branchId(DataFlowPath path, String branchId) {
        return path.id.serialize() + ":branch:" + branchId;
    }

    private static String branchArmId(DataFlowPath path, String branchArmId) {
        return path.id.serialize() + ":arm:" + branchArmId;
    }

    private void addSinkVertex(String sinkId, DataFlowPath path, DataFlowSink sink) {
        Vertex sinkVertex = addVertex(sinkId, "DataFlowSink", sink.componentName);
        set(sinkVertex, "kind", "dataFlowSink");
        set(sinkVertex, "sinkKind", sink.kind != null ? sink.kind.value() : null);
        set(sinkVertex, "pathId", path.id.serialize());
        set(sinkVertex, COMPONENT_ID, sink.componentId != null ? sink.componentId.serialize() : null);
        set(sinkVertex, METHOD, sink.method);
        set(sinkVertex, FIELD_NAME, sink.fieldName);
        set(
                sinkVertex,
                FIELD_OWNER_COMPONENT_ID,
                sink.fieldOwnerComponentId != null ? sink.fieldOwnerComponentId.serialize() : null);
        set(sinkVertex, "channel", sink.channel);
        setLower(sinkVertex, BROKER, sink.broker != null ? sink.broker.name() : null);
        set(sinkVertex, TOPIC, sink.topic);
        set(sinkVertex, "topicPropertyKey", sink.topicPropertyKey);
        set(sinkVertex, "payloadType", sink.payloadType);
        set(sinkVertex, "entityType", sink.entityType);
        set(sinkVertex, "repositoryOperation", sink.repositoryOperation);
        set(sinkVertex, "linkEvidence", sink.linkEvidence);
        set(sinkVertex, "calleeQualifiedName", sink.calleeQualifiedName);
        set(
                sinkVertex,
                "callerComponentId",
                sink.callerComponentId != null ? sink.callerComponentId.serialize() : null);
        setSource(sinkVertex, sink.source);
    }

    private void addSinkTargetEdge(String sinkId, DataFlowSink sink) {
        if (sink.kind == DataFlowSink.Kind.STORE && sink.fieldOwnerComponentId != null) {
            addEdge(
                    sinkId,
                    sink.fieldOwnerComponentId.serialize(),
                    "ON_FIELD",
                    Map.of(FIELD_NAME, Objects.toString(sink.fieldName, "")));
        } else if (sink.componentId != null) {
            addEdge(
                    sinkId,
                    sink.componentId.serialize(),
                    "AT_COMPONENT",
                    Map.of(METHOD, Objects.toString(sink.method, "")));
        }
    }

    private void linkDataFlowSinkReaders(ArchitectureModel sourceModel) {
        for (DataFlowPath path : sourceModel.dataFlowPaths) {
            for (int i = 0; i < path.sinks.size(); i++) {
                linkSinkReaders(path, i, path.sinks.get(i));
            }
        }
    }

    private void linkSinkReaders(DataFlowPath path, int sinkIndex, DataFlowSink sink) {
        if (sink.linkedPathIds == null || sink.linkedPathIds.isEmpty()) return;
        if (sink.kind != DataFlowSink.Kind.STORE
                && sink.kind != DataFlowSink.Kind.MESSAGING
                && sink.kind != DataFlowSink.Kind.EVENT_BUS) return;
        String sinkId = path.id.serialize() + SINK_MARKER + sinkIndex;
        Map<String, Object> props = new HashMap<>();
        props.put("linkKind", sink.kind.value());
        if (sink.kind == DataFlowSink.Kind.STORE) {
            props.put(VIA_FIELD, Objects.toString(sink.fieldName, ""));
            props.put(
                    FIELD_OWNER_COMPONENT_ID,
                    sink.fieldOwnerComponentId != null ? sink.fieldOwnerComponentId.serialize() : "");
        } else {
            props.put(VIA_CHANNEL, Objects.toString(sink.channel, ""));
        }
        for (dev.dominikbreu.archlens.model.ids.DataFlowPathId downstreamPathId : sink.linkedPathIds) {
            addEdge(sinkId, downstreamPathId.serialize(), "LINKS_TO", props);
        }
    }

    private void addWorkflowLinks(ArchitectureModel sourceModel) {
        for (WorkflowLink link : new WorkflowLinker().link(sourceModel)) {
            Map<String, Object> props = new HashMap<>();
            props.put("kind", link.kind().name());
            props.put(CONFIDENCE, link.confidence());
            props.put("fromEntrypointId", Objects.toString(link.fromEntrypointId(), ""));
            props.put("toEntrypointId", Objects.toString(link.toEntrypointId(), ""));
            if (link.channel() != null) {
                props.put("channel", link.channel());
                props.put(VIA_CHANNEL, link.channel());
            }
            if (link.fieldOwnerComponentId() != null) {
                props.put(FIELD_OWNER_COMPONENT_ID, link.fieldOwnerComponentId());
            }
            if (link.fieldName() != null) {
                props.put(FIELD_NAME, link.fieldName());
                props.put(VIA_FIELD, link.fieldName());
            }
            if (link.entityType() != null) props.put("entityType", link.entityType());
            if (link.repositoryOperation() != null) props.put("repositoryOperation", link.repositoryOperation());
            if (link.evidence() != null) props.put("evidence", link.evidence());
            addEdge(link.fromPathId(), link.toPathId(), "WORKFLOW_LINK", props);
        }
    }

    private void addPipelineChains(ArchitectureModel sourceModel) {
        List<Chain> chains = new PipelineGraphBuilder().build(sourceModel, 32);
        int chainIdx = 0;
        for (Chain chain : chains) {
            chainIdx++;
            String chainId = "chain:" + chainIdx;
            addChainVertex(chainId, chain);
            addChainSegmentEdges(chainId, chain, sourceModel);
        }
    }

    private void addChainVertex(String chainId, Chain chain) {
        Segment root = chain.segments.getFirst();
        String rootEpId =
                (root.path != null && root.path.entrypointId != null) ? root.path.entrypointId.serialize() : "";
        Vertex vertex = addVertex(chainId, "PipelineChain", chainId);
        set(vertex, "kind", "pipelineChain");
        set(vertex, "segmentCount", chain.segments.size());
        set(vertex, "rootEntrypointId", rootEpId);
        StringBuilder linkKinds = new StringBuilder();
        for (int i = 1; i < chain.segments.size(); i++) {
            DataFlowSink in = chain.segments.get(i).incomingSink;
            if (!linkKinds.isEmpty()) linkKinds.append(',');
            linkKinds.append(in != null && in.kind != null ? in.kind.value() : "");
        }
        set(vertex, "linkKinds", linkKinds.toString());
    }

    private void addChainSegmentEdges(String chainId, Chain chain, ArchitectureModel sourceModel) {
        for (int i = 0; i < chain.segments.size(); i++) {
            Segment seg = chain.segments.get(i);
            Map<String, Object> edgeProps = new HashMap<>();
            edgeProps.put("segmentIndex", i);
            DataFlowSink in = seg.incomingSink;
            if (in != null) {
                edgeProps.put("linkKind", in.kind != null ? in.kind.value() : "");
                edgeProps.put("incomingSinkId", incomingSinkId(chain, i, sourceModel));
                if (in.kind == DataFlowSink.Kind.STORE) {
                    edgeProps.put(VIA_FIELD, Objects.toString(in.fieldName, ""));
                    edgeProps.put(
                            FIELD_OWNER_COMPONENT_ID,
                            in.fieldOwnerComponentId != null ? in.fieldOwnerComponentId.serialize() : "");
                } else {
                    edgeProps.put(VIA_CHANNEL, Objects.toString(in.channel, ""));
                }
            }
            addEdge(chainId, seg.path.id.serialize(), "HAS_SEGMENT", edgeProps);
        }
    }

    private String incomingSinkId(Chain chain, int segmentIndex, ArchitectureModel sourceModel) {
        if (segmentIndex == 0) return "";
        Segment prev = chain.segments.get(segmentIndex - 1);
        DataFlowSink target = chain.segments.get(segmentIndex).incomingSink;
        for (int i = 0; i < prev.path.sinks.size(); i++) {
            if (prev.path.sinks.get(i) == target) return prev.path.id.serialize() + SINK_MARKER + i;
        }
        return "";
    }

    private Map<String, Object> dependencyProperties(Dependency dependency) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("id", dependency.id != null ? dependency.id.serialize() : null);
        properties.put("kind", Objects.toString(dependency.kind, ""));
        properties.put("dependencyKind", Objects.toString(dependency.kind, ""));
        properties.put(DERIVED_FROM, Objects.toString(dependency.derivedFrom, ""));
        properties.put(CONFIDENCE, dependency.confidence);
        properties.put("isRuntimeRelevant", isRuntimeRelevant(dependency));
        properties.put("isCondensable", isCondensable(dependency));
        properties.put("weight", dependency.confidence);
        Component from = componentById(dependency.fromId.serialize());
        Component to = componentById(dependency.toId.serialize());
        properties.put("fromModule", from != null ? Objects.toString(from.module, "") : "");
        properties.put("toModule", to != null ? Objects.toString(to.module, "") : "");
        properties.put("isCrossModule", from != null && to != null && !Objects.equals(from.module, to.module));
        return properties;
    }

    private void addFieldAccessEdges(ArchitectureModel sourceModel) {
        Map<dev.dominikbreu.archlens.model.ids.FieldRef, List<FieldAccess>> readsByState = new LinkedHashMap<>();
        Map<dev.dominikbreu.archlens.model.ids.FieldRef, List<FieldAccess>> writesByState = new LinkedHashMap<>();
        for (FieldAccess access : sourceModel.fieldAccesses) {
            indexFieldAccess(access, readsByState, writesByState);
        }
        linkStateHandoffs(sourceModel, writesByState, readsByState);
    }

    private void indexFieldAccess(
            FieldAccess access,
            Map<dev.dominikbreu.archlens.model.ids.FieldRef, List<FieldAccess>> readsByState,
            Map<dev.dominikbreu.archlens.model.ids.FieldRef, List<FieldAccess>> writesByState) {
        if (access.kind == null || access.componentId == null || access.fieldBinding == null) {
            return;
        }
        dev.dominikbreu.archlens.model.ids.ComponentId fieldOwner =
                access.fieldBinding instanceof dev.dominikbreu.archlens.model.ids.FieldBinding.CrossComponent(var ref)
                        ? ref.owner()
                        : access.componentId;
        String fieldName = access.fieldBinding.fieldName();
        String edgeLabel = access.kind == FieldAccess.Kind.WRITE ? REL_WRITES_STATE : REL_READS_STATE;
        addEdge(access.componentId.serialize(), fieldOwner.serialize(), edgeLabel, fieldAccessProperties(access));
        dev.dominikbreu.archlens.model.ids.FieldRef key =
                new dev.dominikbreu.archlens.model.ids.FieldRef(fieldOwner, fieldName);
        if (access.kind == FieldAccess.Kind.WRITE) {
            writesByState.computeIfAbsent(key, ignored -> new ArrayList<>()).add(access);
        } else if (access.kind == FieldAccess.Kind.READ) {
            readsByState.computeIfAbsent(key, ignored -> new ArrayList<>()).add(access);
        }
    }

    private void linkStateHandoffs(
            ArchitectureModel sourceModel,
            Map<dev.dominikbreu.archlens.model.ids.FieldRef, List<FieldAccess>> writesByState,
            Map<dev.dominikbreu.archlens.model.ids.FieldRef, List<FieldAccess>> readsByState) {
        for (Map.Entry<dev.dominikbreu.archlens.model.ids.FieldRef, List<FieldAccess>> entry :
                writesByState.entrySet()) {
            List<FieldAccess> reads = readsByState.get(entry.getKey());
            if (reads == null) {
                continue;
            }
            for (FieldAccess write : entry.getValue()) {
                for (FieldAccess read : reads) {
                    linkWriteToRead(write, read, sourceModel);
                }
            }
        }
    }

    private void linkWriteToRead(FieldAccess write, FieldAccess read, ArchitectureModel sourceModel) {
        if (Objects.equals(write.componentId, read.componentId)) {
            if (!Objects.equals(write.method, read.method)) {
                propagateStateHandoffThroughCallers(write, read, sourceModel);
            }
        } else {
            addEdge(
                    write.componentId.serialize(),
                    read.componentId.serialize(),
                    REL_STATE_HANDOFF,
                    stateHandoffProperties(write, read));
        }
    }

    private Map<String, Object> fieldAccessProperties(FieldAccess access) {
        Map<String, Object> properties = new HashMap<>();
        String fieldName;
        if (access.fieldBinding != null) {
            fieldName = access.fieldBinding.fieldName();
        } else {
            fieldName = "";
        }
        dev.dominikbreu.archlens.model.ids.ComponentId fieldOwner;
        if (access.fieldBinding instanceof dev.dominikbreu.archlens.model.ids.FieldBinding.CrossComponent(var ref)) {
            fieldOwner = ref.owner();
        } else {
            fieldOwner = (access.componentId != null ? access.componentId : null);
        }
        properties.put(FIELD_NAME, fieldName);
        properties.put(FIELD_OWNER_COMPONENT_ID, fieldOwner != null ? fieldOwner.serialize() : "");
        properties.put(METHOD, Objects.toString(access.method, ""));
        properties.put("accessKind", access.kind != null ? access.kind.name().toLowerCase(Locale.ROOT) : "");
        properties.put(SOURCE, "field_access");
        if (access.source != null) {
            properties.put(SOURCE_FILE, Objects.toString(access.source.file, ""));
            properties.put(SOURCE_LINE, access.source.line);
            properties.put(CONFIDENCE, access.source.confidence);
        }
        return properties;
    }

    private Map<String, Object> stateHandoffProperties(FieldAccess write, FieldAccess read) {
        Map<String, Object> properties = new HashMap<>();
        String writeFieldName;
        if (write.fieldBinding != null) {
            writeFieldName = write.fieldBinding.fieldName();
        } else {
            writeFieldName = "";
        }
        dev.dominikbreu.archlens.model.ids.ComponentId writeFieldOwner;
        if (write.fieldBinding instanceof dev.dominikbreu.archlens.model.ids.FieldBinding.CrossComponent(var ref)) {
            writeFieldOwner = ref.owner();
        } else {
            writeFieldOwner = (write.componentId != null ? write.componentId : null);
        }
        properties.put(FIELD_NAME, writeFieldName);
        properties.put(FIELD_OWNER_COMPONENT_ID, writeFieldOwner != null ? writeFieldOwner.serialize() : "");
        properties.put("writerMethod", Objects.toString(write.method, ""));
        properties.put("readerMethod", Objects.toString(read.method, ""));
        properties.put(SOURCE, "field_access");
        properties.put(CONFIDENCE, 0.8);
        return properties;
    }

    private void propagateStateHandoffThroughCallers(
            FieldAccess write, FieldAccess read, ArchitectureModel sourceModel) {
        Set<dev.dominikbreu.archlens.model.ids.ComponentId> writerCallers =
                collectCallers(sourceModel, write.componentId, write.method);
        Set<dev.dominikbreu.archlens.model.ids.ComponentId> readerCallers =
                collectCallers(sourceModel, read.componentId, read.method);
        for (dev.dominikbreu.archlens.model.ids.ComponentId caller1 : writerCallers) {
            for (dev.dominikbreu.archlens.model.ids.ComponentId caller2 : readerCallers) {
                if (!caller1.equals(caller2)) {
                    addEdge(
                            caller1.serialize(),
                            caller2.serialize(),
                            REL_STATE_HANDOFF,
                            stateHandoffProperties(write, read));
                }
            }
        }
    }

    private Set<dev.dominikbreu.archlens.model.ids.ComponentId> collectCallers(
            ArchitectureModel sourceModel,
            dev.dominikbreu.archlens.model.ids.ComponentId targetComponent,
            String targetMethod) {
        Set<dev.dominikbreu.archlens.model.ids.ComponentId> callers = new HashSet<>();
        for (CallEdge e : sourceModel.callEdges) {
            if (e.fromComponentId == null || e.toComponentId == null || e.toMethod == null) {
                continue;
            }
            if (targetComponent.equals(e.toComponentId) && targetMethod.equals(e.toMethod)) {
                callers.add(e.fromComponentId);
            }
        }
        return callers;
    }

    private void computeDerivedProperties() {
        Set<String> entrypointReachable = reachableFromEntrypoints();
        for (Vertex vertex : store.verticesById.values()) {
            int fanIn = countEdges(vertex, Direction.IN, REL_DEPENDS_ON);
            int fanOut = countEdges(vertex, Direction.OUT, REL_DEPENDS_ON);
            set(vertex, "fanIn", fanIn);
            set(vertex, "fanOut", fanOut);
            set(vertex, "degree", fanIn + fanOut);
            set(
                    vertex,
                    "entrypointReachable",
                    entrypointReachable.contains(vertex.id().toString()));
            if ("Component".equals(vertex.label())) {
                int ownedEntrypoints = countEdges(vertex, Direction.IN, REL_STARTS_AT);
                ArchitectureRelevanceScorer.Relevance relevance = ArchitectureRelevanceScorer.score(
                        componentById(vertex.id().toString()),
                        new ArchitectureRelevanceScorer.Metrics(
                                fanIn,
                                fanOut,
                                ownedEntrypoints,
                                countEdges(vertex, Direction.OUT, REL_READS_STATE),
                                countEdges(vertex, Direction.OUT, REL_WRITES_STATE),
                                countCrossComponentStateEdges(vertex, REL_READS_STATE),
                                countCrossComponentStateEdges(vertex, REL_WRITES_STATE),
                                countEdges(vertex, Direction.IN, REL_STATE_HANDOFF),
                                countEdges(vertex, Direction.OUT, REL_STATE_HANDOFF)));
                set(vertex, "ownedEntrypointCount", ownedEntrypoints);
                set(vertex, "workflowRelevant", relevance.workflowRelevant());
                set(vertex, "businessRelevant", relevance.businessRelevant());
                set(vertex, "infrastructureRole", relevance.infrastructureRole());
                set(vertex, "noiseScore", relevance.noiseScore());
                set(vertex, "workflowBridgeScore", relevance.workflowBridgeScore());
                set(vertex, "architecturalWeight", relevance.architecturalWeight());
                set(vertex, "primaryRole", relevance.primaryRole());
                set(vertex, "supportRole", relevance.supportRole());
                set(vertex, "agentCategory", relevance.agentCategory());
                set(vertex, "classificationEvidence", relevance.classificationEvidence());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> reachableFromEntrypoints() {
        return store.g
                .V()
                .hasLabel("Entrypoint")
                .aggregate("seen")
                .repeat(org.apache
                        .tinkerpop
                        .gremlin
                        .process
                        .traversal
                        .dsl
                        .graph
                        .__
                        .out(
                                REL_STARTS_AT,
                                "CALLS",
                                REL_DEPENDS_ON,
                                "VISITS",
                                "ORIGINATES",
                                "REACHES",
                                "LINKS_TO",
                                "WORKFLOW_LINK",
                                REL_WRITES_STATE,
                                REL_READS_STATE,
                                REL_STATE_HANDOFF,
                                "ON_FIELD",
                                "AT_COMPONENT",
                                "HAS_SEGMENT")
                        .where(org.apache.tinkerpop.gremlin.process.traversal.P.without("seen"))
                        .aggregate("seen"))
                .cap("seen")
                .unfold()
                .id()
                .map(Object::toString)
                .toSet();
    }

    private int countEdges(Vertex vertex, Direction direction, String label) {
        int count = 0;
        Iterator<Edge> edges = vertex.edges(direction, label);
        while (edges.hasNext()) {
            edges.next();
            count++;
        }
        return count;
    }

    private int countCrossComponentStateEdges(Vertex vertex, String label) {
        int count = 0;
        String vertexId = vertex.id().toString();
        Iterator<Edge> edges = vertex.edges(Direction.OUT, label);
        while (edges.hasNext()) {
            Edge edge = edges.next();
            if (!vertexId.equals(edge.inVertex().id().toString())) {
                count++;
            }
        }
        return count;
    }

    private Vertex addVertex(String id, String label, String name) {
        if (StringUtils.isBlank(id)) {
            id = label + ":" + store.verticesById.size();
        }
        GraphNodeId key = GraphNodeId.of(id);
        Vertex existing = store.verticesById.get(key);
        if (existing != null) {
            return existing;
        }
        Vertex vertex = store.graph.addVertex(T.id, id, T.label, label);
        set(vertex, "name", name);
        store.verticesById.put(key, vertex);
        return vertex;
    }

    private void addEdge(String fromId, String toId, String label, Map<String, ?> properties) {
        if (fromId == null || toId == null || fromId.isBlank() || toId.isBlank()) {
            return;
        }
        Vertex from = store.verticesById.get(GraphNodeId.of(fromId));
        Vertex to = store.verticesById.get(GraphNodeId.of(toId));
        if (from == null || to == null) {
            return;
        }
        Edge edge = from.addEdge(label, to);
        properties.forEach((key, value) -> set(edge, key, value));
    }

    private Component componentById(String componentId) {
        if (model == null || componentId == null) {
            return null;
        }
        return model.components.stream()
                .filter(component -> componentId.equals(component.id.serialize()))
                .findFirst()
                .orElse(null);
    }

    private static String packageName(String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }
        int index = qualifiedName.lastIndexOf('.');
        if (index > 0) {
            return qualifiedName.substring(0, index);
        } else {
            return "";
        }
    }

    private static String protocolFor(Entrypoint entrypoint) {
        if (entrypoint.type == null) {
            return null;
        }
        return switch (entrypoint.type) {
            case REST_ENDPOINT -> "http";
            case JMS_CONSUMER -> "jms";
            case MESSAGING_CONSUMER, MESSAGING_PRODUCER -> "messaging";
            case CDI_EVENT_OBSERVER -> "event";
            case SCHEDULER -> "scheduler";
            case EJB_BUSINESS_METHOD -> "ejb";
            case RMI_ENDPOINT -> "rmi";
            case MAIN_METHOD -> "main";
            case EVENT_BUS_CONSUMER -> "event-bus";
            case WEBSOCKET_ENDPOINT -> "websocket";
            case SSE_ENDPOINT -> "sse";
            case GRPC_METHOD -> "grpc";
            case ENTITY_EVENT_LISTENER -> "entity-event";
            case UNKNOWN -> "unknown";
        };
    }

    private static boolean isRuntimeRelevant(Dependency dependency) {
        String kind = Objects.toString(dependency.kind, "").toLowerCase(Locale.ROOT);
        return kind.contains("injection")
                || kind.contains("method")
                || kind.contains("event")
                || kind.contains("client")
                || kind.contains("message");
    }

    private boolean isCondensable(Dependency dependency) {
        Component from = componentById(dependency.fromId.serialize());
        Component to = componentById(dependency.toId.serialize());
        return isUtilityLike(from) || isUtilityLike(to);
    }

    private static boolean isUtilityLike(Component component) {
        return component != null
                && component.type != null
                && ("UTILITY".equals(component.type.name()) || "UNKNOWN".equals(component.type.name()));
    }

    private static String formatMapping(Map<String, String> mapping, String separator) {
        if (mapping == null || mapping.isEmpty()) {
            return "";
        }
        return mapping.entrySet().stream()
                .map(entry -> entry.getKey() + separator + entry.getValue())
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static void set(Vertex vertex, String key, Object value) {
        if (value != null && !Objects.toString(value).isBlank()) {
            vertex.property(key, value);
        }
    }

    private static void setLower(Vertex vertex, String key, String value) {
        if (value != null && !value.isBlank()) {
            vertex.property(key, value.toLowerCase(Locale.ROOT));
        }
    }

    private static void set(Edge edge, String key, Object value) {
        if (value != null && !Objects.toString(value).isBlank()) {
            edge.property(key, value);
        }
    }

    private static void setSource(Vertex vertex, SourceInfo source) {
        if (source == null) {
            return;
        }
        set(vertex, SOURCE_FILE, source.file);
        set(vertex, SOURCE_LINE, source.line);
        set(vertex, DERIVED_FROM, source.derivedFrom);
        set(vertex, CONFIDENCE, source.confidence);
    }
}
