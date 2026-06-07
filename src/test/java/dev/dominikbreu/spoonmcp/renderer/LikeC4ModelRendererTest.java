package dev.dominikbreu.spoonmcp.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dominikbreu.spoonmcp.likec4.LikeC4Document;
import dev.dominikbreu.spoonmcp.likec4.LikeC4DynamicStep;
import dev.dominikbreu.spoonmcp.likec4.LikeC4DynamicView;
import dev.dominikbreu.spoonmcp.likec4.LikeC4Element;
import dev.dominikbreu.spoonmcp.likec4.LikeC4Relationship;
import dev.dominikbreu.spoonmcp.likec4.LikeC4View;
import dev.dominikbreu.spoonmcp.view.ArchitectureViewKind;
import dev.dominikbreu.spoonmcp.view.ArchitectureViewProjection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LikeC4ModelRendererTest {

    @Test
    void rendersProjectionAsLikeC4TextWithMetadata() {
        ArchitectureViewProjection projection = new ArchitectureViewProjection(
                ArchitectureViewKind.COMPONENT,
                "Demo Component View",
                "app:demo",
                List.of(new ArchitectureViewProjection.Node(
                        "SchedulerJob",
                        "SchedulerJob",
                        "component",
                        Map.of("workflowRelevant", true, "noiseScore", 0))),
                List.of(),
                List.of());

        String likec4 = new LikeC4ModelRenderer().render(projection);

        assertTrue(likec4.contains("specification"));
        assertTrue(likec4.contains("model"));
        assertTrue(likec4.contains("views"));
        assertTrue(likec4.contains("schedulerjob = component 'SchedulerJob'"));
        assertTrue(likec4.contains("workflowrelevant 'true'"), "expected workflowrelevant 'true' in:\n" + likec4);
    }

    @Test
    void usesStableNodeIdsForRelationshipReferences() {
        ArchitectureViewProjection projection = new ArchitectureViewProjection(
                ArchitectureViewKind.COMPONENT,
                "Demo Component View",
                "app:demo",
                List.of(
                        new ArchitectureViewProjection.Node("SchedulerJob", "SchedulerJob", "component", Map.of()),
                        new ArchitectureViewProjection.Node("BillingService", "BillingService", "component", Map.of())),
                List.of(new ArchitectureViewProjection.Edge("SchedulerJob", "BillingService", "CALLS", "calls")),
                List.of());

        String likec4 = new LikeC4ModelRenderer().render(projection);

        assertTrue(likec4.contains("schedulerjob = component 'SchedulerJob'"), likec4);
        assertTrue(likec4.contains("billingservice = component 'BillingService'"), likec4);
        assertTrue(likec4.contains("schedulerjob -> billingservice 'calls'"), likec4);
    }

    @Test
    void assignsUniqueProjectionAliasesForCollidingNodeIds() {
        ArchitectureViewProjection projection = new ArchitectureViewProjection(
                ArchitectureViewKind.COMPONENT,
                "Demo Component View",
                "app:demo",
                List.of(
                        new ArchitectureViewProjection.Node("foo-bar", "First", "component", Map.of()),
                        new ArchitectureViewProjection.Node("foo_bar", "Second", "component", Map.of())),
                List.of(new ArchitectureViewProjection.Edge("foo-bar", "foo_bar", "CALLS", "calls")),
                List.of());

        String likec4 = new LikeC4ModelRenderer().render(projection);

        assertTrue(likec4.contains("foo_bar = component 'First'"), likec4);
        assertTrue(likec4.contains("foo_bar_2 = component 'Second'"), likec4);
        assertTrue(likec4.contains("foo_bar -> foo_bar_2 'calls'"), likec4);
    }

    @Test
    void rendersProjectionWarningsAsSafeComments() {
        ArchitectureViewProjection projection = new ArchitectureViewProjection(
                ArchitectureViewKind.COMPONENT,
                "Demo Component View",
                "app:demo",
                List.of(),
                List.of(),
                List.of("first warning\nmodel { injected }"));

        String likec4 = new LikeC4ModelRenderer().render(projection);

        assertTrue(
                likec4.startsWith("// Warning: first warning\n// Warning: model { injected }\n\nspecification {"),
                likec4);
    }

    @Test
    void rendersWorkspaceDocumentWithMultipleViews() {
        LikeC4Document document = new LikeC4Document(
                List.of("system", "component"),
                List.of(
                        new LikeC4Element(
                                "system:Billing",
                                "system",
                                "Billing Platform",
                                "app:billing",
                                Map.of("owner", "Finance\\Ops")),
                        new LikeC4Element(
                                "component:InvoiceService",
                                "component",
                                "Invoice's Service",
                                "class:InvoiceService",
                                Map.of("kind", "component", "layer", "application", "technology", "java"))),
                List.of(new LikeC4Relationship(
                        "system:Billing",
                        "component:InvoiceService",
                        "contains",
                        "uses",
                        Map.of("reason", "invoice orchestration"))),
                List.of(
                        new LikeC4View(
                                "workspace",
                                "Workspace Overview",
                                List.of("system:Billing", "component:InvoiceService"),
                                List.of("Generated from starter workspace")),
                        new LikeC4View("everything", "Everything", List.of(), List.of())),
                List.of("Unresolved dependency ClassPath"),
                List.of());

        String likec4 = new LikeC4ModelRenderer().render(document);

        assertEquals("""
                // Warning: Unresolved dependency ClassPath

                specification {
                  element system
                  element component
                }

                model {
                  system_billing = system 'Billing Platform' {
                    metadata {
                      owner 'Finance\\\\Ops'
                      sourceid 'app:billing'
                    }
                  }
                  component_invoiceservice = component 'Invoice\\'s Service' {
                    metadata {
                      layer 'application'
                      meta_kind 'component'
                      meta_technology 'java'
                      sourceid 'class:InvoiceService'
                    }
                  }
                  system_billing -> component_invoiceservice 'contains' {
                    metadata {
                      reason 'invoice orchestration'
                      sourcelabel 'uses'
                    }
                  }
                }

                views {
                  view workspace {
                    title 'Workspace Overview'
                    // Generated from starter workspace
                    include system_billing
                    include component_invoiceservice
                  }
                  view everything {
                    title 'Everything'
                    include *
                  }
                }
                """, likec4);
        assertTrue(likec4.contains("// Warning: Unresolved dependency ClassPath"), likec4);
        assertTrue(likec4.contains("element system"), likec4);
        assertTrue(likec4.contains("element component"), likec4);
        assertTrue(likec4.contains("system_billing = system 'Billing Platform'"), likec4);
        assertTrue(likec4.contains("component_invoiceservice = component 'Invoice\\'s Service'"), likec4);
        assertTrue(likec4.contains("sourceid 'class:InvoiceService'"), likec4);
        assertTrue(likec4.contains("owner 'Finance\\\\Ops'"), likec4);
        assertTrue(likec4.contains("meta_technology 'java'"), likec4);
        assertTrue(likec4.contains("meta_kind 'component'"), likec4);
        assertTrue(likec4.contains("system_billing -> component_invoiceservice 'contains'"), likec4);
        assertTrue(likec4.contains("view workspace {"), likec4);
        assertTrue(likec4.contains("include system_billing"), likec4);
        assertTrue(likec4.contains("include component_invoiceservice"), likec4);
        assertTrue(likec4.contains("// Generated from starter workspace"), likec4);
        assertTrue(likec4.contains("view everything {"), likec4);
        assertTrue(likec4.contains("include *"), likec4);
    }

    @Test
    void assignsUniqueDocumentAliasesForCollidingElementIds() {
        LikeC4Document document = new LikeC4Document(
                List.of("component"),
                List.of(
                        new LikeC4Element("foo-bar", "component", "First", "source:first", Map.of()),
                        new LikeC4Element("foo_bar", "component", "Second", "source:second", Map.of())),
                List.of(new LikeC4Relationship("foo-bar", "foo_bar", "calls", "", Map.of())),
                List.of(new LikeC4View("collisions", "Collisions", List.of("foo-bar", "foo_bar"), List.of())),
                List.of(),
                List.of());

        String likec4 = new LikeC4ModelRenderer().render(document);

        assertTrue(likec4.contains("foo_bar = component 'First'"), likec4);
        assertTrue(likec4.contains("foo_bar_2 = component 'Second'"), likec4);
        assertTrue(likec4.contains("foo_bar -> foo_bar_2 'calls'"), likec4);
        assertTrue(likec4.contains("include foo_bar\n"), likec4);
        assertTrue(likec4.contains("include foo_bar_2\n"), likec4);
    }

    @Test
    void rendersDynamicViewsInsideViewsBlock() {
        LikeC4Document document = new LikeC4Document(
                List.of("component", "queue"),
                List.of(
                        new LikeC4Element(
                                "comp:OrderService", "component", "OrderService", "comp:OrderService", Map.of()),
                        new LikeC4Element(
                                "topic:KAFKA:orders",
                                "queue",
                                "orders",
                                "topic:KAFKA:orders",
                                Map.of("broker", "KAFKA"))),
                List.of(),
                List.of(new LikeC4View("component", "Component", List.of("comp:OrderService"), List.of())),
                List.of(),
                List.of(new LikeC4DynamicView(
                        "kafka_flow",
                        "KAFKA Message Flow",
                        List.of(new LikeC4DynamicStep("topic:KAFKA:orders", "comp:OrderService", "consumes orders")))));

        String likec4 = new LikeC4ModelRenderer().render(document);

        assertTrue(likec4.contains("dynamic view kafka_flow {"), likec4);
        assertTrue(likec4.contains("title 'KAFKA Message Flow'"), likec4);
        assertTrue(likec4.contains("-> comp_orderservice 'consumes orders'"), likec4);
        assertTrue(likec4.contains("topic_kafka_orders ->"), likec4);
    }

    @Test
    void omitsDynamicViewsBlockWhenNoDynamicViewsExist() {
        LikeC4Document document = new LikeC4Document(
                List.of("component"),
                List.of(new LikeC4Element("comp:A", "component", "A", "comp:A", Map.of())),
                List.of(),
                List.of(new LikeC4View("context", "Context", List.of("comp:A"), List.of())),
                List.of(),
                List.of());

        String likec4 = new LikeC4ModelRenderer().render(document);

        assertFalse(likec4.contains("dynamic view"), likec4);
    }

    @Test
    void commentsEveryPhysicalLineOfWarningsAndViewNotes() {
        LikeC4Document document = new LikeC4Document(
                List.of("component"),
                List.of(),
                List.of(),
                List.of(new LikeC4View("injection", "Injection", List.of(), List.of("first note\ninclude malicious"))),
                List.of("first warning\nmodel { injected }"),
                List.of());

        String likec4 = new LikeC4ModelRenderer().render(document);

        assertTrue(likec4.contains("// Warning: first warning\n// Warning: model { injected }\n"), likec4);
        assertTrue(likec4.contains("    // first note\n    // include malicious\n"), likec4);
    }
}
