package dev.dominikbreu.spoonmcp.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class BuildMetadataServiceTest {

    @Test
    void buildModuleStoresNormalizedMetadata() {
        File moduleRoot = new File("/workspace/api");
        BuildModule module = new BuildModule(
                "api",
                moduleRoot,
                null,
                "jar",
                List.of("java", "org.springframework.boot"),
                List.of(new File(moduleRoot, "src/main/java")),
                List.of(new File(moduleRoot, "src/main/resources")),
                "literal-test");

        assertThat(module.name()).isEqualTo("api");
        assertThat(module.root()).isEqualTo(moduleRoot);
        assertThat(module.packagingType()).isEqualTo("jar");
        assertThat(module.plugins()).containsExactly("java", "org.springframework.boot");
        assertThat(module.sourceRoots()).containsExactly(new File(moduleRoot, "src/main/java"));
        assertThat(module.resourceRoots()).containsExactly(new File(moduleRoot, "src/main/resources"));
        assertThat(module.evidence()).isEqualTo("literal-test");
    }

    @Test
    void mavenDetectorFindsSingleModuleWithPackagingAndRoots() {
        File root = projectPath("quarkus-sample");

        BuildProject project = new MavenBuildProjectDetector().detect(root).orElseThrow();

        assertThat(project.buildSystem()).isEqualTo(BuildSystem.MAVEN);
        assertThat(project.modules()).hasSize(1);
        BuildModule module = project.modules().getFirst();
        assertThat(module.name()).isEqualTo("quarkus-sample");
        assertThat(module.packagingType()).isEqualTo("jar");
        assertThat(module.sourceRoots()).contains(new File(root, "src/main/java"));
        assertThat(module.resourceRoots()).contains(new File(root, "src/main/resources"));
    }

    @Test
    void mavenDetectorFindsMultiModuleTree() {
        File root = projectPath("multimodule-sample");

        BuildProject project = new MavenBuildProjectDetector().detect(root).orElseThrow();

        assertThat(project.buildSystem()).isEqualTo(BuildSystem.MAVEN);
        assertThat(project.modules()).extracting(BuildModule::name).contains("api", "service", "domain");
        assertThat(project.modules())
                .filteredOn(module -> "api".equals(module.name()))
                .singleElement()
                .satisfies(module -> assertThat(module.root()).isEqualTo(new File(root, "api")));
    }

    @Test
    void gradleGroovyDetectorFindsSpringBootPluginAndBootJar() {
        File root = projectPath("gradle-springboot-sample");

        BuildProject project = new GradleBuildProjectDetector().detect(root).orElseThrow();

        assertThat(project.buildSystem()).isEqualTo(BuildSystem.GRADLE_GROOVY);
        assertThat(project.modules()).hasSize(1);
        BuildModule module = project.modules().getFirst();
        assertThat(module.name()).isEqualTo("gradle-springboot-sample");
        assertThat(module.packagingType()).isEqualTo("boot-jar");
        assertThat(module.plugins()).contains("java", "org.springframework.boot", "io.spring.dependency-management");
        assertThat(module.sourceRoots()).contains(new File(root, "src/main/java"));
    }

    @Test
    void gradleKotlinDetectorFindsSpringBootPluginAndBootJar() {
        File root = projectPath("gradle-kotlin-springboot-sample");

        BuildProject project = new GradleBuildProjectDetector().detect(root).orElseThrow();

        assertThat(project.buildSystem()).isEqualTo(BuildSystem.GRADLE_KOTLIN);
        assertThat(project.modules()).hasSize(1);
        assertThat(project.modules().getFirst().plugins()).contains("java", "org.springframework.boot");
        assertThat(project.modules().getFirst().packagingType()).isEqualTo("boot-jar");
    }

    @Test
    void gradleDetectorFindsLiteralMultiModuleIncludes() {
        File root = projectPath("gradle-multimodule-springboot-sample");

        BuildProject project = new GradleBuildProjectDetector().detect(root).orElseThrow();

        assertThat(project.buildSystem()).isEqualTo(BuildSystem.GRADLE_GROOVY);
        assertThat(project.modules()).extracting(BuildModule::name).containsExactlyInAnyOrder("api", "service");
        assertThat(project.modules())
                .filteredOn(module -> "api".equals(module.name()))
                .singleElement()
                .satisfies(module -> {
                    assertThat(module.plugins()).contains("java", "org.springframework.boot");
                    assertThat(module.packagingType()).isEqualTo("boot-jar");
                });
    }

    @Test
    void buildMetadataServiceChoosesGradleBeforePlainFallback() {
        File root = projectPath("gradle-springboot-sample");

        BuildProject project = new BuildMetadataService().detect(root);

        assertThat(project.buildSystem()).isEqualTo(BuildSystem.GRADLE_GROOVY);
        assertThat(project.modules()).hasSize(1);
        assertThat(project.modules().getFirst().packagingType()).isEqualTo("boot-jar");
    }

    @Test
    void unknownDetectorReturnsPlainJavaSourceRoot() {
        File root = projectPath("plain-java-sample");

        BuildProject project = new UnknownBuildProjectDetector().detect(root).orElseThrow();

        assertThat(project.buildSystem()).isEqualTo(BuildSystem.UNKNOWN);
        assertThat(project.modules()).hasSize(1);
        assertThat(project.modules().getFirst().sourceRoots()).contains(new File(root, "src/main/java"));
    }

    static File projectPath(String name) {
        try {
            var url = BuildMetadataServiceTest.class.getClassLoader().getResource("testprojects/" + name);
            Objects.requireNonNull(url, "test resource not found: testprojects/" + name);
            return Paths.get(url.toURI()).toFile();
        } catch (Exception e) {
            throw new RuntimeException("cannot resolve test project path: " + name, e);
        }
    }
}
