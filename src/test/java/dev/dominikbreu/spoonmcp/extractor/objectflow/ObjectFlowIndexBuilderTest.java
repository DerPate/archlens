package dev.dominikbreu.spoonmcp.extractor.objectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor;
import dev.dominikbreu.spoonmcp.extractor.ExtractorTestBase;
import dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndex;
import dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndexBuilder;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.tracing.StdoutSpanExporter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.VirtualFile;

class ObjectFlowIndexBuilderTest extends ExtractorTestBase {
    private static CtModel ctModel;
    private static ObjectFlowIndex index;

    @BeforeAll
    static void buildIndex() {
        ctModel = scan("generic-object-flow");
        ArchitectureModel architecture =
                new ArchitectureExtractor().extract(List.of(projectPath("generic-object-flow")));
        index = new ObjectFlowIndexBuilder().build(ctModel, architecture);
    }

    @AfterEach
    void resetGlobalTracing() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void emitsObjectFlowStageSpansAndScanCounters() {
        ArchitectureModel architecture =
                new ArchitectureExtractor().extract(List.of(projectPath("generic-object-flow")));
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
        try {
            GlobalOpenTelemetry.resetForTest();
            SdkTracerProvider provider = SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(new StdoutSpanExporter()))
                    .build();
            GlobalOpenTelemetry.set(
                    OpenTelemetrySdk.builder().setTracerProvider(provider).build());

            new ObjectFlowIndexBuilder().build(ctModel, architecture);

            provider.forceFlush();
        } finally {
            System.setOut(originalOut);
        }

