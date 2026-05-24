package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.extractor.objectflow.ObjectFlowIndex;
import dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndex;
import dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndexBuilder;
import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spoon.reflect.CtModel;

class CallGraphExtractorTest extends ExtractorTestBase {

    private static ArchitectureModel model;

    @BeforeAll
    static void buildModel() {
        CtModel ctModel = scan("quarkus-sample");
        model = emptyModel(QUARKUS_APP_ID);
        new QuarkusExtractor().extract(ctModel.getAllTypes(), model, QUARKUS_APP_ID);
        new DependencyExtractor().extract(ctModel, model);
        new CallGraphExtractor().extract(ctModel, model);
    }

    @Test
    void extractsEdgeFromResourceToService() {
        assertThat(model.callEdges)
                .as("OrderResource.get -> OrderService.find edge")
                .anySatisfy(e -> {
                    assertThat(e.fromComponentId).isEqualTo("comp:com.example.api.OrderResource");
                    assertThat(e.fromMethod).isEqualTo("get");
                    assertThat(e.toComponentId).isEqualTo("comp:com.example.service.OrderService");
                    assertThat(e.toMethod).isEqualTo("find");
                    assertThat(e.callKind).isEqualTo("direct");
                });
    }

    @Test
    void sourceFactBackedCallGraphPreservesFieldInjectionEdges() {
        CtModel ctModel = scan("quarkus-sample");
        ArchitectureModel sourceModel = emptyModel(QUARKUS_APP_ID);
        new QuarkusExtractor().extract(ctModel.getAllTypes(), sourceModel, QUARKUS_APP_ID);
        new DependencyExtractor().extract(ctModel, sourceModel);

        SourceFactIndex sourceFacts = new SourceFactIndexBuilder().build(ctModel, "quarkus-sample", 1);
        new CallGraphExtractor(ObjectFlowIndex.empty(), sourceFacts).extract(ctModel, sourceModel);

        assertThat(sourceModel.callEdges)
                .anySatisfy(edge -> {
                    assertThat(edge.fromComponentId).isEqualTo("comp:com.example.api.OrderResource");
                    assertThat(edge.fromMethod).isEqualTo("get");
                    assertThat(edge.toComponentId).isEqualTo("comp:com.example.service.OrderService");
                    assertThat(edge.toMethod).isEqualTo("find");
                });
    }

    @Test
    void sourceFactBackedCallGraphResolvesConstructorInjectedInterfaceFields() {
        CtModel ctModel = scan("constructor-injection-sample");
        ArchitectureModel sourceModel = emptyModel("app:constructor-injection-sample");
        sourceModel.components.add(component("com.example.constructor.AccountController"));
        sourceModel.components.add(component("com.example.constructor.AccountService"));

        SourceFactIndex sourceFacts =
                new SourceFactIndexBuilder().build(ctModel, "constructor-injection-sample", 1);
        new CallGraphExtractor(ObjectFlowIndex.empty(), sourceFacts).extract(ctModel, sourceModel);

        assertThat(sourceModel.callEdges)
                .anySatisfy(edge -> {
                    assertThat(edge.fromComponentId).isEqualTo("comp:com.example.constructor.AccountController");
                    assertThat(edge.fromMethod).isEqualTo("get");
                    assertThat(edge.toComponentId).isEqualTo("comp:com.example.constructor.AccountService");
                    assertThat(edge.toMethod).isEqualTo("getById");
                    assertThat(edge.receiverEvidence).isEqualTo("legacy-field-read");
                });
    }

    @Test
    void extractsEdgeFromServiceToRepository() {
        assertThat(model.callEdges)
                .as("OrderService.find -> OrderRepository.findById edge")
                .anySatisfy(e -> {
                    assertThat(e.fromComponentId).isEqualTo("comp:com.example.service.OrderService");
                    assertThat(e.fromMethod).isEqualTo("find");
                    assertThat(e.toComponentId).isEqualTo("comp:com.example.repository.OrderRepository");
                    assertThat(e.toMethod).isEqualTo("findById");
                    assertThat(e.callKind).isEqualTo("direct");
                });
    }

