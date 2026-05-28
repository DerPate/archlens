package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.*;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spoon.reflect.CtModel;

class JavaEEExtractorTest extends ExtractorTestBase {

    private static ArchitectureModel model;

    @BeforeAll
    static void scanOnce() {
        CtModel ctModel = scan("javaee-sample");
        model = emptyModel(JAVAEE_APP_ID);
        new JavaEEExtractor().extract(ctModel.getAllTypes(), model, JAVAEE_APP_ID);
    }

    // ── component detection ──────────────────────────────────────────────────

    @Test
    void detectsRestResource() {
        assertHasComponentOfType(ComponentType.REST_RESOURCE, "CustomerResource");
    }

    @Test
    void detectsStatelessEjb() {
        assertHasComponentOfType(ComponentType.EJB_STATELESS, "CustomerEjb");
    }

    @Test
    void detectsMessageDrivenBean() {
        assertHasComponentOfType(ComponentType.MESSAGE_DRIVEN_BEAN, "NotificationMDB");
    }

    @Test
    void detectsEntity() {
        assertHasComponentOfType(ComponentType.ENTITY, "Customer");
    }

    @Test
    void componentCountMatchesKnownClasses() {
        // CustomerResource, CustomerEjb, NotificationMDB, Customer
        assertThat(model.components).hasSize(4);
    }

    @Test
    void ejbHasJavaEETechnology() {
        Component ejb = componentByName("CustomerEjb");
        assertThat(ejb.technology).isEqualTo("javaee");
    }

    @Test
    void mdbHasJavaEETechnology() {
        Component mdb = componentByName("NotificationMDB");
        assertThat(mdb.technology).isEqualTo("javaee");
    }

    @Test
    void entityHasJpaTechnology() {
        Component entity = componentByName("Customer");
        assertThat(entity.technology).isEqualTo("jpa");
    }

    @Test
    void componentsHaveSourceInfo() {
        model.components.forEach(
                c -> assertThat(c.source).as("source for %s", c.name).isNotNull());
    }

    @Test
    void componentsHaveExpectedStereotypes() {
        assertThat(componentByName("CustomerEjb").stereotypes).contains("ejb-stateless");
        assertThat(componentByName("NotificationMDB").stereotypes).contains("mdb");
        assertThat(componentByName("Customer").stereotypes).contains("entity");
    }

    // ── entrypoint detection ─────────────────────────────────────────────────

    @Test
    void detectsGetEndpointOnResource() {
        assertHasRestEndpoint("GET", "/customers");
    }

    @Test
    void detectsGetByIdEndpoint() {
        assertHasRestEndpoint("GET", "/customers/{id}");
    }

    @Test
    void detectsPostEndpoint() {
        assertHasRestEndpoint("POST", "/customers");
    }

    @Test
    void detectsMdbOnMessageAsJmsConsumer() {
        List<Entrypoint> jms = model.entrypoints.stream()
                .filter(e -> e.type == EntrypointType.JMS_CONSUMER)
                .toList();
        assertThat(jms).hasSize(1);
        assertThat(jms.get(0).name).isEqualTo("onMessage");
        assertThat(jms.get(0).componentId.qualifiedName()).contains("NotificationMDB");
    }

    @Test
    void jmsEntrypointDerivedFromTypeRelation() {
        Entrypoint jms = model.entrypoints.stream()
                .filter(e -> e.type == EntrypointType.JMS_CONSUMER)
                .findFirst()
                .orElseThrow();
        assertThat(jms.source.derivedFrom).isEqualTo("type-relation");
    }

    @Test
    void restEndpointsHaveSourceAnnotationDerivation() {
        model.entrypoints.stream()
                .filter(e -> e.type == EntrypointType.REST_ENDPOINT)
                .forEach(ep -> assertThat(ep.source.derivedFrom)
                        .as("derivedFrom for %s", ep.name)
                        .isEqualTo("annotation"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void assertHasComponentOfType(ComponentType type, String name) {
        assertThat(model.components)
                .as("component [%s] %s", type, name)
                .anyMatch(c -> c.type == type && c.name.equals(name));
    }

    private void assertHasRestEndpoint(String method, String pathSuffix) {
        assertThat(model.entrypoints)
                .as("%s %s", method, pathSuffix)
                .anyMatch(e -> e.type == EntrypointType.REST_ENDPOINT
                        && method.equals(e.httpMethod)
                        && e.path != null
                        && e.path.endsWith(pathSuffix));
    }

    private Component componentByName(String name) {
        return model.components.stream()
                .filter(c -> c.name.equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("component not found: " + name));
    }
}
