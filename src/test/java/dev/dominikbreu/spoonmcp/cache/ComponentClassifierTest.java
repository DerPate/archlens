package dev.dominikbreu.spoonmcp.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.SourceInfo;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import org.junit.jupiter.api.Test;

class ComponentClassifierTest {

    @Test
    void classifiesCoreBusinessService() {
        Component component = component("de.homeinstead.phoenix.service.AbsenceService", ComponentType.SERVICE);

        ComponentClassifier.Classification classification = ComponentClassifier.classify(component, metrics(1, 12, 0));

        assertThat(classification.primaryRole()).isEqualTo("business-service");
        assertThat(classification.supportRole()).isNull();
        assertThat(classification.agentCategory()).isEqualTo("core-workflow");
        assertThat(classification.evidence()).contains("type:SERVICE", "package:service", "name:AbsenceService");
    }

    @Test
    void classifiesMigrationInitializerAsSupportingInfrastructure() {
        Component component = component(
                "de.homeinstead.phoenix.infrastructure.FlywayClientDatabaseInitializer", ComponentType.SERVICE);

        ComponentClassifier.Classification classification = ComponentClassifier.classify(component, metrics(0, 0, 0));

        assertThat(classification.primaryRole()).isEqualTo("support");
        assertThat(classification.supportRole()).isEqualTo("migration-initializer");
        assertThat(classification.agentCategory()).isEqualTo("supporting-infrastructure");
        assertThat(classification.evidence())
                .contains("package:infrastructure", "name:FlywayClientDatabaseInitializer");
    }

    @Test
    void classifiesRedisLockAsSupportingInfrastructureEvenWhenReachable() {
        Component component =
                component("de.homeinstead.phoenix.redis.OwnerAwareRedisLockRegistry", ComponentType.SERVICE);

        ComponentClassifier.Classification classification = ComponentClassifier.classify(component, metrics(6, 1, 0));

        assertThat(classification.primaryRole()).isEqualTo("support");
        assertThat(classification.supportRole()).isEqualTo("redis-lock");
        assertThat(classification.agentCategory()).isEqualTo("supporting-infrastructure");
        assertThat(classification.evidence()).contains("package:redis", "name:OwnerAwareRedisLockRegistry");
    }

    @Test
    void classifiesSecurityConfigurationAsSupportingInfrastructure() {
        Component component =
                component("de.homeinstead.phoenix.authorization.AllowedUrlConfiguration", ComponentType.SERVICE);
        component.stereotypes.add("configuration");

        ComponentClassifier.Classification classification = ComponentClassifier.classify(component, metrics(0, 0, 0));

        assertThat(classification.primaryRole()).isEqualTo("support");
        assertThat(classification.supportRole()).isEqualTo("security-configuration");
        assertThat(classification.agentCategory()).isEqualTo("supporting-infrastructure");
        assertThat(classification.evidence()).contains("stereotype:configuration", "package:authorization");
    }

    @Test
    void classifiesMapperAsSupportingInfrastructure() {
        Component component =
                component("de.homeinstead.phoenix.client.model.mapper.IExtHolidayMapper", ComponentType.SERVICE);

        ComponentClassifier.Classification classification = ComponentClassifier.classify(component, metrics(1, 0, 0));

        assertThat(classification.primaryRole()).isEqualTo("support");
        assertThat(classification.supportRole()).isEqualTo("mapper");
        assertThat(classification.agentCategory()).isEqualTo("supporting-infrastructure");
        assertThat(classification.evidence()).contains("package:mapper", "name:IExtHolidayMapper");
    }

    @Test
    void classifiesEntryDataAndDomainRoles() {
        assertThat(ComponentClassifier.classify(
                                component(
                                        "de.homeinstead.phoenix.controller.app.AppAbsenceController",
                                        ComponentType.REST_RESOURCE),
                                metrics(0, 1, 8))
                        .agentCategory())
                .isEqualTo("boundary");
        assertThat(ComponentClassifier.classify(
                                component(
                                        "de.homeinstead.phoenix.repository.IAccountRepository",
                                        ComponentType.REPOSITORY),
                                metrics(3, 1, 0))
                        .primaryRole())
                .isEqualTo("data-access");
        assertThat(ComponentClassifier.classify(
                                component("de.homeinstead.phoenix.bean.Account", ComponentType.ENTITY),
                                metrics(11, 1, 0))
                        .primaryRole())
                .isEqualTo("domain-model");
    }

    private static Component component(String qualifiedName, ComponentType type) {
        Component component = new Component();
        component.id = ComponentId.of(qualifiedName);
        component.name = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        component.qualifiedName = qualifiedName;
        component.type = type;
        component.source = new SourceInfo("/tmp/" + component.name + ".java", 1, "test", 0.9);
        return component;
    }

    private static ArchitectureRelevanceScorer.Metrics metrics(int fanIn, int fanOut, int ownedEntrypointCount) {
        return new ArchitectureRelevanceScorer.Metrics(fanIn, fanOut, ownedEntrypointCount, 0, 0, 0, 0, 0, 0);
    }
}
