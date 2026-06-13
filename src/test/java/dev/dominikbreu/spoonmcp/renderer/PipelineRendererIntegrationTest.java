package dev.dominikbreu.spoonmcp.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.cache.ToolModelIndex;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Chain;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.DataFlowStep;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.DataFlowPathId;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link PipelineGraphBuilder} and {@link MermaidPipelineRenderer} on a synthetic
 * model representing a three-phase pipeline:
 *
 * <pre>
 *   ingestor (MESSAGING_CONSUMER) → STORE(Cache.records) → scheduler (SCHEDULER) → MESSAGING(internal) → processor (MESSAGING_CONSUMER)
 * </pre>
 *
 * <p>The model is hand-built so the test exercises only the builder + renderer contract,
 * independent of the upstream extractor.
 */
class PipelineRendererIntegrationTest {

    @Test
    void buildsThreeSegmentChainAndRendersBoundaries() {
        ArchitectureModel model = buildModel();
        List<Chain> chains = new PipelineGraphBuilder().build(model, 8);

        assertThat(chains).as("one chain rooted at the ingestor").hasSize(1);
        Chain chain = chains.get(0);

        // 1. Root + correct ordering
        assertThat(chain.segments).hasSize(3);
        assertThat(chain.segments.get(0).path.entrypointId).isEqualTo(EntrypointId.deserialize("ingest"));
        assertThat(chain.segments.get(1).path.entrypointId).isEqualTo(EntrypointId.deserialize("schedule"));
        assertThat(chain.segments.get(2).path.entrypointId).isEqualTo(EntrypointId.deserialize("process"));

        // 2./3. Boundary between segment 1 and 2 is the STORE on Cache.records
        DataFlowSink linkOne = chain.segments.get(1).incomingSink;
        assertThat(linkOne.kind).isEqualTo(DataFlowSink.Kind.STORE);
        assertThat(linkOne.fieldOwnerComponentId).isEqualTo(ComponentId.of("Cache"));
        assertThat(linkOne.fieldName).isEqualTo("records");

        // 4. Boundary between segment 2 and 3 is MESSAGING on the internal channel
        DataFlowSink linkTwo = chain.segments.get(2).incomingSink;
        assertThat(linkTwo.kind).isEqualTo(DataFlowSink.Kind.MESSAGING);
        assertThat(linkTwo.channel).isEqualTo("internal");

        // 5. Rendered Mermaid contains the boundary shapes and edges
        String mermaid = new MermaidPipelineRenderer().render(chain, ToolModelIndex.from(model));
        assertThat(mermaid).startsWith("flowchart TD");
        // STORE boundary uses cylinder: [("Cache.records")]
        assertThat(mermaid).containsPattern("\\[\\(\"Cache\\.records\"\\)]");
        // MESSAGING boundary uses rounded rectangle: ("internal")
        assertThat(mermaid).contains("(\"internal\")");
        // At least one directed edge with --> arrow
        assertThat(mermaid).contains("-->");
    }

    @Test
    void emitsNoChainWhenNoLinks() {
        ArchitectureModel model = new ArchitectureModel("synthetic");
        DataFlowPath orphan = new DataFlowPath();
        orphan.id = DataFlowPathId.of(EntrypointId.deserialize("orphan"), "*");
        orphan.entrypointId = EntrypointId.deserialize("orphan");
        model.dataFlowPaths.add(orphan);

        assertThat(new PipelineGraphBuilder().build(model, 8)).isEmpty();
    }