    @Test
    void edgeIdsAreUnique() {
        List<String> ids = model.callEdges.stream().map(e -> e.id).toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void doesNotRecordIntraComponentCalls() {
        assertThat(model.callEdges)
                .as("no self-referential edges")
                .noneMatch(e -> e.fromComponentId.equals(e.toComponentId));
    }

    @Test
    void noEdgesFromUnknownComponents() {
        var compIds = model.components.stream().map(c -> c.id).toList();
        assertThat(model.callEdges).allSatisfy(e -> {
            assertThat(compIds).contains(e.fromComponentId);
            assertThat(compIds).contains(e.toComponentId);
        });
    }

    @Test
    void edgeSourceIsPopulated() {
        assertThat(model.callEdges)
                .as("all edges have source info")
                .allSatisfy(e -> assertThat(e.source).isNotNull());
    }

    @Test
    void recordsSynthesisedParamMappingForTernaryArg() {
        // findTernary calls orderRepository.findById(id != null ? id : 0L)
        // — argument is a CtConditional wrapping CtVariableRead 'id', so buildParamMapping
        // descends into it and flags the callee param as synthesised.
        assertThat(model.callEdges)
                .as("OrderService.findTernary -> OrderRepository.findById has synthesised mapping")
                .anySatisfy(e -> {
                    assertThat(e.fromMethod).isEqualTo("findTernary");
                    assertThat(e.toMethod).isEqualTo("findById");
                    assertThat(e.paramMapping).containsEntry("id", "id");
                    assertThat(e.syntheticParamMappings).contains("id");
                });
    }

    @Test
    void recordsSynthesisedParamMappingForNestedInvocationArg() {
        // findWrapped calls orderRepository.findById(Long.valueOf(String.valueOf(raw)))
        // — argument is a nested CtInvocation, descend should reach 'raw'.
        assertThat(model.callEdges)
                .as("OrderService.findWrapped -> OrderRepository.findById has synthesised mapping")
                .anySatisfy(e -> {
                    assertThat(e.fromMethod).isEqualTo("findWrapped");
                    assertThat(e.toMethod).isEqualTo("findById");
                    assertThat(e.paramMapping).containsEntry("raw", "id");
                    assertThat(e.syntheticParamMappings).contains("id");
                });
    }

    @Test
    void scansOutboundFileSinkSites() {
        assertThat(model.outboundSinkSites)
                .as("OrderRepository.archive uses java.nio.file.Files → FILE_OUTBOUND site")
                .anySatisfy(s -> {
                    assertThat(s.componentId).contains("OrderRepository");
                    assertThat(s.method).isEqualTo("archive");
                    assertThat(s.calleeQualifiedName).startsWith("java.nio.file.Files");
                    assertThat(s.calleeMethod).isEqualTo("writeString");
                    assertThat(s.kind.name()).isEqualTo("FILE_OUTBOUND");
                });
    }

    @Test
    void scansEmitterSendAsMessagingOutboundSink() {
        // KafkaService.publishAudit calls auditEmitter.send(msg) on an injected
        // org.eclipse.microprofile.reactive.messaging.Emitter field. The Emitter type
        // is not a known component, so extractFromMethod cannot emit a CallEdge —
        // extractOutboundSinkSites must produce a MESSAGING OutboundSinkSite instead.
        assertThat(model.outboundSinkSites)
                .as("KafkaService.publishAudit -> Emitter.send is a MESSAGING sink")
                .anySatisfy(s -> {
                    assertThat(s.componentId).contains("KafkaService");
                    assertThat(s.method).isEqualTo("publishAudit");
                    assertThat(s.calleeMethod).isEqualTo("send");
                    assertThat(s.kind).isEqualTo(DataFlowSink.Kind.MESSAGING);
                });
    }

    @Test
    void endToEndIncomingCacheSchedulerEmitterPipelineProducesLinkedSinks() {
        // Real-world three-bean pipeline (OrderIngest → OrderBuffer ← OrderForwarder):
        //   @Incoming("orders-in") OrderIngest.consume(payload)
        //     → OrderBuffer.store(value) → cache.put("k", value)         [STORE sink on 'cache']
        //   @Scheduled OrderForwarder.forward()
        //     → OrderBuffer.peek() → cache.get("k")
        //     → outEmitter.send(v)                                       [MESSAGING sink]
        // After running the full extraction + DataFlowTracer pipeline:
        //   • the consumer path must carry a STORE sink on 'cache' owned by OrderBuffer
        //   • the scheduler path must carry a MESSAGING sink (Emitter.send)
        //   • the consumer's STORE sink must link to the scheduler path id
        List<DataFlowPath> paths = new DataFlowTracer().trace(model);

        DataFlowPath consumerPath = paths.stream()
                .filter(p -> p.entrypointId.contains("OrderIngest") && p.entrypointId.contains("#consume"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no data-flow path traced from OrderIngest.consume"));

        DataFlowPath schedulerPath = paths.stream()
                .filter(p -> p.entrypointId.contains("OrderForwarder") && p.entrypointId.contains("#forward"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no data-flow path traced from OrderForwarder.forward"));

        DataFlowSink storeSink = consumerPath.sinks.stream()
                .filter(s -> s.kind == DataFlowSink.Kind.STORE
                        && "cache".equals(s.fieldName)
                        && s.fieldOwnerComponentId != null
                        && s.fieldOwnerComponentId.contains("OrderBuffer"))
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError("OrderIngest.consume did not emit a STORE sink on OrderBuffer.cache"));

        DataFlowSink messagingSink = schedulerPath.sinks.stream()
                .filter(s -> s.kind == DataFlowSink.Kind.MESSAGING && "send".equals(s.method))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("OrderForwarder.forward did not emit a MESSAGING sink via Emitter.send"));

        assertThat(messagingSink.channel)
                .as("MESSAGING sink must carry the channel name from @Channel annotation")
                .isEqualTo("orders-out");

        DataFlowPath nextStagePath = paths.stream()
                .filter(p -> p.entrypointId.contains("OrderNextStage") && p.entrypointId.contains("#process"))
                .findFirst()
                .orElse(null);
        // OrderNextStage.process has no parameters and no call edges → no path with sinks,
        // so only assert the link if a path was traced.
        if (nextStagePath != null) {
            assertThat(messagingSink.linkedPathIds)
                    .as("MESSAGING sink on 'orders-out' must link to OrderNextStage consumer path")
                    .contains(nextStagePath.id);
        }

        assertThat(storeSink.linkedPathIds)
                .as("STORE sink on OrderBuffer.cache must link to the OrderForwarder reader path")
                .contains(schedulerPath.id);
    }

    @Test
    void endToEndCacheSchedulerPipelineProducesComponentStateHandoffEdge() {
        ArchitectureModel traced = new ArchitectureModel("test");
        traced.components.addAll(model.components);
        traced.entrypoints.addAll(model.entrypoints);
        traced.dependencies.addAll(model.dependencies);
        traced.callEdges.addAll(model.callEdges);
        traced.fieldAccesses.addAll(model.fieldAccesses);
        traced.outboundSinkSites.addAll(model.outboundSinkSites);
        traced.dataFlowPaths.addAll(new DataFlowTracer().trace(traced));

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(traced);

        assertThat(graph.findEdges("STATE_HANDOFF", java.util.Map.of("fieldName", "cache"), 20))
                .as("component graph must expose OrderBuffer.cache handoff to OrderForwarder")
                .anyMatch(edge -> edge.fromId().contains("OrderBuffer")
                        && edge.toId().contains("OrderForwarder")
                        && "cache".equals(edge.properties().get("fieldName")));
    }

    @Test
    void loggerFieldIsExcludedFromSharedStateSeeding() {
        // KafkaService has a Logger field ('log') added to the quarkus-sample fixture.
        // Logger is in SHARED_STATE_TYPE_DENYLIST, so buildSharedStateFieldSet must
        // exclude it — the tracer must not produce any path seeded from 'log'.
        List<DataFlowPath> paths = new DataFlowTracer().trace(model);
        assertThat(paths)
                .as("no DataFlowPath should be seeded from a Logger field")
                .noneMatch(p -> p.entrypointId.contains("KafkaService") && "log".equals(p.trackedParam));
    }

    @Test
    void rerunDoesNotDuplicateEdges() {
        int beforeCount = model.callEdges.size();
        CtModel ctModel = scan("quarkus-sample");
        new CallGraphExtractor().extract(ctModel, model);
        assertThat(model.callEdges).hasSize(beforeCount);
    }

    @Test
    void recordsFieldWriteForIncomingConsumerWithDerivedLocalVar() {
        // CachingConsumer.handle writes to ConcurrentHashMap 'cache' via
        // cache.put("id", tmp.get("id")) — the stored value is derived through
        // a local HashMap, not a direct CtVariableRead of the tracked param.
        // extractFieldAccesses must still emit a WRITE FieldAccess for this.
        assertThat(model.fieldAccesses)
                .as("CachingConsumer.handle -> WRITE to 'cache'")
                .anySatisfy(fa -> {
                    assertThat(fa.componentId).contains("CachingConsumer");
                    assertThat(fa.method).isEqualTo("handle");
                    assertThat(fa.kind).isEqualTo(FieldAccess.Kind.WRITE);
                    assertThat(fa.fieldName).isEqualTo("cache");
                });
    }

    @Test
    void genericObjectFlowExtractsPlainJavaReceiverCalls() {
        ArchitectureModel generic = new ArchitectureExtractor()
                .extract(List.of(projectPath("generic-object-flow")));

        assertThat(generic.callEdges)
                .anySatisfy(edge -> {
                    assertThat(edge.fromComponentId).isEqualTo("comp:com.example.objectflow.MainApp");
                    assertThat(edge.fromMethod).isEqualTo("run");
                    assertThat(edge.toComponentId).isEqualTo("comp:com.example.objectflow.GameService");
                    assertThat(edge.toMethod).isEqualTo("run");
                    assertThat(edge.receiverEvidence).isIn("constructor-assignment", "declared-field-type");
                });

        assertThat(generic.callEdges)
                .anySatisfy(edge -> {
                    assertThat(edge.fromComponentId).isEqualTo("comp:com.example.objectflow.GameService");
                    assertThat(edge.fromMethod).isEqualTo("run");
                    assertThat(edge.toComponentId).isIn(
                            "comp:com.example.objectflow.RandomPlayer",
                            "comp:com.example.objectflow.SimplePlayer");
                    assertThat(edge.toMethod).isEqualTo("nextMove");
                });
    }

    @Test
    void genericObjectFlowCapsTooBroadPolymorphicExpansion() {
        ArchitectureModel generic = new ArchitectureExtractor()
                .extract(List.of(projectPath("generic-object-flow")));

        long expanded = generic.callEdges.stream()
                .filter(edge -> edge.toComponentId != null && edge.toComponentId.contains("TooManyHandler"))
                .count();

        assertThat(expanded).isLessThanOrEqualTo(25);
    }

    @Test
    void genericObjectFlowRecordsAccessorBasedStateReadsAndWrites() {
        ArchitectureModel generic = new ArchitectureExtractor()
                .extract(List.of(projectPath("generic-object-flow")));

        assertThat(generic.fieldAccesses)
                .anySatisfy(access -> {
                    assertThat(access.componentId).isEqualTo("comp:com.example.objectflow.StateWriter");
                    assertThat(access.kind).isEqualTo(FieldAccess.Kind.WRITE);
                    assertThat(access.fieldOwnerComponentId).isEqualTo("comp:com.example.objectflow.StateStore");
                    assertThat(access.fieldName).isEqualTo("cache");
                });

        assertThat(generic.fieldAccesses)
                .anySatisfy(access -> {
                    assertThat(access.componentId).isEqualTo("comp:com.example.objectflow.StateReader");
                    assertThat(access.kind).isEqualTo(FieldAccess.Kind.READ);
                    assertThat(access.fieldOwnerComponentId).isEqualTo("comp:com.example.objectflow.StateStore");
                    assertThat(access.fieldName).isEqualTo("cache");
                });
    }

    private static Component component(String qualifiedName) {
        Component component = new Component();
        component.id = "comp:" + qualifiedName;
        component.name = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        component.qualifiedName = qualifiedName;
        component.module = "app:constructor-injection-sample";
        component.technology = "java";
        return component;
    }
}
