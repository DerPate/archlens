package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.ids.AppId;
import dev.dominikbreu.spoonmcp.scanner.SpoonScanner;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import spoon.reflect.CtModel;

/**
 * Shared helpers for extractor tests that need real Spoon-scanned source.
 */
public abstract class ExtractorTestBase {

    protected static final String QUARKUS_APP_ID = "app:quarkus-sample";
    protected static final String JAVAEE_APP_ID = "app:javaee-sample";

    protected static String projectPath(String name) {
        try {
            var url = ExtractorTestBase.class.getClassLoader().getResource("testprojects/" + name);
            Objects.requireNonNull(url, "test resource not found: testprojects/" + name);
            return Paths.get(url.toURI()).toString();
        } catch (Exception e) {
            throw new RuntimeException("cannot resolve test project path: " + name, e);
        }
    }

    protected static CtModel scan(String projectName) {
        return new SpoonScanner().scan(List.of(projectPath(projectName)));
    }

    protected static ArchitectureModel buildQuarkusModel() {
        return new ArchitectureExtractor().extract(List.of(projectPath("quarkus-sample")));
    }

    protected static ArchitectureModel emptyModel(String appId) {
        ArchitectureModel model = new ArchitectureModel("test");
        AppEntry app = new AppEntry();
        app.id = AppId.of(appId);
        app.name = appId.replace("app:", "");
        app.technology = "unknown";
        app.packagingType = "jar";
        model.applications.add(app);
        return model;
    }
}
