package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.AppEntry;
import java.io.File;
import java.net.URL;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import spoon.Launcher;
import spoon.reflect.declaration.CtType;

class InternalModuleClassifierTest {

    private final InternalModuleClassifier classifier = new InternalModuleClassifier();

    @Test
    void moduleWithEjbAnnotationsIsInternalModule() throws Exception {
        Collection<CtType<?>> types = scanModule("core-module");
        AppEntry app = appEntry("core-module");
        assertThat(classifier.classify(types, app)).isEqualTo("internal_module");
    }

    @Test
    void moduleWithNoArchAnnotationsIsTechnicalLibrary() throws Exception {
        Collection<CtType<?>> types = scanModule("util-module");
        AppEntry app = appEntry("util-module");
        assertThat(classifier.classify(types, app)).isEqualTo("technical_library");
    }

    @Test
    void utilNameReducesScore() throws Exception {
        Collection<CtType<?>> types = scanModule("util-module");
        AppEntry app = appEntry("utils");
        assertThat(classifier.classify(types, app)).isEqualTo("technical_library");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Collection<CtType<?>> scanModule(String moduleName) throws Exception {
        URL url = getClass().getClassLoader().getResource("testprojects/war-modules-sample/" + moduleName);
        assertThat(url).isNotNull();
        File root = new File(url.toURI());
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.getEnvironment().setShouldCompile(false);
        File srcMain = new File(root, "src/main/java");
        if (srcMain.exists()) launcher.addInputResource(srcMain.getAbsolutePath());
        launcher.buildModel();
        return launcher.getModel().getAllTypes();
    }

    private AppEntry appEntry(String name) {
        AppEntry app = new AppEntry();
        app.id = "app:" + name;
        app.name = name;
        return app;
    }
}
