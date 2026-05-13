package dev.dominikbreu.spoonmcp.merger;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DeploymentEntry;
import java.io.File;
import java.net.URL;
import java.util.List;
import org.junit.jupiter.api.Test;

class DockerComposeMergerTest {

    private final DockerComposeMerger merger = new DockerComposeMerger();

    @Test
    void parsesServicesFromComposeFile() throws Exception {
        File dir = composeSampleDir();
        ArchitectureModel model = new ArchitectureModel("test");
        merger.merge(dir, model);

        List<String> names = model.deployments.stream().map(d -> d.name).toList();
        assertThat(names).contains("orders");
        assertThat(names).contains("postgres");
    }

    @Test
    void deploymentTypeIsDockerCompose() throws Exception {
        ArchitectureModel model = new ArchitectureModel("test");
        merger.merge(composeSampleDir(), model);
        assertThat(model.deployments).allMatch(d -> "docker-compose".equals(d.type));
    }

    @Test
    void parsesPortsForService() throws Exception {
        ArchitectureModel model = new ArchitectureModel("test");
        merger.merge(composeSampleDir(), model);
        DeploymentEntry orders = model.deployments.stream()
                .filter(d -> "orders".equals(d.name))
                .findFirst()
                .orElseThrow();
        assertThat(orders.ports).isNotEmpty();
        assertThat(String.join(",", orders.ports)).contains("8080");
    }

    @Test
    void parsesDependsOn() throws Exception {
        ArchitectureModel model = new ArchitectureModel("test");
        merger.merge(composeSampleDir(), model);
        DeploymentEntry orders = model.deployments.stream()
                .filter(d -> "orders".equals(d.name))
                .findFirst()
                .orElseThrow();
        assertThat(orders.dependsOn).contains("postgres");
    }

    @Test
    void linksAppIdWhenNameMatches() throws Exception {
        ArchitectureModel model = new ArchitectureModel("test");
        var app = new dev.dominikbreu.spoonmcp.model.AppEntry();
        app.id = "app:orders";
        app.name = "orders";
        model.applications.add(app);

        merger.merge(composeSampleDir(), model);
        DeploymentEntry orders = model.deployments.stream()
                .filter(d -> "orders".equals(d.name))
                .findFirst()
                .orElseThrow();
        assertThat(orders.appIds).contains("app:orders");
    }

    @Test
    void noDeploymentsWhenNoComposeFile() throws Exception {
        ArchitectureModel model = new ArchitectureModel("test");
        merger.merge(new File("/tmp/no-compose-here-xyz"), model);
        assertThat(model.deployments).isEmpty();
    }

    private File composeSampleDir() throws Exception {
        URL url = getClass().getClassLoader().getResource("testprojects/compose-sample");
        assertThat(url).isNotNull();
        return new File(url.toURI());
    }
}
