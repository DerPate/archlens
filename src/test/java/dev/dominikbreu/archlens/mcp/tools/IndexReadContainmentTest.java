package dev.dominikbreu.archlens.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.extractor.ArchitectureExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Indexes real directories and searches everything that is persisted for a marker. Each case runs
 * twice: with the marker inside the project, which must be found, and outside, which must not.
 */
class IndexReadContainmentTest {

    private static final String MARKER = "ZzMarker";
    private static final String POM = """
            <project><modelVersion>4.0.0</modelVersion><groupId>g</groupId><artifactId>a</artifactId>
            <version>1</version>%s</project>
            """;
    private static final String JAVA =
            "package p; @jakarta.ejb.Stateless public class " + MARKER + "Service { public void run() {} }";

    @TempDir
    Path tempDir;

    @FunctionalInterface
    interface Layout {
        void create(Path project, Path target) throws IOException;
    }

    static Stream<Arguments> layouts() {
        return Stream.of(
                layout("properties file", (project, target) -> {
                    write(project, "pom.xml", POM.formatted(""));
                    link(
                            project.resolve("src/main/resources/application.properties"),
                            write(target, "f", "some.url=" + MARKER));
                }),
                layout("yaml file", (project, target) -> {
                    write(project, "pom.xml", POM.formatted(""));
                    link(
                            project.resolve("src/main/resources/application.yaml"),
                            write(target, "f", "some:\n  url: " + MARKER + "\n"));
                }),
                layout("yml file", (project, target) -> {
                    write(project, "pom.xml", POM.formatted(""));
                    link(
                            project.resolve("src/main/resources/application.yml"),
                            write(target, "f", "some:\n  url: " + MARKER + "\n"));
                }),
                layout("resources directory", (project, target) -> {
                    write(project, "pom.xml", POM.formatted(""));
                    write(target, "res/application.properties", "some.url=" + MARKER);
                    link(project.resolve("src/main/resources"), target.resolve("res"));
                }),
                layout("persistence descriptor below a linked parent", (project, target) -> {
                    write(project, "pom.xml", POM.formatted(""));
                    write(
                            target,
                            "main/resources/META-INF/persistence.xml",
                            "<persistence><persistence-unit name=\"" + MARKER + "\"/></persistence>");
                    link(project.resolve("src/main"), target.resolve("main"));
                }),
                layout("java file", (project, target) -> {
                    write(project, "pom.xml", POM.formatted(""));
                    link(
                            project.resolve("src/main/java/p/" + MARKER + "Service.java"),
                            write(target, MARKER + "Service.java", JAVA));
                }),
                layout("java package directory", (project, target) -> {
                    write(project, "pom.xml", POM.formatted(""));
                    write(target, "p/" + MARKER + "Service.java", JAVA);
                    link(project.resolve("src/main/java/p"), target.resolve("p"));
                }),
                layout("java source root", (project, target) -> {
                    write(project, "pom.xml", POM.formatted(""));
                    write(target, "java/p/" + MARKER + "Service.java", JAVA);
                    link(project.resolve("src/main/java"), target.resolve("java"));
                }),
                layout("java without a build file", (project, target) -> {
                    write(target, "p/" + MARKER + "Service.java", JAVA);
                    link(project.resolve("code"), target.resolve("p"));
                }),
                layout(
                        "pom file",
                        (project, target) -> link(
                                project.resolve("pom.xml"),
                                write(target, "f", POM.formatted("<packaging>" + MARKER + "</packaging>")))),
                layout("linked module", (project, target) -> {
                    write(project, "pom.xml", POM.formatted("<modules><module>mod</module></modules>"));
                    write(target, "m/pom.xml", POM.formatted("<packaging>" + MARKER + "</packaging>"));
                    link(project.resolve("mod"), target.resolve("m"));
                }),
                layout("relative module path", (project, target) -> {
                    write(
                            project,
                            "pom.xml",
                            POM.formatted("<modules><module>" + project.relativize(target) + "</module></modules>"));
                    write(target, "pom.xml", POM.formatted("<packaging>" + MARKER + "</packaging>"));
                }),
                layout("absolute module path", (project, target) -> {
                    // File(parent, child) appends an absolute child, so climb to the file system root first.
                    String climb = "../".repeat(project.getNameCount())
                            + target.toString().substring(1);
                    write(project, "pom.xml", POM.formatted("<modules><module>" + climb + "</module></modules>"));
                    write(target, "pom.xml", POM.formatted("<packaging>" + MARKER + "</packaging>"));
                }),
                layout(
                        "gradle settings",
                        (project, target) -> link(
                                project.resolve("settings.gradle"), write(target, "f", "include '" + MARKER + "'\n"))),
                layout("gradle include path", (project, target) -> {
                    write(project, "settings.gradle", "include '" + project.relativize(target) + "'\n");
                    write(target, "src/main/resources/application.properties", "some.url=" + MARKER);
                }),
                layout(
                        "compose file",
                        (project, target) -> link(
                                project.resolve("docker-compose.yml"),
                                write(target, "f", "services:\n  " + MARKER + ":\n    ports: ['1:1']\n"))),
                layout(
                        "ansible inventory",
                        (project, target) ->
                                link(project.resolve("hosts"), write(target, "f", "[" + MARKER + "]\nexample\n"))),
                layout("ansible inventory below a linked directory", (project, target) -> {
                    write(target, "deploy/hosts", "[" + MARKER + "]\nexample\n");
                    link(project.resolve("deploy"), target.resolve("deploy"));
                }),
                layout(
                        "ansible playbook",
                        (project, target) -> link(
                                project.resolve("deploy/site.yml"),
                                write(target, "f", "- hosts: " + MARKER + "\n  roles: [web]\n"))),
                layout("ansible directory", (project, target) -> {
                    write(target, "infra/site.yml", "- hosts: " + MARKER + "\n  roles: [web]\n");
                    link(project.resolve("infra"), target.resolve("infra"));
                }));
    }

