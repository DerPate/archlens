package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spoon.reflect.CtModel;

class GenericJavaExtractorTest extends ExtractorTestBase {

    private static final String APP_ID = "app:plain-java-sample";
    private static ArchitectureModel model;

    @BeforeAll
    static void scanOnce() {
        CtModel ctModel = scan("plain-java-sample");
        model = emptyModel(APP_ID);
        new GenericJavaExtractor().extract(ctModel.getAllTypes(), model, APP_ID);
    }

    @Test
    void detectsMainMethodAsEntrypoint() {
        assertThat(model.entrypoints)
                .anyMatch(e -> e.type == EntrypointType.MAIN_METHOD
                        && "main".equals(e.name)
                        && e.componentId.qualifiedName().contains("PlainServer"));
    }

    @Test
    void mainEntrypointHasSignatureDerivation() {
        model.entrypoints.stream()
                .filter(e -> e.type == EntrypointType.MAIN_METHOD)
                .forEach(e -> assertThat(e.source.derivedFrom).isEqualTo("signature"));
    }

    @Test
    void mainEntrypointHasFullConfidence() {
        model.entrypoints.stream()
                .filter(e -> e.type == EntrypointType.MAIN_METHOD)
                .forEach(e -> assertThat(e.source.confidence).isEqualTo(1.0));
    }

    @Test
    void noMainEntrypointForClassWithoutMainMethod() {
        assertThat(model.entrypoints)
                .noneMatch(e -> e.type == EntrypointType.MAIN_METHOD
                        && e.componentId.qualifiedName().contains("PlainTool"));
    }
}
