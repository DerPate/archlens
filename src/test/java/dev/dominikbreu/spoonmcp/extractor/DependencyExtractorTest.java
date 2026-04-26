package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spoon.reflect.CtModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyExtractorTest extends ExtractorTestBase {

    private static ArchitectureModel quarkusModel;
    private static ArchitectureModel javaeeModel;

    @BeforeAll
    static void scanBoth() {
        // Quarkus: OrderResource ->@Inject OrderService ->@Inject OrderRepository
        CtModel qModel = scan("quarkus-sample");
        quarkusModel = emptyModel(QUARKUS_APP_ID);
        QuarkusExtractor qExt = new QuarkusExtractor();
        qExt.extract(qModel.getAllTypes(), quarkusModel, QUARKUS_APP_ID);
        new DependencyExtractor().extract(qModel, quarkusModel);

        // JavaEE: CustomerResource ->@EJB CustomerEjb
        CtModel jModel = scan("javaee-sample");
        javaeeModel = emptyModel(JAVAEE_APP_ID);
        JavaEEExtractor jExt = new JavaEEExtractor();
        jExt.extract(jModel.getAllTypes(), javaeeModel, JAVAEE_APP_ID);
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
    void quarkusDependenciesAreAnnotationDerived() {
        quarkusModel.dependencies.forEach(d ->
            assertThat(d.derivedFrom).as("derivedFrom for %s", d.id).isEqualTo("annotation"));
    }

    @Test
    void quarkusDependenciesHaveHighConfidence() {
        quarkusModel.dependencies.forEach(d ->
            assertThat(d.confidence).as("confidence for %s", d.id).isGreaterThanOrEqualTo(0.9));
    }

    @Test
    void noDuplicateDependencies() {
        long unique = quarkusModel.dependencies.stream().map(d -> d.id).distinct().count();
        assertThat(unique).isEqualTo(quarkusModel.dependencies.size());
    }

    @Test
    void dependencyKindIsInjection() {
        quarkusModel.dependencies.forEach(d ->
            assertThat(d.kind).as("kind for %s", d.id).isEqualTo("injection"));
    }

    // ── javaee ejb/resource injection ────────────────────────────────────────

    @Test
    void detectsEjbDependencyResourceToEjb() {
        assertHasDependency(javaeeModel, "CustomerResource", "CustomerEjb");
    }

    @Test
    void noSelfDependency() {
        quarkusModel.dependencies.forEach(d ->
            assertThat(d.fromId).as("no self-dep").isNotEqualTo(d.toId));
        javaeeModel.dependencies.forEach(d ->
            assertThat(d.fromId).as("no self-dep").isNotEqualTo(d.toId));
    }

    @Test
    void doesNotAddDependencyToNonComponent() {
        // EntityManager is not a known component — no dep to it
        assertThat(javaeeModel.dependencies)
            .noneMatch(d -> d.toId.contains("EntityManager"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void assertHasDependency(ArchitectureModel m, String fromName, String toName) {
        assertThat(m.dependencies)
            .as("dependency %s -> %s", fromName, toName)
            .anyMatch(d -> d.fromId.contains(fromName) && d.toId.contains(toName));
    }
}
