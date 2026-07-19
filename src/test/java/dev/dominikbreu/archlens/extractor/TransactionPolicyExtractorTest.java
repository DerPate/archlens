package dev.dominikbreu.archlens.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TransactionPolicyExtractorTest {

    @Test
    void linksEntityManagerOperationToUnitAndMethodPolicy() {
        ArchitectureModel model = extract("javaee-sample");

        assertThat(model.persistenceOperations).anySatisfy(operation -> {
            assertThat(operation.componentId.serialize()).isEqualTo("com.example.ejb.CustomerEjb");
            assertThat(operation.methodName).isEqualTo("save");
            assertThat(operation.operation).isEqualTo("persist");
            assertThat(operation.entityType).isEqualTo("com.example.model.Customer");
            assertThat(operation.persistenceUnitName).isEqualTo("customer-unit");
        });
        assertThat(model.transactionPolicies)
                .filteredOn(policy -> "com.example.ejb.CustomerEjb".equals(policy.componentId.serialize()))
                .anySatisfy(policy -> {
                    if ("save".equals(policy.methodName)) {
                        assertThat(policy.policy).isEqualTo("REQUIRES_NEW");
                        assertThat(policy.declarationLevel).isEqualTo("method");
                    }
                })
                .anySatisfy(policy -> {
                    if ("findById".equals(policy.methodName)) {
                        assertThat(policy.policy).isEqualTo("REQUIRED");
                        assertThat(policy.defaulted).isTrue();
                    }
                });
        assertThat(model.transactionPolicies)
                .filteredOn(policy -> policy.componentId != null
                        && "com.example.ejb.CustomerEjb".equals(policy.componentId.serialize())
                        && "audit".equals(policy.methodName))
                .singleElement()
                .satisfies(policy -> {
                    assertThat(policy.policy).isEqualTo("NOT_SUPPORTED");
                    assertThat(policy.framework).isEqualTo("ejb-xml");
                    assertThat(policy.declarationLevel).isEqualTo("xml");
                    assertThat(policy.source.derivedFrom).isEqualTo("ejb-xml");
                });

        GraphQuery graph = GraphQuery.from(model);
        assertThat(graph.findNodes("PersistenceOperation", "persist", Map.of(), 10))
                .isNotEmpty();
        assertThat(graph.findNodes("TransactionBoundary", "save", Map.of("policy", "REQUIRES_NEW"), 10))
                .isNotEmpty();
        assertThat(graph.findEdges("GOVERNS_OPERATION", Map.of(), 10)).isNotEmpty();
        assertThat(model.runtimeFlows.stream().flatMap(flow -> flow.steps.stream()))
                .anySatisfy(step -> {
                    if ("com.example.ejb.CustomerEjb".equals(step.componentId.serialize())
                            && "save".equals(step.method)) {
                        assertThat(step.transactionPolicy).isEqualTo("REQUIRES_NEW");
                        assertThat(step.transactionTransition).isIn("begin", "suspend-and-begin");
                    }
                });
    }

    @Test
    void appliesSpringTypePolicyAndMethodOverrideWithXmlPersistenceUnit() {
        ArchitectureModel model = extract("spring-pipeline-sample");

        assertThat(model.persistenceUnits).anySatisfy(unit -> {
            if ("spring-orders".equals(unit.name))
                assertThat(unit.transactionType).isEqualTo("RESOURCE_LOCAL");
        });
        assertThat(model.transactionPolicies)
                .filteredOn(
                        policy -> "com.example.pipeline.service.OrderService".equals(policy.componentId.serialize()))
                .anySatisfy(policy -> {
                    if ("create".equals(policy.methodName)) {
                        assertThat(policy.policy).isEqualTo("REQUIRED");
                        assertThat(policy.readOnly).isTrue();
                        assertThat(policy.declarationLevel).isEqualTo("type");
                    }
                })
                .anySatisfy(policy -> {
                    if ("markReady".equals(policy.methodName)) {
                        assertThat(policy.policy).isEqualTo("REQUIRES_NEW");
                        assertThat(policy.readOnly).isFalse();
                        assertThat(policy.declarationLevel).isEqualTo("method");
                        assertThat(policy.limitations).contains("spring-self-invocation-may-bypass-proxy");
                        assertThat(policy.source.confidence).isLessThanOrEqualTo(0.6);
                    }
                });
    }

    @Test
    void appliesJakartaStylePolicyToQuarkusAndKeepsXmlDatasourceUnresolved() {
        ArchitectureModel model = extract("quarkus-sample");

        assertThat(model.persistenceUnits).anyMatch(unit -> "quarkus-orders".equals(unit.name));
        assertThat(model.dataSources).anySatisfy(dataSource -> {
            if ("java:/QuarkusDS".equals(dataSource.jndiName))
                assertThat(dataSource.unresolved).isTrue();
        });
        assertThat(model.transactionPolicies)
                .filteredOn(policy -> "com.example.service.OrderService".equals(policy.componentId.serialize()))
                .allSatisfy(policy -> {
                    assertThat(policy.policy).isEqualTo("SUPPORTS");
                    assertThat(policy.declarationLevel).isEqualTo("type");
                });
    }

    @Test
    void resolvesSpringXmlRulesAndRetainsUnresolvedPointcutEvidence() {
        ArchitectureModel model = extract("spring-xml-transaction-sample");

        assertThat(model.transactionPolicies)
                .filteredOn(policy -> policy.componentId != null
                        && "com.example.xml.XmlOrderService".equals(policy.componentId.serialize())
                        && "write".equals(policy.methodName))
                .singleElement()
                .satisfies(policy -> {
                    assertThat(policy.policy).isEqualTo("REQUIRES_NEW");
                    assertThat(policy.framework).isEqualTo("spring-xml");
                    assertThat(policy.declarationLevel).isEqualTo("xml");
                    assertThat(policy.rollbackRules).contains("rollback-for=java.io.IOException");
                });
        assertThat(model.transactionPolicies)
                .filteredOn(policy -> policy.componentId != null
                        && "com.example.xml.XmlOrderService".equals(policy.componentId.serialize())
                        && "loadOrder".equals(policy.methodName))
                .singleElement()
                .satisfies(policy -> {
                    assertThat(policy.policy).isEqualTo("SUPPORTS");
                    assertThat(policy.readOnly).isTrue();
                });
        assertThat(model.transactionPolicies)
                .filteredOn(policy -> "xml-unresolved".equals(policy.declarationLevel))
                .singleElement()
                .satisfies(policy -> {
                    assertThat(policy.componentId).isNull();
                    assertThat(policy.limitations).contains("unresolved-aop-pointcut");
                    assertThat(policy.source.confidence).isEqualTo(0.5);
                });

        GraphQuery graph = GraphQuery.from(model);
        assertThat(graph.findEdges("DECLARES_TRANSACTION_CONFIG", Map.of(), 10)).hasSize(1);
    }

    @Test
    void modelsInheritedAndProgrammaticSpringPoliciesWithVisibleLimitations() {
        ArchitectureModel model = extract("spring-xml-transaction-sample");

        assertThat(model.transactionPolicies)
                .filteredOn(policy -> policy.componentId != null
                        && "com.example.xml.InheritedService".equals(policy.componentId.serialize())
                        && "inspect".equals(policy.methodName))
                .singleElement()
                .satisfies(policy -> {
                    assertThat(policy.policy).isEqualTo("REQUIRED");
                    assertThat(policy.declarationLevel).isEqualTo("inherited-type");
                    assertThat(policy.limitations).contains("inherited-policy-runtime-resolution");
                });
        assertThat(model.transactionPolicies)
                .filteredOn(policy -> policy.componentId != null
                        && "com.example.xml.ProgrammaticService".equals(policy.componentId.serialize())
                        && "execute".equals(policy.methodName))
                .singleElement()
                .satisfies(policy -> {
                    assertThat(policy.policy).isEqualTo("PROGRAMMATIC");
                    assertThat(policy.programmatic).isTrue();
                    assertThat(policy.limitations).contains("scope-controlled-by-programmatic-api");
                });
    }

    private static ArchitectureModel extract(String fixture) {
        Path path = Path.of("src/test/resources/testprojects", fixture).toAbsolutePath();
        return new ArchitectureExtractor().extract(List.of(path.toString()));
    }
}