    private static Arguments layout(String name, Layout layout) {
        return Arguments.of(name, layout);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("layouts")
    void ignoresContentOutsideTheProject(String name, Layout layout) throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        layout.create(project, Files.createDirectory(tempDir.resolve("elsewhere")));

        assertThat(indexed(project)).doesNotContain(MARKER);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("layouts")
    void readsTheSameContentInsideTheProject(String name, Layout layout) throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        layout.create(project, Files.createDirectory(project.resolve("shared")));

        assertThat(indexed(project)).contains(MARKER);
    }

    @Test
    void readsLinksIntoAnotherIndexedRoot() throws Exception {
        Path project = projectLinkingTo(Files.createDirectory(tempDir.resolve("other")));

        assertThat(indexed(project, tempDir.resolve("other"))).contains(MARKER);
    }

    @Test
    void readsLinksWithinTheEnclosingCheckout() throws Exception {
        Files.createDirectory(tempDir.resolve(".git"));
        Path project = projectLinkingTo(Files.createDirectory(tempDir.resolve("shared")));

        assertThat(indexed(project)).contains(MARKER);
    }

    @Test
    void aCheckoutInTheHomeDirectoryDoesNotWidenTheProject() throws Exception {
        Files.createDirectory(tempDir.resolve(".git"));
        Path project = projectLinkingTo(Files.createDirectory(tempDir.resolve("private")));
        String home = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        try {
            assertThat(indexed(project)).doesNotContain(MARKER);
        } finally {
            System.setProperty("user.home", home);
        }
    }

    @Test
    void aProjectPathMayItselfBeALink() throws Exception {
        Path real = projectLinkingTo(null);
        Path alias = Files.createSymbolicLink(tempDir.resolve("alias"), real);

        assertThat(indexed(alias)).contains(MARKER);
    }

    @Test
    void aProjectPathMayNotLeaveItsCheckoutThroughALink() throws Exception {
        Path checkout = Files.createDirectory(tempDir.resolve("checkout"));
        Files.createDirectory(checkout.resolve(".git"));
        Path outside = projectLinkingTo(null);
        link(checkout.resolve("api"), outside);

        assertThat(indexed(checkout.resolve("api"))).doesNotContain(MARKER);
    }

    @Test
    void survivesALinkCycle() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        write(project, "pom.xml", POM.formatted(""));
        Path source = write(project, "src/main/java/p/" + MARKER + "Service.java", JAVA);
        link(source.resolveSibling("again"), source.getParent());

        assertThat(indexed(project)).contains(MARKER);
    }

    /** Creates a project whose configuration holds the marker, linked from {@code target} if given. */
    private Path projectLinkingTo(Path target) throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        write(project, "pom.xml", POM.formatted(""));
        String config = "src/main/resources/application.properties";
        if (target == null) {
            write(project, config, "some.url=" + MARKER);
        } else {
            link(project.resolve(config), write(target, "f", "some.url=" + MARKER));
        }
        return project;
    }

    /** Indexes through the tool and returns its answer plus every byte it persisted. */
    private String indexed(Path... roots) throws Exception {
        Path cacheDir = tempDir.resolve("cache");
        ModelCache cache = new ModelCache(cacheDir.toString());
        ToolResult result = new IndexWorkspaceTool(new ArchitectureExtractor(), cache)
                .execute(Map.of(
                        "paths", List.of(roots).stream().map(Path::toString).toList()));
        StringBuilder all = new StringBuilder(result.text());
        if (Files.isDirectory(cacheDir)) {
            try (Stream<Path> files = Files.walk(cacheDir)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    all.append(Files.readString(file));
                }
            }
        }
        return all.toString();
    }

    private static Path write(Path directory, String name, String content) throws IOException {
        Path file = directory.resolve(name);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private static void link(Path link, Path target) throws IOException {
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, target);
    }
}
