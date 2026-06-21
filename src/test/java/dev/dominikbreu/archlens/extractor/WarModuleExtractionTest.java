package dev.dominikbreu.archlens.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.AppEntry;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import java.io.File;
import java.net.URL;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarModuleExtractionTest {

    private static ArchitectureModel model;

    @BeforeAll
    static void extractModel() throws Exception {
        URL url = WarModuleExtractionTest.class.getClassLoader().getResource("testprojects/war-modules-sample");
        assertThat(url).isNotNull();
        String path = new File(url.toURI()).getAbsolutePath();
        model = new ArchitectureExtractor().extract(List.of(path));
    }

    @Test
    void warParentIsRegisteredAsDeploymentUnit() {
        assertThat(model.applications)
                .anyMatch(a -> "deployment_unit".equals(a.role) && "war-modules-sample".equals(a.name));
    }

    @Test
    void leafModulesAreRegisteredAsChildren() {
        AppEntry war = model.applications.stream()
                .filter(a -> "deployment_unit".equals(a.role))
                .findFirst()
                .orElseThrow();

        List<AppEntry> children = model.applications.stream()
                .filter(a -> war.id.equals(a.parentAppId))
                .toList();
        assertThat(children).isNotEmpty();
    }

    @Test
    void coreModuleClassifiedAsInternalModule() {
        assertThat(model.applications)
                .anyMatch(a -> "core-module".equals(a.name)
                        && ("internal_module".equals(a.role) || "technical_library".equals(a.role)));
    }

    @Test
    void utilModuleClassifiedAsTechnicalLibrary() {
        // util-module has no architectural annotations → heuristic marks it technical_library
        AppEntry util = model.applications.stream()
                .filter(a -> "util-module".equals(a.name))
                .findFirst()
                .orElseThrow();
        assertThat(util.role).isEqualTo("technical_library");
    }

    @Test
    void componentsExtractedFromInternalModules() {
        // EJBs in core-module (OrderService, OrderRepository) should be present
        assertThat(model.components).anyMatch(c -> "OrderService".equals(c.name));
        assertThat(model.components).anyMatch(c -> "OrderRepository".equals(c.name));
    }

    @Test
    void parentAppIdIsSetOnChildModules() {
        assertThat(model.applications.stream()
                        .filter(a -> "internal_module".equals(a.role) || "technical_library".equals(a.role))
                        .allMatch(a -> a.parentAppId != null))
                .isTrue();
    }
}
