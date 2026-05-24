package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.extractor.ExtractorTestBase;
import org.junit.jupiter.api.Test;

class SourceFactIndexBuilderTest extends ExtractorTestBase {

    @Test
    void sourceFactTypesHaveStableIdsAndLocations() {
        SourceLocation location = new SourceLocation("Example.java", 7);
        SourceType type = new SourceType(
                "type:com.example.Example",
                "com.example.Example",
                "Example",
                "com.example",
                false,
                false,
                location);

        assertThat(type.id()).isEqualTo("type:com.example.Example");
        assertThat(type.qualifiedName()).isEqualTo("com.example.Example");
        assertThat(type.location()).isSameAs(location);
    }

    @Test
    void sourceFactIndexReturnsImmutableFactsByStableIds() {
        SourceType type = new SourceType(
                "type:example.Service",
                "example.Service",
                "Service",
                "example",
                false,
                false,
                SourceLocation.unknown());
        SourceMethod method = new SourceMethod(
                "method:example.Service#handle(java.lang.String)",
                type.id(),
                "handle",
                "handle(java.lang.String)",
                false,
                java.util.List.of("payload"),
                java.util.List.of("java.lang.String"),
                SourceLocation.unknown());
        SourceFactIndex index = new SourceFactIndex(
                java.util.List.of(type),
                java.util.List.of(method),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.Map.of());

        assertThat(index.type("example.Service")).isSameAs(type);
        assertThat(index.methods(type.id())).containsExactly(method);
        assertThat(index.method(method.id())).isSameAs(method);
        assertThat(index.methods("type:missing")).isEmpty();
    }

    @Test
    void buildsTypesMembersAnnotationsAndLocationsFromQuarkusSample() {
        SourceFactIndex index = new SourceFactIndexBuilder().build(scan("quarkus-sample"), "quarkus-sample", 1);

        SourceType orderResource = index.type("com.example.api.OrderResource");
        assertThat(orderResource).isNotNull();
        assertThat(orderResource.simpleName()).isEqualTo("OrderResource");
        assertThat(orderResource.location().file()).endsWith("OrderResource.java");
        assertThat(orderResource.location().line()).isGreaterThan(0);

        assertThat(index.methods(orderResource.id()))
                .extracting(SourceMethod::name)
                .contains("get");
        assertThat(index.fields(orderResource.id()))
                .extracting(SourceField::name)
                .contains("orderService");
        assertThat(index.annotations(orderResource.id()))
                .extracting(SourceAnnotation::qualifiedName)
                .anyMatch(name -> name.endsWith(".Path") || name.equals("Path"));
    }

    @Test
    void indexesImplementationsFromGenericObjectFlow() {
        SourceFactIndex index = new SourceFactIndexBuilder().build(scan("generic-object-flow"), "generic-object-flow", 1);

        assertThat(index.implementations("com.example.objectflow.Player"))
                .extracting(SourceType::qualifiedName)
                .contains("com.example.objectflow.RandomPlayer", "com.example.objectflow.SimplePlayer");
        assertThat(index.implementations("com.example.objectflow.Move"))
                .extracting(SourceType::qualifiedName)
                .contains("com.example.objectflow.Rock", "com.example.objectflow.Paper");
    }

    @Test
    void indexesInvocationsAssignmentsReturnsAndInjectionFacts() {
        SourceFactIndex index = new SourceFactIndexBuilder().build(scan("generic-object-flow"), "generic-object-flow", 1);
        SourceType mainApp = index.type("com.example.objectflow.MainApp");
        SourceMethod run = index.methods(mainApp.id()).stream()
                .filter(method -> method.name().equals("run"))
                .findFirst()
                .orElseThrow();

        assertThat(index.invocations(run.id()))
                .extracting(SourceInvocation::executableName)
                .contains("run", "printStats");
        assertThat(index.assignments(run.id()))
                .anySatisfy(assignment -> {
                    assertThat(assignment.target()).contains("localGame");
                    assertThat(assignment.evidence()).isIn(SourceEvidence.LOCAL_ASSIGNMENT, SourceEvidence.CONSTRUCTOR_CALL);
                });

        SourceType provider = index.type("com.example.objectflow.StateStoreProvider");
        SourceMethod store = index.methods(provider.id()).stream()
                .filter(method -> method.name().equals("store"))
                .findFirst()
                .orElseThrow();
        assertThat(index.returns(store.id()))
                .anySatisfy(ret -> {
                    assertThat(ret.referencedField()).isEqualTo("store");
                    assertThat(ret.evidence()).isEqualTo(SourceEvidence.METHOD_RETURNS_FIELD);
                });
    }

    @Test
    void indexesFieldInjectionFromQuarkusSample() {
        SourceFactIndex index = new SourceFactIndexBuilder().build(scan("quarkus-sample"), "quarkus-sample", 1);
        SourceType orderResource = index.type("com.example.api.OrderResource");

        assertThat(index.injectionPoints(orderResource.id()))
                .anySatisfy(injection -> {
                    assertThat(injection.fieldName()).isEqualTo("orderService");
                    assertThat(injection.targetType()).isEqualTo("com.example.service.OrderService");
                    assertThat(injection.evidence()).isEqualTo(SourceEvidence.FIELD_INJECTION);
                    assertThat(injection.confidence()).isEqualTo(FactConfidence.KNOWN);
                });
    }
}
