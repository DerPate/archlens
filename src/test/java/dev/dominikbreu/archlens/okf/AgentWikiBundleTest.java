package dev.dominikbreu.archlens.okf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Holds the committed wiki to the OKF v0.3 rules for bundle structure and path fields. */
class AgentWikiBundleTest {
    private static final Path BUNDLE = Path.of("docs/agent-wiki");
    private static final String REPOSITORY = "https://github.com/DerPate/archlens/";

    @Test
    void declaresOkfV03() throws IOException {
        assertThat(frontmatter(BUNDLE.resolve("index.md"))).containsEntry("okf_version", "0.3");
    }

    @Test
    void onlyTheRootIndexHasFrontmatterAmongReservedFiles() throws IOException {
        for (Path file : markdownFiles()) {
            if (!isReserved(file) || file.equals(BUNDLE.resolve("index.md"))) continue;
            assertThat(Files.readString(file)).as(file.toString()).doesNotStartWith("---\n");
        }
    }

    @Test
    void everyConceptHasAType() throws IOException {
        for (Path file : markdownFiles()) {
            if (!isReserved(file))
                assertThat(frontmatter(file)).as(file.toString()).containsKey("type");
        }
    }

    @Test
    void everyPathFieldResolves() throws IOException {
        List<String> broken = new ArrayList<>();
        int checked = 0;
        for (Path file : markdownFiles()) {
            if (isReserved(file)) continue;
            for (String value : pathFields(frontmatter(file))) {
                checked++;
                if (!resolves(file, value)) broken.add(file + " -> " + value);
            }
        }
        assertThat(checked).isPositive();
        assertThat(broken).isEmpty();
    }

    /** A repository URL must name a file here; a path stays inside the bundle; a descriptor has spaces. */
    private static boolean resolves(Path file, String value) {
        if (value.startsWith(REPOSITORY)) {
            return Files.exists(Path.of(value.substring(REPOSITORY.length()).replaceFirst("^(blob|tree)/main/", "")));
        }
        if (value.contains("://") || value.contains(" ")) return true;
        Path base = value.startsWith("/") ? BUNDLE : file.getParent();
        Path target = base.resolve(value.replaceFirst("^/", "")).normalize();
        return target.startsWith(BUNDLE) && Files.exists(target);
    }

    private static List<String> pathFields(Map<?, ?> frontmatter) {
        List<String> values = new ArrayList<>();
        if (frontmatter.get("resource") instanceof String resource) values.add(resource);
        if (frontmatter.get("sources") instanceof List<?> sources) {
            for (Object source : sources) {
                if (source instanceof Map<?, ?> entry && entry.get("resource") instanceof String resource) {
                    values.add(resource);
                }
            }
        }
        return values;
    }

    private static List<Path> markdownFiles() throws IOException {
        try (var files = Files.walk(BUNDLE)) {
            return files.filter(path -> path.toString().endsWith(".md")).toList();
        }
    }

    private static boolean isReserved(Path file) {
        return Set.of("index.md", "log.md").contains(String.valueOf(file.getFileName()));
    }

    private static Map<String, Object> frontmatter(Path file) throws IOException {
        String text = Files.readString(file);
        return new Yaml().load(text.substring(4, text.indexOf("\n---\n", 4)));
    }
}
