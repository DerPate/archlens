package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.Container;
import dev.dominikbreu.spoonmcp.model.SourceInfo;
import dev.dominikbreu.spoonmcp.model.ids.AppId;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ContainerInferrerTest {

    private final ContainerInferrer inferrer = new ContainerInferrer();

    @Test
    void groupsRestResourceInApiContainer() {
        List<Component> comps = List.of(comp("Resource", ComponentType.REST_RESOURCE, "app1"));
        List<Container> containers = inferrer.infer(comps);
        assertThat(containers)
                .anyMatch(c -> "api".equals(c.name)
                        && c.componentIds.stream().anyMatch(id -> "Resource".equals(id.serialize())));
    }

    @Test
    void groupsServiceInServiceContainer() {
        List<Component> comps = List.of(comp("Svc", ComponentType.SERVICE, "app1"));
        List<Container> containers = inferrer.infer(comps);
        assertThat(containers)
                .anyMatch(c -> "service".equals(c.name)
                        && c.componentIds.stream().anyMatch(id -> "Svc".equals(id.serialize())));
    }

    @Test
    void groupsRepositoryInRepositoryContainer() {
        List<Component> comps = List.of(comp("Repo", ComponentType.REPOSITORY, "app1"));
        List<Container> containers = inferrer.infer(comps);
        assertThat(containers)
                .anyMatch(c -> "repository".equals(c.name)
                        && c.componentIds.stream().anyMatch(id -> "Repo".equals(id.serialize())));
    }

    @Test
    void groupsEntityInDomainContainer() {
        List<Component> comps = List.of(comp("Entity", ComponentType.ENTITY, "app1"));
        List<Container> containers = inferrer.infer(comps);
        assertThat(containers)
                .anyMatch(c -> "domain".equals(c.name)
                        && c.componentIds.stream().anyMatch(id -> "Entity".equals(id.serialize())));
    }

    @Test
    void groupsMdbInMessagingContainer() {
        List<Component> comps = List.of(comp("MDB", ComponentType.MESSAGE_DRIVEN_BEAN, "app1"));
        List<Container> containers = inferrer.infer(comps);
        assertThat(containers)
                .anyMatch(c -> "messaging".equals(c.name)
                        && c.componentIds.stream().anyMatch(id -> "MDB".equals(id.serialize())));
    }

    @Test
    void groupsSchedulerInSchedulingContainer() {
        List<Component> comps = List.of(comp("Sched", ComponentType.SCHEDULER, "app1"));
        List<Container> containers = inferrer.infer(comps);
        assertThat(containers)
                .anyMatch(c -> "scheduling".equals(c.name)
                        && c.componentIds.stream().anyMatch(id -> "Sched".equals(id.serialize())));
    }

    @Test
    void allEjbTypesGoToServiceContainer() {
        List<Component> comps = List.of(
                comp("SL", ComponentType.EJB_STATELESS, "app1"),
                comp("SF", ComponentType.EJB_STATEFUL, "app1"),
                comp("SI", ComponentType.EJB_SINGLETON, "app1"));
        List<Container> containers = inferrer.infer(comps);
        Container svc = containers.stream()
                .filter(c -> "service".equals(c.name))
                .findFirst()
                .orElseThrow();
        assertThat(svc.componentIds.stream().map(ComponentId::serialize).toList())
                .containsExactlyInAnyOrder("SL", "SF", "SI");
    }

    @Test
    void createsDistinctContainersPerApp() {
        List<Component> comps = List.of(
                comp("Resource1", ComponentType.REST_RESOURCE, "app1"),
                comp("Resource2", ComponentType.REST_RESOURCE, "app2"));
        List<Container> containers = inferrer.infer(comps);
        List<Container> apiContainers =
                containers.stream().filter(c -> "api".equals(c.name)).toList();
        assertThat(apiContainers).hasSize(2);
        assertThat(apiContainers.stream().map(c -> c.appId.serialize()).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("app1", "app2");
    }

    @Test
    void containerIdIsUnique() {
        List<Component> comps = List.of(
                comp("R", ComponentType.REST_RESOURCE, "app1"),
                comp("S", ComponentType.SERVICE, "app1"),
                comp("P", ComponentType.REPOSITORY, "app1"));
        List<Container> containers = inferrer.infer(comps);
        long unique = containers.stream().map(c -> c.id).distinct().count();
        assertThat(unique).isEqualTo(containers.size());
    }

    @Test
    void containerHasCorrectAppId() {
        List<Component> comps = List.of(comp("X", ComponentType.SERVICE, "my-app"));
        List<Container> containers = inferrer.infer(comps);
        assertThat(containers).isNotEmpty().allMatch(c -> "my-app".equals(c.appId.serialize()));
    }

    @Test
    void multipleComponentsOfSameTypeShareContainer() {
        List<Component> comps =
                List.of(comp("S1", ComponentType.SERVICE, "app1"), comp("S2", ComponentType.SERVICE, "app1"));
        List<Container> containers = inferrer.infer(comps);
        assertThat(containers).hasSize(1);
        assertThat(containers.get(0).componentIds.stream()
                        .map(ComponentId::serialize)
                        .toList())
                .containsExactlyInAnyOrder("S1", "S2");
    }

    @Test
    void plainJavaComponentsUsePackageResponsibilityContainers() {
        Component server = comp("McpServer", ComponentType.SERVICE, "app1");
        server.technology = "java";
        server.qualifiedName = "dev.dominikbreu.spoonmcp.mcp.McpServer";

        Component tool = comp("ListAppsTool", ComponentType.SERVICE, "app1");
        tool.technology = "java";
        tool.qualifiedName = "dev.dominikbreu.spoonmcp.mcp.tools.ListAppsTool";

        Component model = comp("ArchitectureModel", ComponentType.ENTITY, "app1");
        model.technology = "java";
        model.qualifiedName = "dev.dominikbreu.spoonmcp.model.ArchitectureModel";

        List<Container> containers = inferrer.infer(List.of(server, tool, model));

        assertThat(containers)
                .anyMatch(c -> "mcp-server".equals(c.name)
                        && c.componentIds.stream().anyMatch(id -> "McpServer".equals(id.serialize())));
        assertThat(containers)
                .anyMatch(c -> "mcp-tools".equals(c.name)
                        && c.componentIds.stream().anyMatch(id -> "ListAppsTool".equals(id.serialize())));
        assertThat(containers)
                .anyMatch(c -> "model".equals(c.name)
                        && c.componentIds.stream().anyMatch(id -> "ArchitectureModel".equals(id.serialize())));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Component comp(String id, ComponentType type, String module) {
        Component c = new Component();
        c.id = ComponentId.of(id);
        c.name = id;
        c.type = type;
        c.module = AppId.of(module);
        c.technology = "test";
        c.source = new SourceInfo("test.java", 1, "test", 1.0);
        return c;
    }
}
