package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.model.ids.AppId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spoon.reflect.CtModel;

class DependencyExtractorTest extends ExtractorTestBase {

    private static ArchitectureModel quarkusModel;
    private static ArchitectureModel javaeeModel;

    @BeforeAll
    static void scanBoth() {
        // Quarkus: OrderResource ->@Inject OrderService ->@Inject OrderRepository
        CtModel qModel = scan("quarkus-sample");
        quarkusModel = emptyModel(QUARKUS_APP_ID);
        QuarkusExtractor qExt = new QuarkusExtractor();
        qExt.extract(qModel.getAllTypes(), quarkusModel, AppId.of(QUARKUS_APP_ID));
        new DependencyExtractor().extract(qModel, quarkusModel);

        // JavaEE: CustomerResource ->@EJB CustomerEjb
        CtModel jModel = scan("javaee-sample");
        javaeeModel = emptyModel(JAVAEE_APP_ID);
        JavaEEExtractor jExt = new JavaEEExtractor();
        jExt.extract(jModel.getAllTypes(), javaeeModel, AppId.of(JAVAEE_APP_ID));
        new DependencyExtractor().extract(jModel, javaeeModel);
    }

    // ── quarkus injection dependencies ───────────────────────────────────────

    @Test
    void detectsInjectDependencyResourceToService() {
        assertHasDependency(quarkusModel, "OrderResource", "OrderService");
    }

    @Test
    void detectsInjectDependencyServiceToRepository() {
        assertHasDependency(quarkusModel, "OrderService", "OrderRepository");
    }

    @Test
    void detectsEntityTypeUsageThroughMethodReturnType() {
        // OrderService.find() returns Order (entity) — no @Inject field, so type-usage evidence
        assertHasDependency(quarkusModel, "OrderService", "com.example.model.Order");
        quarkusModel.dependencies.stream()
                .filter(d -> d.fromId.serialize().contains("OrderService")
                        && d.toId.serialize().equals("com.example.model.Order"))
                .forEach(d -> assertThat(d.kind).isEqualTo("type-usage"));
    }

    @Test
    void quarkusDependenciesAreAnnotationDerived() {
        quarkusModel.dependencies.stream()
                .filter(d -> "injection".equals(d.kind))
                .forEach(
                        d -> assertThat(d.derivedFrom).as("derivedFrom for %s", d.id).isEqualTo("annotation"));
    }

    @Test
    void quarkusDependenciesHaveHighConfidence() {
        quarkusModel.dependencies.stream()
                .filter(d -> "injection".equals(d.kind))
                .forEach(
                        d -> assertThat(d.confidence)
                                .as("confidence for %s", d.id)
                                .isGreaterThanOrEqualTo(0.9));
    }

    @Test
    void noDuplicateDependencies() {
        long unique =
                quarkusModel.dependencies.stream().map(d -> d.id).distinct().count();
        assertThat(unique).isEqualTo(quarkusModel.dependencies.size());
    }

    @Test
    void dependencyKindIsInjectionOrTypeUsage() {
        quarkusModel.dependencies.forEach(
                d -> assertThat(d.kind)
                        .as("kind for %s", d.id)
                        .isIn("injection", "type-usage"));
    }

    // ── javaee ejb/resource injection ────────────────────────────────────────

    @Test
    void detectsEjbDependencyResourceToEjb() {
        assertHasDependency(javaeeModel, "CustomerResource", "CustomerEjb");
    }

    @Test
    void noSelfDependency() {
        quarkusModel.dependencies.forEach(
                d -> assertThat(d.fromId).as("no self-dep").isNotEqualTo(d.toId));
        javaeeModel.dependencies.forEach(
                d -> assertThat(d.fromId).as("no self-dep").isNotEqualTo(d.toId));
    }

    @Test
    void doesNotAddDependencyToNonComponent() {
        // EntityManager is not a known component — no dep to it
        assertThat(javaeeModel.dependencies).noneMatch(d -> d.toId.serialize().contains("EntityManager"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void assertHasDependency(ArchitectureModel m, String fromName, String toName) {
        assertThat(m.dependencies)
                .as("dependency %s -> %s", fromName, toName)
                .anyMatch(d -> d.fromId.serialize().contains(fromName)
                        && d.toId.serialize().contains(toName));
    }
}
