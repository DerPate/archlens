package dev.dominikbreu.archlens.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.build.BuildModule;
import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.ids.AppId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceTopologyExtractorTest {

    private static final Path FIXTURE =
            Path.of("src/test/resources/testprojects/javaee-sample").toAbsolutePath();

    @TempDir
    Path tempDir;

    @Test
    void extractsPersistenceUnitJndiDatasourceAndSanitizedEndpoint() {
        ArchitectureModel model = new ArchitectureExtractor().extract(List.of(FIXTURE.toString()));

        assertThat(model.persistenceUnits).singleElement().satisfies(unit -> {
            assertThat(unit.name).isEqualTo("customer-unit");
            assertThat(unit.provider).isEqualTo("org.hibernate.jpa.HibernatePersistenceProvider");
            assertThat(unit.transactionType).isEqualTo("JTA");
            assertThat(unit.jtaDataSource).isEqualTo("java:/jdbc/CustomerDS");
            assertThat(unit.managedClasses).containsExactly("com.example.model.Customer");
            assertThat(unit.mappingFiles).containsExactly("META-INF/customer-orm.xml");
            assertThat(unit.source.derivedFrom).isEqualTo("persistence.xml");
        });
        assertThat(model.persistenceUnitUsages).anySatisfy(usage -> {
            assertThat(usage.componentId.serialize()).isEqualTo("com.example.ejb.CustomerEjb");
            assertThat(usage.unitName).isEqualTo("customer-unit");
            assertThat(usage.source.derivedFrom).isEqualTo("annotation");
        });
        assertThat(model.dataSources).singleElement().satisfies(dataSource -> {
            assertThat(dataSource.jndiName).isEqualTo("java:/jdbc/CustomerDS");
            assertThat(dataSource.endpoint).isEqualTo("postgresql://db.example:5432/customers");
            assertThat(dataSource.databaseKind).isEqualTo("postgresql");
            assertThat(dataSource.unresolved).isFalse();
            assertThat(dataSource.source.derivedFrom).isEqualTo("wildfly-config");
        });
        assertThat(model.externalSystems).anySatisfy(system -> {
            assertThat(system.kind).isEqualTo("DATABASE");
            assertThat(system.name).isEqualTo("postgresql://db.example:5432/customers");
        });
        assertThat(GraphQuery.from(model).snapshot(500).nodes())
                .flatExtracting(node -> node.properties().values())
                .noneMatch(value -> value != null && value.toString().contains("fixture-secret"));
    }

    @Test
    void projectsEveryPersistenceHopWithEvidenceAndReachability() {
        ArchitectureModel model = new ArchitectureExtractor().extract(List.of(FIXTURE.toString()));
        GraphQuery graph = GraphQuery.from(model);

        assertThat(graph.findNodes("PersistenceUnit", "customer-unit", Map.of(), 10))
                .singleElement()
                .isInstanceOfSatisfying(GraphQuery.PersistenceUnitNode.class, unit -> {
                    assertThat(unit.properties())
                            .containsEntry("jtaDataSource", "java:/jdbc/CustomerDS")
                            .containsEntry("derivedFrom", "persistence.xml")
                            .containsEntry("entrypointReachable", true);
                });
        assertThat(graph.findNodes("DataSource", "CustomerDS", Map.of(), 10))
                .singleElement()
                .isInstanceOfSatisfying(
                        GraphQuery.DataSourceNode.class,
                        dataSource -> assertThat(dataSource.properties())
                                .containsEntry("endpoint", "postgresql://db.example:5432/customers")
                                .containsEntry("derivedFrom", "wildfly-config")
                                .containsEntry("entrypointReachable", true));
        assertThat(graph.summary().edges())
                .containsEntry("DECLARES_PERSISTENCE_UNIT", 1)
                .containsEntry("USES_DATASOURCE", 2)
                .containsEntry("MANAGES_ENTITY", 1)
                .containsEntry("CONNECTS_TO", 1);
        assertThat(graph.summary().edges().get("USES_PERSISTENCE_UNIT")).isGreaterThanOrEqualTo(1);
        assertThat(graph.findEdges("CONNECTS_TO", Map.of(), 10))
                .singleElement()
                .satisfies(edge -> assertThat(edge.properties())
                        .containsEntry("derivedFrom", "wildfly-config")
                        .containsEntry("confidenceBand", "known"));
    }

    @Test
    void sanitizesCredentialsAndQueryParametersFromJdbcUrls() {
        assertThat(PersistenceTopologyExtractor.sanitizeEndpoint(
                        "jdbc:postgresql://alice:secret@db.internal:5432/orders?password=secret&ssl=true"))
                .isEqualTo("postgresql://db.internal:5432/orders");
        assertThat(PersistenceTopologyExtractor.sanitizeEndpoint("${DB_URL}")).isNull();
    }

    @Test
    void preservesUnresolvedDatasourceReferenceWithoutInventingEndpoint() throws Exception {
        Path resources = tempDir.resolve("src/main/resources");
        Path metaInf = resources.resolve("META-INF");
        Files.createDirectories(metaInf);
        Files.writeString(metaInf.resolve("persistence.xml"), """
                <persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.0">
                  <persistence-unit name="unresolved-unit">
                    <jta-data-source>${CUSTOMER_DATASOURCE}</jta-data-source>
                  </persistence-unit>
                </persistence>
                """);
        BuildModule module = new BuildModule(
                "unresolved-app",
                tempDir.toFile(),
                null,
                "jar",
                List.of(),
                List.of(),
                List.of(resources.toFile()),
                "test");
        ArchitectureModel model = new ArchitectureModel(tempDir.toString());

        new PersistenceTopologyExtractor().extract(module, List.of(), model, AppId.of("unresolved-app"));

        assertThat(model.persistenceUnits)
                .singleElement()
                .satisfies(unit -> assertThat(unit.unresolvedPlaceholders).containsExactly("${CUSTOMER_DATASOURCE}"));
        assertThat(model.dataSources).singleElement().satisfies(dataSource -> {
            assertThat(dataSource.unresolved).isTrue();
            assertThat(dataSource.endpoint).isNull();
        });
        assertThat(model.externalSystems).isEmpty();
        assertThat(GraphQuery.from(model).findEdges("CONNECTS_TO", Map.of(), 10))
                .isEmpty();
    }
}