    @Test
    void rendererAndBuilderHardcodeNoFixtureNames() throws Exception {
        // 6. Defensive check that no component, channel, or field name from the fixture
        // (or any test scenario) leaks into the renderer/builder source.
        java.nio.file.Path renderer =
                java.nio.file.Paths.get("src/main/java/dev/dominikbreu/spoonmcp/renderer/MermaidPipelineRenderer.java");
        java.nio.file.Path builder =
                java.nio.file.Paths.get("src/main/java/dev/dominikbreu/spoonmcp/extractor/PipelineGraphBuilder.java");
        String rendererSrc = java.nio.file.Files.readString(renderer);
        String builderSrc = java.nio.file.Files.readString(builder);
        for (String forbidden : List.of(
                "RecordStore",
                "RecordDispatcher",
                "Cache.records",
                "recordsInternal",
                "internal",
                "Ingestor",
                "Processor")) {
            // Only fail on word-boundary matches inside identifiers, not e.g. "internal" in javadoc.
            // Plain contains is sufficient because these are component identifiers we'd never use
            // in renderer code if the implementation is generic.
            assertThat(builderSrc).as("builder must not hardcode %s", forbidden).doesNotContain(forbidden);
            // The renderer has class names like "messaging" — the forbidden list above are all
            // distinct tokens; this is a coarse but effective signal.
            if (!"internal".equals(forbidden)) {
                assertThat(rendererSrc)
                        .as("renderer must not hardcode %s", forbidden)
                        .doesNotContain(forbidden);
            }
        }
    }

    // ── synthetic model ──────────────────────────────────────────────────────

    private ArchitectureModel buildModel() {
        ArchitectureModel m = new ArchitectureModel("synthetic");

        m.components.addAll(List.of(
                comp("Ingestor", "Ingestor", ComponentType.MESSAGE_DRIVEN_BEAN),
                comp("Cache", "Cache", ComponentType.SERVICE),
                comp("Scheduler", "Scheduler", ComponentType.SCHEDULER),
                comp("Processor", "Processor", ComponentType.MESSAGE_DRIVEN_BEAN),
                comp("Broker", "Broker", ComponentType.HTTP_CLIENT)));

        m.entrypoints.addAll(List.of(
                ep("ingest", "consume", EntrypointType.MESSAGING_CONSUMER, "external", "Ingestor"),
                ep("schedule", "tick", EntrypointType.SCHEDULER, null, "Scheduler"),
                ep("process", "process", EntrypointType.MESSAGING_CONSUMER, "internal", "Processor")));

        // Path 1: ingestor → STORE(Cache.records)
        DataFlowPath p1 = path("ingest", "ingest", "snapshot");
        p1.steps.add(new DataFlowStep(0, ComponentId.of("Ingestor"), "Ingestor", "consume", "snapshot"));
        DataFlowSink storeSink = new DataFlowSink();
        storeSink.kind = DataFlowSink.Kind.STORE;
        storeSink.componentId = ComponentId.of("Cache");
        storeSink.componentName = "Cache";
        storeSink.method = "records";
        storeSink.fieldName = "records";
        storeSink.fieldOwnerComponentId = ComponentId.of("Cache");
        storeSink.linkedPathIds.add(DataFlowPathId.deserialize("schedule"));
        p1.sinks.add(storeSink);

        // Path 2: scheduler → MESSAGING(internal) (also a non-linking HTTP_OUTBOUND terminal)
        DataFlowPath p2 = path("schedule", "schedule", "*");
        p2.steps.add(new DataFlowStep(0, ComponentId.of("Scheduler"), "Scheduler", "tick", "*"));
        DataFlowSink msgSink = new DataFlowSink();
        msgSink.kind = DataFlowSink.Kind.MESSAGING;
        msgSink.componentId = ComponentId.of("Scheduler");
        msgSink.componentName = "Scheduler";
        msgSink.method = "send";
        msgSink.channel = "internal";
        msgSink.linkedPathIds.add(DataFlowPathId.deserialize("process"));
        p2.sinks.add(msgSink);
        DataFlowSink httpSink = new DataFlowSink();
        httpSink.kind = DataFlowSink.Kind.HTTP_OUTBOUND;
        httpSink.componentId = ComponentId.of("Broker");
        httpSink.componentName = "Broker";
        httpSink.method = "publish";
        p2.sinks.add(httpSink);

        // Path 3: processor (terminal segment, no links forward)
        DataFlowPath p3 = path("process", "process", "entry");
        p3.steps.add(new DataFlowStep(0, ComponentId.of("Processor"), "Processor", "process", "entry"));

        m.dataFlowPaths.addAll(List.of(p1, p2, p3));
        return m;
    }

    private Component comp(String id, String name, ComponentType type) {
        Component c = new Component();
        c.id = ComponentId.of(id);
        c.name = name;
        c.type = type;
        return c;
    }

