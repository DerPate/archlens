package dev.dominikbreu.spoonmcp.merger;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DeploymentEntry;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnsibleMergerTest {

    private final AnsibleMerger merger = new AnsibleMerger();

    @Test
    void parsesInventoryGroups() throws Exception {
        File dir = ansibleSampleDir();
        ArchitectureModel model = new ArchitectureModel("test");
        merger.merge(dir, model);

        List<String> names = model.deployments.stream().map(d -> d.name).toList();
        assertThat(names).contains("webservers");
        assertThat(names).contains("databases");
    }

    @Test
    void inventoryGroupHasCorrectType() throws Exception {
        ArchitectureModel model = new ArchitectureModel("test");
        merger.merge(ansibleSampleDir(), model);
        assertThat(model.deployments.stream()
            .filter(d -> "webservers".equals(d.name))
            .findFirst().orElseThrow().type)
            .isEqualTo("ansible-group");
    }

    @Test
    void inventoryGroupHasHosts() throws Exception {
        ArchitectureModel model = new ArchitectureModel("test");
        merger.merge(ansibleSampleDir(), model);
        DeploymentEntry web = model.deployments.stream()
            .filter(d -> "webservers".equals(d.name)).findFirst().orElseThrow();
        assertThat(web.hosts).contains("web1.example.com", "web2.example.com");
    }

    @Test
    void playbookParsesRoles() throws Exception {
        ArchitectureModel model = new ArchitectureModel("test");
        merger.merge(ansibleSampleDir(), model);
        // deploy.yml defines plays with roles
        List<DeploymentEntry> plays = model.deployments.stream()
            .filter(d -> "ansible-host".equals(d.type)).toList();
        assertThat(plays).isNotEmpty();
        assertThat(plays.stream().flatMap(p -> p.roles.stream()).toList())
            .contains("common");
    }

    @Test
    void noDeploymentsWhenDirDoesNotExist() {
        ArchitectureModel model = new ArchitectureModel("test");
        merger.merge(new File("/tmp/no-ansible-xyz"), model);
        assertThat(model.deployments).isEmpty();
    }

    private File ansibleSampleDir() throws Exception {
        URL url = getClass().getClassLoader().getResource("testprojects/ansible-sample");
        assertThat(url).isNotNull();
        return new File(url.toURI());
    }
}
