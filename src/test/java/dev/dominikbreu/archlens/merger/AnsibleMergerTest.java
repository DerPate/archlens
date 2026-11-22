package dev.dominikbreu.archlens.merger;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.DeploymentEntry;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
                        .findFirst()
                        .orElseThrow()
                        .type)
                .isEqualTo("ansible-group");
    }

    @Test
    void inventoryGroupHasHosts() throws Exception {
        ArchitectureModel model = new ArchitectureModel("test");
        merger.merge(ansibleSampleDir(), model);
        DeploymentEntry web = model.deployments.stream()
                .filter(d -> "webservers".equals(d.name))
                .findFirst()
                .orElseThrow();
        assertThat(web.hosts).contains("web1.example.com", "web2.example.com");
    }

    @Test
    void playbookParsesRoles() throws Exception {
        ArchitectureModel model = new ArchitectureModel("test");
        merger.merge(ansibleSampleDir(), model);
        // deploy.yml defines plays with roles
        List<DeploymentEntry> plays = model.deployments.stream()
                .filter(d -> "ansible-host".equals(d.type))
                .toList();
        assertThat(plays).isNotEmpty();
        assertThat(plays.stream().flatMap(p -> p.roles.stream()).toList()).contains("common");
    }

    @Test
    void noDeploymentsWhenDirDoesNotExist() {
        ArchitectureModel model = new ArchitectureModel("test");
        merger.merge(new File("/tmp/no-ansible-xyz"), model);
        assertThat(model.deployments).isEmpty();
    }

    @Test
    void readsAnInventoryDirectory(@TempDir Path dir) throws Exception {
        Path inventory = Files.createDirectory(dir.resolve("inventory"));
        Files.writeString(inventory.resolve("production.ini"), "[web]\nweb1\n");
        Files.writeString(inventory.resolve("hosts"), "[db]\ndb1\n");
        Files.writeString(inventory.resolve("notes.txt"), "[ignored]\nnope\n");
        ArchitectureModel model = new ArchitectureModel("test");

        merger.merge(dir.toFile(), model);

        assertThat(model.deployments).extracting(d -> d.name).containsExactlyInAnyOrder("web", "db");
    }

    @Test
    void aGroupInSeveralInventoryFilesIsOneDeploymentWithAllHosts(@TempDir Path dir) throws Exception {
        Path inventory = Files.createDirectory(dir.resolve("inventory"));
        Files.writeString(inventory.resolve("staging.ini"), "[web]\nweb-staging\nshared\n");
        Files.writeString(inventory.resolve("production.ini"), "[web]\nweb-production\nshared\n");
        ArchitectureModel model = new ArchitectureModel("test");

        merger.merge(dir.toFile(), model);

        assertThat(model.deployments).singleElement().satisfies(web -> {
            assertThat(web.hosts).containsExactly("web-production", "shared", "web-staging");
            assertThat(web.source).endsWith("production.ini");
        });
    }

    @Test
    void aHostsDirectoryDoesNotHideTheInventoryFileAfterIt(@TempDir Path dir) throws Exception {
        Files.createDirectory(dir.resolve("hosts"));
        Files.writeString(dir.resolve("inventory.ini"), "[web]\nweb1\n");
        ArchitectureModel model = new ArchitectureModel("test");

        merger.merge(dir.toFile(), model);

        assertThat(model.deployments).extracting(d -> d.name).containsExactly("web");
    }

    private File ansibleSampleDir() throws Exception {
        URL url = getClass().getClassLoader().getResource("testprojects/ansible-sample");
        assertThat(url).isNotNull();
        return new File(url.toURI());
    }
}