        assertThat(captured.toString())
                .contains("objectflow.build")
                .contains("objectflow.component-index")
                .contains("objectflow.type-index")
                .contains("objectflow.implementation-index")
                .contains("objectflow.receiver-targets")
                .contains("model-types=")
                .contains("project-types=")
                .contains("executable-bodies=")
                .contains("invocations=")
                .contains("resolved-invocations=")
                .contains("receiver-targets=")
                .contains("local-assignment-targets=")
                .contains("field-targets=")
                .contains("declared-type-targets=");
    }

    @Test
    void resolvesFieldReceiverToAllocatedComponent() {
        List<ReceiverTarget> targets = index.resolveReceiver(invocation("fieldGame.run"));

        assertThat(targets).anySatisfy(target -> {
            assertThat(target.componentId()).isEqualTo("com.example.objectflow.GameService");
            assertThat(target.methodName()).isEqualTo("run");
            assertThat(target.evidence()).isEqualTo(ObjectFlowEvidence.CONSTRUCTOR_ASSIGNMENT);
            assertThat(target.confidence()).isEqualTo(0.90);
        });
    }

    @Test
    void resolvesLocalReceiverToAllocatedComponent() {
        List<ReceiverTarget> targets = index.resolveReceiver(invocation("localGame.printStats"));

        assertThat(targets).anySatisfy(target -> {
            assertThat(target.componentId()).isEqualTo("com.example.objectflow.GameService");
            assertThat(target.methodName()).isEqualTo("printStats");
            assertThat(target.evidence()).isEqualTo(ObjectFlowEvidence.LOCAL_ASSIGNMENT);
        });
    }

    @Test
    void resolvesArrayElementReceiverToConcretePlayerImplementations() {
        List<ReceiverTarget> targets = index.resolveReceiver(invocation("players[0].nextMove"));

        assertThat(targets)
                .extracting(ReceiverTarget::componentId)
                .contains("com.example.objectflow.RandomPlayer", "com.example.objectflow.SimplePlayer");
    }

    @Test
    void resolvesAccessorChainToStateStoreMapAccess() {
        List<ReceiverTarget> targets = index.resolveReceiver(invocation("provider.store().cache().put"));

        assertThat(targets).anySatisfy(target -> {
            assertThat(target.componentId()).isEqualTo("com.example.objectflow.StateStore");
            assertThat(target.methodName()).isEqualTo("put");
            assertThat(target.evidence()).isEqualTo(ObjectFlowEvidence.ACCESSOR_STATE_OWNER);
            assertThat(target.confidence()).isEqualTo(0.60);
        });
    }

    @Test
    void sourceFactBackedBuilderPreservesReceiverResolution() {
        ArchitectureModel architecture =
                new ArchitectureExtractor().extract(List.of(projectPath("generic-object-flow")));
        SourceFactIndex facts = new SourceFactIndexBuilder().build(ctModel, "generic-object-flow", 1);

        ObjectFlowIndex factBacked = new ObjectFlowIndexBuilder().build(ctModel, architecture, facts);

        assertThat(factBacked.resolveReceiver(invocation("provider.store().cache().put")))
                .extracting(ReceiverTarget::componentId)
                .contains("com.example.objectflow.StateStore");
        assertThat(factBacked.expandDeclaredType("com.example.objectflow.Player", "nextMove"))
                .extracting(ReceiverTarget::componentId)
                .contains("com.example.objectflow.RandomPlayer", "com.example.objectflow.SimplePlayer");
    }

    @Test
    void sourceFactBackedBuilderExpandsInterfaceWithoutInterfaceComponent() {
        CtModel constructorModel = scan("constructor-injection-sample");
        ArchitectureModel architecture = new ArchitectureModel("test");
        architecture.components.add(component("com.example.constructor.AccountController"));
        architecture.components.add(component("com.example.constructor.AccountService"));
        SourceFactIndex facts = new SourceFactIndexBuilder().build(constructorModel, "constructor-injection-sample", 1);

        ObjectFlowIndex factBacked = new ObjectFlowIndexBuilder().build(constructorModel, architecture, facts);

        assertThat(factBacked.expandDeclaredType("com.example.constructor.IAccountService", "getById"))
                .extracting(ReceiverTarget::componentId)
                .containsExactly("com.example.constructor.AccountService");
    }

    @Test
    void capsPolymorphicExpansionAtTwentyFiveTargets() {
        List<ReceiverTarget> targets = index.expandDeclaredType("com.example.objectflow.TooManyHandler", "handle");

        assertThat(targets)
                .hasSize(ObjectFlowIndex.DEFAULT_POLYMORPHIC_TARGET_CAP)
                .allSatisfy(target -> assertThat(target.expansionCapped()).isTrue());
        assertThat(index.diagnostics())
                .anySatisfy(diagnostic -> assertThat(diagnostic)
                        .contains(
                                "TooManyHandler",
                                "polymorphic expansion capped at " + ObjectFlowIndex.DEFAULT_POLYMORPHIC_TARGET_CAP));
    }

    @Test
    void expandsConcreteTypeThroughFullSupertypeClosure() {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(21);
        launcher.getEnvironment().setShouldCompile(false);
        launcher.addInputResource(new VirtualFile("""
                package example;
                interface Root {}
                interface Middle extends Root {}
                abstract class Base implements Middle {}
                class Concrete extends Base {}
                """, "ClosureFixture.java"));
        launcher.buildModel();

        ArchitectureModel architecture = new ArchitectureModel("test");
        architecture.components.add(component("example.Root"));
        architecture.components.add(component("example.Middle"));
        architecture.components.add(component("example.Base"));
        architecture.components.add(component("example.Concrete"));

        ObjectFlowIndex closureIndex = new ObjectFlowIndexBuilder().build(launcher.getModel(), architecture);

        assertThat(closureIndex.expandDeclaredType("example.Root", "run"))
                .extracting(ReceiverTarget::componentId)
                .containsExactly("example.Concrete");
        assertThat(closureIndex.expandDeclaredType("example.Middle", "run"))
                .extracting(ReceiverTarget::componentId)
                .containsExactly("example.Concrete");
        assertThat(closureIndex.expandDeclaredType("example.Base", "run"))
                .extracting(ReceiverTarget::componentId)
                .containsExactly("example.Concrete");
        assertThat(closureIndex.expandDeclaredType("example.Concrete", "run"))
                .extracting(ReceiverTarget::componentId)
                .containsExactly("example.Concrete");
    }

    @Test
    void returnsDeclaredInterfaceOnlyEvidenceForKnownInterfaceWithoutImplementations() {
        ObjectFlowIndex.TypeFact interfaceType =
                new ObjectFlowIndex.TypeFact("example.Interface", "example.Interface", true);
        ObjectFlowIndex emptyImplementationIndex =
                new ObjectFlowIndex(Map.of(interfaceType.qualifiedName(), interfaceType), Map.of());

        List<ReceiverTarget> targets =
                emptyImplementationIndex.expandDeclaredType(interfaceType.qualifiedName(), "run");

        assertThat(targets).singleElement().satisfies(target -> {
            assertThat(target.componentId()).isEqualTo(interfaceType.componentId());
            assertThat(target.methodName()).isEqualTo("run");
            assertThat(target.evidence()).isEqualTo(ObjectFlowEvidence.DECLARED_INTERFACE_ONLY);
            assertThat(target.confidence()).isEqualTo(ObjectFlowEvidence.DECLARED_INTERFACE_ONLY.confidence());
        });
    }

    @Test
    void toleratesUnresolvedNoClasspathVariableReceivers() {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(21);
        launcher.getEnvironment().setShouldCompile(false);
        launcher.addInputResource(new VirtualFile("""
                package example;
                class UsesMissingReceiver {
                    void test() {
                        missingReceiver.run();
                    }
                }
                """, "UsesMissingReceiver.java"));
        launcher.buildModel();

        ArchitectureModel architecture = new ArchitectureModel("test");
        architecture.components.add(component("example.UsesMissingReceiver"));

        assertThatCode(() -> new ObjectFlowIndexBuilder().build(launcher.getModel(), architecture))
                .doesNotThrowAnyException();
    }

    @Test
    void includesDeclaredConcreteTypeWhenImplementationsExist() {
        ObjectFlowIndex.TypeFact baseType =
                new ObjectFlowIndex.TypeFact("example.ConcreteBase", "example.ConcreteBase", false);
        ObjectFlowIndex.TypeFact childType = new ObjectFlowIndex.TypeFact("example.Child", "example.Child", false);
        ObjectFlowIndex concreteBaseIndex = new ObjectFlowIndex(
                Map.of(baseType.qualifiedName(), baseType, childType.qualifiedName(), childType),
                Map.of(baseType.qualifiedName(), List.of(childType)));

        List<ReceiverTarget> targets = concreteBaseIndex.expandDeclaredType(baseType.qualifiedName(), "run");

        assertThat(targets)
                .hasSize(2)
                .satisfiesExactly(
                        target -> {
                            assertThat(target.componentId()).isEqualTo(baseType.componentId());
                            assertThat(target.methodName()).isEqualTo("run");
                            assertThat(target.evidence()).isEqualTo(ObjectFlowEvidence.DECLARED_FIELD_TYPE);
                            assertThat(target.confidence())
                                    .isEqualTo(ObjectFlowEvidence.DECLARED_FIELD_TYPE.confidence());
                            assertThat(target.expansionCapped()).isFalse();
                        },
                        target -> {
                            assertThat(target.componentId()).isEqualTo(childType.componentId());
                            assertThat(target.evidence()).isEqualTo(ObjectFlowEvidence.SMALL_POLYMORPHIC_EXPANSION);
                            assertThat(target.expansionCapped()).isFalse();
                        });
    }

    @Test
    void resolvesCollectionElementReceiverToConcretePlayerImplementations() {
        List<ReceiverTarget> targets = index.resolveReceiver(invocation("player.nextMove"));

        assertThat(targets)
                .extracting(ReceiverTarget::componentId)
                .contains("com.example.objectflow.RandomPlayer", "com.example.objectflow.SimplePlayer");
        assertThat(targets)
                .allSatisfy(target ->
                        assertThat(target.evidence()).isEqualTo(ObjectFlowEvidence.COLLECTION_ELEMENT_ALLOCATION));
    }

    @Test
    void doesNotDuplicatePolymorphicCapDiagnostics() {
        String declaredType = "example.ManyHandlers";
        List<ObjectFlowIndex.TypeFact> implementations = IntStream.range(0, 26)
                .mapToObj(index ->
                        new ObjectFlowIndex.TypeFact("example.Handler" + index, "example.Handler" + index, false))
                .toList();
        ObjectFlowIndex manyImplementationIndex = new ObjectFlowIndex(Map.of(), Map.of(declaredType, implementations));

        manyImplementationIndex.expandDeclaredType(declaredType, "handle");
        manyImplementationIndex.expandDeclaredType(declaredType, "handle");

        assertThat(manyImplementationIndex.diagnostics())
                .filteredOn(diagnostic -> diagnostic.contains(declaredType))
                .singleElement()
                .asString()
                .contains("polymorphic expansion capped at " + ObjectFlowIndex.DEFAULT_POLYMORPHIC_TARGET_CAP);
    }

    @Test
    void doesNotMisresolveAccessorChainThroughCommonJavaApiMethod() {
        // Represents: opt.get().doWork() where opt is java.util.Optional<Worker>
        // and Other happens to have a get() method too.
        // Without the denylist, resolveAccessorTarget picks Other (it has "get"),
        // producing a spurious ACCESSOR_RETURN edge to Other.doWork.
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(21);
        launcher.getEnvironment().setShouldCompile(false);
        launcher.addInputResource(new VirtualFile("""
                package example;
                class Caller {
                    void test(java.util.Optional<Worker> opt) {
                        opt.get().doWork();
                    }
                }
                class Worker { void doWork() {} }
                class Other { void get() {} }
                """, "GenericApiFixture.java"));
        launcher.buildModel();

        ArchitectureModel architecture = new ArchitectureModel("test");
        architecture.components.add(component("example.Caller"));
        architecture.components.add(component("example.Worker"));
        architecture.components.add(component("example.Other"));

        ObjectFlowIndex idx = new ObjectFlowIndexBuilder().build(launcher.getModel(), architecture);

        CtInvocation<?> doWorkCall = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)).stream()
                .filter(inv -> "doWork".equals(inv.getExecutable().getSimpleName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no doWork invocation found"));

        assertThat(idx.resolveReceiver(doWorkCall))
                .as("doWork() must not resolve to Other via get() name-match")
                .noneMatch(t -> "example.Other".equals(t.componentId()));
    }

    private static Component component(String qualifiedName) {
        Component component = new Component();
        component.id = ComponentId.of("" + qualifiedName);
        component.name = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        component.qualifiedName = qualifiedName;
        return component;
    }

    private static CtInvocation<?> invocation(String text) {
        return ctModel.getElements(new TypeFilter<>(CtInvocation.class)).stream()
                .filter(invocation -> invocation.toString().contains(text))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No invocation containing: " + text));
    }
}
