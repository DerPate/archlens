package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.workflow.WorkflowLink;
import dev.dominikbreu.spoonmcp.workflow.WorkflowLinker;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersistenceWorkflowLinkTest extends ExtractorTestBase {

    @Test
    void linksRepositorySaveToSchedulerRepositoryReadByEntityType() {
        ArchitectureModel model = new ArchitectureExtractor().extract(List.of(projectPath("spring-pipeline-sample")));

        assertThat(model.dataFlowPaths)
                .anySatisfy(path -> assertThat(path.sinks).anySatisfy(sink -> {
                    assertThat(sink.kind).isEqualTo(DataFlowSink.Kind.PERSISTENCE);
                    assertThat(sink.repositoryOperation).isEqualTo("save");
                    assertThat(sink.entityType).isEqualTo("com.example.pipeline.model.OrderEntity");
                    assertThat(sink.linkedPathIds).isNotEmpty();
                }));

        assertThat(new WorkflowLinker().link(model)).anySatisfy(link -> {
            assertThat(link.kind()).isEqualTo(WorkflowLink.Kind.PERSISTENCE_HANDOFF);
            assertThat(link.entityType()).isEqualTo("com.example.pipeline.model.OrderEntity");
            assertThat(link.evidence()).isEqualTo("repository-entity-match");
        });
    }
}
