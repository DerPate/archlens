package dev.dominikbreu.spoonmcp.renderer;

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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(chain.segments.get(0).path.entrypointId).isEqualTo("ep:ingest");
        assertThat(chain.segments.get(1).path.entrypointId).isEqualTo("ep:schedule");
        assertThat(chain.segments.get(2).path.entrypointId).isEqualTo("ep:process");

        // 2./3. Boundary between segment 1 and 2 is the STORE on Cache.records
        DataFlowSink linkOne = chain.segments.get(1).incomingSink;
        assertThat(linkOne.kind).isEqualTo(DataFlowSink.Kind.STORE);
        assertThat(linkOne.fieldOwnerComponentId).isEqualTo("comp:Cache");
        assertThat(linkOne.fieldName).isEqualTo("records");

        // 4. Boundary between segment 2 and 3 is MESSAGING on the internal channel
        DataFlowSink linkTwo = chain.segments.get(2).incomingSink;
        assertThat(linkTwo.kind).isEqualTo(DataFlowSink.Kind.MESSAGING);
        assertThat(linkTwo.channel).isEqualTo("internal");

        // 5. Rendered Mermaid contains the boundary shapes and edges
        String mermaid = new MermaidPipelineRenderer().render(chain, model);
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
        orphan.id = "df:orphan";
        orphan.entrypointId = "ep:orphan";
        model.dataFlowPaths.add(orphan);

        assertThat(new PipelineGraphBuilder().build(model, 8)).isEmpty();
    }

    @Test
    void rendererAndBuilderHardcodeNoFixtureNames() throws Exception {
        // 6. Defensive check that no component, channel, or field name from the fixture
        // (or any test scenario) leaks into the renderer/builder source.
        java.nio.file.Path renderer = java.nio.file.Paths.get(
            "src/main/java/dev/dominikbreu/spoonmcp/renderer/MermaidPipelineRenderer.java");
        java.nio.file.Path builder = java.nio.file.Paths.get(
            "src/main/java/dev/dominikbreu/spoonmcp/extractor/PipelineGraphBuilder.java");
        String rendererSrc = java.nio.file.Files.readString(renderer);
        String builderSrc  = java.nio.file.Files.readString(builder);
        for (String forbidden : List.of("RecordStore", "RecordDispatcher", "Cache.records",
                                          "recordsInternal", "internal", "Ingestor", "Processor")) {
            // Only fail on word-boundary matches inside identifiers, not e.g. "internal" in javadoc.
            // Plain contains is sufficient because these are component identifiers we'd never use
            // in renderer code if the implementation is generic.
            assertThat(builderSrc).as("builder must not hardcode %s", forbidden).doesNotContain(forbidden);
            // The renderer has class names like "messaging" — the forbidden list above are all
            // distinct tokens; this is a coarse but effective signal.
            if (!"internal".equals(forbidden)) {
                assertThat(rendererSrc).as("renderer must not hardcode %s", forbidden).doesNotContain(forbidden);
            }
        }
    }

    // ── synthetic model ──────────────────────────────────────────────────────

    private ArchitectureModel buildModel() {
        ArchitectureModel m = new ArchitectureModel("synthetic");

        m.components.addAll(List.of(
            comp("comp:Ingestor",  "Ingestor",  ComponentType.MESSAGE_DRIVEN_BEAN),
            comp("comp:Cache",     "Cache",     ComponentType.SERVICE),
            comp("comp:Scheduler", "Scheduler", ComponentType.SCHEDULER),
            comp("comp:Processor", "Processor", ComponentType.MESSAGE_DRIVEN_BEAN),
            comp("comp:Broker",    "Broker",    ComponentType.HTTP_CLIENT)
        ));

        m.entrypoints.addAll(List.of(
            ep("ep:ingest",   "consume",      EntrypointType.MESSAGING_CONSUMER, "external", "comp:Ingestor"),
            ep("ep:schedule", "tick",         EntrypointType.SCHEDULER,           null,       "comp:Scheduler"),
            ep("ep:process",  "process",      EntrypointType.MESSAGING_CONSUMER, "internal", "comp:Processor")
        ));

        // Path 1: ingestor → STORE(Cache.records)
        DataFlowPath p1 = path("df:ingest", "ep:ingest", "snapshot");
        p1.steps.add(new DataFlowStep(0, "comp:Ingestor", "Ingestor", "consume", "snapshot"));
        DataFlowSink storeSink = new DataFlowSink();
        storeSink.kind = DataFlowSink.Kind.STORE;
        storeSink.componentId = "comp:Cache";
        storeSink.componentName = "Cache";
        storeSink.method = "records";
        storeSink.fieldName = "records";
        storeSink.fieldOwnerComponentId = "comp:Cache";
        storeSink.linkedPathIds.add("df:schedule");
        p1.sinks.add(storeSink);

        // Path 2: scheduler → MESSAGING(internal) (also a non-linking HTTP_OUTBOUND terminal)
        DataFlowPath p2 = path("df:schedule", "ep:schedule", "*");
        p2.steps.add(new DataFlowStep(0, "comp:Scheduler", "Scheduler", "tick", "*"));
        DataFlowSink msgSink = new DataFlowSink();
        msgSink.kind = DataFlowSink.Kind.MESSAGING;
        msgSink.componentId = "comp:Scheduler";
        msgSink.componentName = "Scheduler";
        msgSink.method = "send";
        msgSink.channel = "internal";
        msgSink.linkedPathIds.add("df:process");
        p2.sinks.add(msgSink);
        DataFlowSink httpSink = new DataFlowSink();
        httpSink.kind = DataFlowSink.Kind.HTTP_OUTBOUND;
        httpSink.componentId = "comp:Broker";
        httpSink.componentName = "Broker";
        httpSink.method = "publish";
        p2.sinks.add(httpSink);

        // Path 3: processor (terminal segment, no links forward)
        DataFlowPath p3 = path("df:process", "ep:process", "entry");
        p3.steps.add(new DataFlowStep(0, "comp:Processor", "Processor", "process", "entry"));

        m.dataFlowPaths.addAll(List.of(p1, p2, p3));
        return m;
    }

    private Component comp(String id, String name, ComponentType type) {
        Component c = new Component();
        c.id = id;
        c.name = name;
        c.type = type;
        return c;
    }

    private Entrypoint ep(String id, String name, EntrypointType type, String channel, String compId) {
        Entrypoint e = new Entrypoint();
        e.id = id;
        e.name = name;
        e.type = type;
        e.channelName = channel;
        e.componentId = compId;
        return e;
    }

    private DataFlowPath path(String id, String entrypointId, String trackedParam) {
        DataFlowPath p = new DataFlowPath();
        p.id = id;
        p.entrypointId = entrypointId;
        p.trackedParam = trackedParam;
        return p;
    }
}