    private Entrypoint ep(String id, String name, EntrypointType type, String channel, String compId) {
        Entrypoint e = new Entrypoint();
        e.id = EntrypointId.deserialize(id);
        e.name = name;
        e.type = type;
        e.channelName = channel;
        e.componentId = ComponentId.of(compId);
        return e;
    }

    private DataFlowPath path(String id, String entrypointId, String trackedParam) {
        DataFlowPath p = new DataFlowPath();
        p.id = DataFlowPathId.deserialize(id);
        p.entrypointId = EntrypointId.deserialize(entrypointId);
        p.trackedParam = trackedParam;
        return p;
    }

    @Test
    void intraComponentStepsAreCondensed() {
        ArchitectureModel model = new ArchitectureModel("condense-test");
        model.components.add(comp("Service", "Service", ComponentType.SERVICE));
        model.components.add(comp("Repo", "Repo", ComponentType.REPOSITORY));
        model.entrypoints.add(ep("handle", "handle", EntrypointType.MESSAGING_CONSUMER, "in", "Service"));

        DataFlowPath p = path("handle", "handle", "*");
        // Three steps within Service (intra-component), then one on Repo
        p.steps.add(new DataFlowStep(0, ComponentId.of("Service"), "Service", "handle", "*"));
        p.steps.add(new DataFlowStep(1, ComponentId.of("Service"), "Service", "validate", "*"));
        p.steps.add(new DataFlowStep(2, ComponentId.of("Service"), "Service", "transform", "*"));
        p.steps.add(new DataFlowStep(3, ComponentId.of("Repo"), "Repo", "save", "*"));
        DataFlowSink sink = new DataFlowSink();
        sink.kind = DataFlowSink.Kind.PERSISTENCE;
        sink.componentId = ComponentId.of("Repo");
        sink.componentName = "Repo";
        sink.method = "save";
        p.sinks.add(sink);
        model.dataFlowPaths.add(p);

        PipelineGraphBuilder.Chain chain = new PipelineGraphBuilder.Chain();
        chain.segments.add(new PipelineGraphBuilder.Segment(p, null, model.entrypoints.get(0)));

        String mermaid = new MermaidPipelineRenderer().render(chain, ToolModelIndex.from(model));

        // validate and transform are intra-component — must not appear as separate nodes
        assertThat(mermaid).doesNotContain("Service.validate");
        assertThat(mermaid).doesNotContain("Service.transform");
        // header and the cross-component step must still be present
        assertThat(mermaid).contains("Service.handle");
        assertThat(mermaid).contains("Repo.save");
    }

    @Test
    void duplicateComponentFromDfsBacktrackingIsDeduplicatedInSegment() {
        // Simulates the DFS artifact: DataFlowTracer visits sendTombstone from two branches
        // (once from ingest directly, once from processNonNullValue), recording it twice.
        ArchitectureModel model = new ArchitectureModel("dedup-test");
        model.components.add(comp("DeviceStateDataService", "DeviceStateDataService", ComponentType.SERVICE));
        model.components.add(comp("KafkaMessageSender", "KafkaMessageSender", ComponentType.SERVICE));
        model.components.add(comp("Repo", "Repo", ComponentType.REPOSITORY));
        model.entrypoints.add(
                ep("ingest", "ingest", EntrypointType.MESSAGING_CONSUMER, "in", "DeviceStateDataService"));

        DataFlowPath p = path("ingest", "ingest", "*");
        // Steps as recorded by DFS backtracking: sendTombstone appears twice (non-consecutive)
        p.steps.add(
                new DataFlowStep(0, ComponentId.of("DeviceStateDataService"), "DeviceStateDataService", "ingest", "*"));
        p.steps.add(
                new DataFlowStep(1, ComponentId.of("KafkaMessageSender"), "KafkaMessageSender", "sendTombstone", "*"));
        p.steps.add(new DataFlowStep(
                2, ComponentId.of("DeviceStateDataService"), "DeviceStateDataService", "processNonNullValue", "*"));
        p.steps.add(new DataFlowStep(
                3, ComponentId.of("KafkaMessageSender"), "KafkaMessageSender", "sendTombstone", "*")); // DFS artifact
        p.steps.add(new DataFlowStep(4, ComponentId.of("Repo"), "Repo", "save", "*"));
        DataFlowSink sink = new DataFlowSink();
        sink.kind = DataFlowSink.Kind.PERSISTENCE;
        sink.componentId = ComponentId.of("Repo");
        sink.componentName = "Repo";
        sink.method = "save";
        p.sinks.add(sink);
        model.dataFlowPaths.add(p);

        PipelineGraphBuilder.Chain chain = new PipelineGraphBuilder.Chain();
        chain.segments.add(new PipelineGraphBuilder.Segment(p, null, model.entrypoints.get(0)));

        String mermaid = new MermaidPipelineRenderer().render(chain, ToolModelIndex.from(model));

        // KafkaMessageSender.sendTombstone must appear at most once as a node
        long sendTombstoneCount = java.util.Arrays.stream(mermaid.split("\n"))
                .filter(line -> line.contains("sendTombstone") && line.contains("["))
                .count();
        assertThat(sendTombstoneCount)
                .as("KafkaMessageSender.sendTombstone must not be duplicated in the pipeline")
                .isLessThanOrEqualTo(1);
        // Main flow nodes must still appear
        assertThat(mermaid).contains("processNonNullValue");
        assertThat(mermaid).contains("Repo.save");
    }

