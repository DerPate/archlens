package dev.dominikbreu.archlens.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.EntrypointType;
import dev.dominikbreu.archlens.model.ids.AppId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.support.compiler.VirtualFile;

class GenericJavaExtractorTest extends ExtractorTestBase {

    private static final String APP_ID = "app:plain-java-sample";
    private static ArchitectureModel model;

    @BeforeAll
    static void scanOnce() {
        CtModel ctModel = scan("plain-java-sample");
        model = emptyModel(APP_ID);
        new GenericJavaExtractor().extract(ctModel.getAllTypes(), model, AppId.of(APP_ID));
    }

    @Test
    void classifiesBehaviouralNameSuffixesAsService() {
        // Annotation-free code gives no declaration of intent, so the type name is the only
        // signal. "service" itself was missing from the list until 2026-08-16, so a plain-Java
        // FooService was left UNKNOWN — on this repo that was 121 of 220 components unclassified.
        for (String name : new String[] {
            "PaymentService", "OrderResolver", "ReportBuilder", "WidgetFactory",
            "EventHandler", "PayloadValidator", "RowMapper", "TokenProvider"
        }) {
            assertThat(classify(name)).as("%s should classify as SERVICE", name).isEqualTo(ComponentType.SERVICE);
        }
    }

    @Test
    void leavesValueLikeNamesUnclassified() {
        // Deliberate: calling these ENTITY would hide them from workflow diagrams via the
        // boundary-crossing filter, and they are not domain objects in that sense.
        for (String name : new String[] {"QueryResult", "RenderView", "CallContext"}) {
            assertThat(classify(name)).as("%s should stay UNKNOWN", name).isEqualTo(ComponentType.UNKNOWN);
        }
    }

    @Test
    void suffixMatchIsOnTheEndNotAnywhere() {
        // ToolResult starts with a known suffix word but is not a tool.
        assertThat(classify("ToolResult")).isEqualTo(ComponentType.UNKNOWN);
        assertThat(classify("ExportTool")).isEqualTo(ComponentType.SERVICE);
    }

    private static ComponentType classify(String simpleName) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(21);
        launcher.getEnvironment().setShouldCompile(false);
        launcher.addInputResource(
                new VirtualFile("package com.acme.app;\npublic class " + simpleName + " { void run() {} }"));
        launcher.buildModel();
        ArchitectureModel m = emptyModel("app:classify");
        new GenericJavaExtractor().extract(launcher.getModel().getAllTypes(), m, AppId.of("app:classify"));
        return m.components.stream()
                .filter(c -> simpleName.equals(c.name))
                .findFirst()
                .orElseThrow()
                .type;
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