    @Test
    void renderedSegmentDoesNotStartWithSelfReferentialEdge() {
        ArchitectureModel model = new ArchitectureModel("self-call-test");
        model.components.add(comp("Scheduler", "Scheduler", ComponentType.SCHEDULER));
        model.components.add(comp("Repo", "Repo", ComponentType.REPOSITORY));

        model.entrypoints.add(ep("tick", "tick", EntrypointType.SCHEDULER, null, "Scheduler"));

        DataFlowPath p1 = path("tick", "tick", "*");
        p1.steps.add(new DataFlowStep(0, ComponentId.of("Scheduler"), "Scheduler", "tick", "*"));
        p1.steps.add(new DataFlowStep(1, ComponentId.of("Repo"), "Repo", "save", "*"));
        DataFlowSink terminal = new DataFlowSink();
        terminal.kind = DataFlowSink.Kind.PERSISTENCE;
        terminal.componentId = ComponentId.of("Repo");
        terminal.componentName = "Repo";
        terminal.method = "save";
        p1.sinks.add(terminal);
        model.dataFlowPaths.add(p1);

        PipelineGraphBuilder.Chain chain = new PipelineGraphBuilder.Chain();
        chain.segments.add(new PipelineGraphBuilder.Segment(p1, null, model.entrypoints.get(0)));

        String mermaid = new MermaidPipelineRenderer().render(chain, ToolModelIndex.from(model));

        // "Scheduler.tick" must appear as exactly one node declaration (not two).
        long headerCount = java.util.Arrays.stream(mermaid.split("\n"))
                .filter(line -> line.contains("\"Scheduler.tick\"") && line.contains("["))
                .count();
        assertThat(headerCount)
                .as("'Scheduler.tick' node must appear exactly once — no duplicate header")
                .isEqualTo(1);
    }

    @Test
    void rendersPersistenceHandoffBoundary() {
        ArchitectureModel model = buildModel();
        DataFlowSink persistence = new DataFlowSink();
        persistence.kind = DataFlowSink.Kind.PERSISTENCE;
        persistence.componentId = ComponentId.of("Repo");
        persistence.componentName = "Repo";
        persistence.method = "save";
        persistence.entityType = "com.example.Order";
        persistence.repositoryOperation = "save";
        persistence.linkEvidence = "repository-entity-match";
        persistence.linkedPathIds.add(DataFlowPathId.deserialize("schedule"));
        model.dataFlowPaths.get(0).sinks.clear();
        model.dataFlowPaths.get(0).sinks.add(persistence);

        PipelineGraphBuilder.Chain chain =
                new PipelineGraphBuilder().build(model, 8).getFirst();
        String mermaid = new MermaidPipelineRenderer().render(chain, ToolModelIndex.from(model));

        assertThat(mermaid).contains("com.example.Order");
        assertThat(mermaid).containsPattern("\\[\\(\"com\\.example\\.Order\"\\)]");
    }
}
