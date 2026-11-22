package dev.dominikbreu.archlens.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.ArchitectureModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ArchLens analyses without the project's classpath, so its own dependencies must not decide what it
 * finds. Spoon falls back to the JVM classpath, which makes any library ArchLens ships resolvable.
 */
class UnresolvedLibraryTypeTest extends ExtractorTestBase {
    @TempDir
    Path temp;

    @Test
    void callGraphDoesNotDependOnWhetherLibraryTypesResolve() throws IOException {
        // tools.jackson is on ArchLens's classpath and resolves; com.acmecorp.json exists nowhere.
        ArchitectureModel resolvable = extractSchedulerHubUsing("tools.jackson");
        ArchitectureModel unresolvable = extractSchedulerHubUsing("com.acmecorp.json");

        assertThat(callFacts(unresolvable)).containsExactlyInAnyOrderElementsOf(callFacts(resolvable));
        assertThat(fieldFacts(unresolvable)).containsExactlyInAnyOrderElementsOf(fieldFacts(resolvable));
    }

    @Test
    void doesNotGuessBetweenOverloadsOfTheSameArity() throws IOException {
        Path source = temp.resolve("overloads/src/main/java/com/example/OverloadService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package com.example;

                import com.acmecorp.json.Payload;

                public class OverloadService {
                    public void run(Payload payload) {
                        payload.items().forEach(item -> handle(item));
                    }

                    void handle(String text) {}

                    void handle(Integer number) {}
                }
                """);
        ArchitectureModel model = new ArchitectureExtractor()
                .extract(List.of(temp.resolve("overloads").toString()));

        assertThat(model.callEdges)
                .filteredOn(edge -> "handle".equals(edge.toMethod))
                .singleElement()
                .satisfies(edge -> assertThat(edge.paramMapping).isEmpty());
    }

    private ArchitectureModel extractSchedulerHubUsing(String jacksonPackage) throws IOException {
        Path fixture = Path.of(projectPath("scheduler-hub"));
        Path copy = temp.resolve(jacksonPackage).resolve("scheduler-hub");
        try (var files = Files.walk(fixture)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path target = copy.resolve(fixture.relativize(file).toString());
                Files.createDirectories(target.getParent());
                if (file.toString().endsWith(".java")) {
                    Files.writeString(target, Files.readString(file).replace("com.fasterxml.jackson", jacksonPackage));
                } else {
                    Files.copy(file, target);
                }
            }
        }
        return new ArchitectureExtractor().extract(List.of(copy.toString()));
    }

    private static List<String> callFacts(ArchitectureModel model) {
        return model.callEdges.stream()
                .map(edge -> edge.fromComponentId.serialize() + "#" + edge.fromMethod + " -> "
                        + edge.toComponentId.serialize() + "#" + edge.toMethod + " params=" + edge.paramMapping
                        + " literals=" + edge.resolvedLiteralArgs + " returnsTracked=" + edge.returnsTracked)
                .toList();
    }

    private static List<String> fieldFacts(ArchitectureModel model) {
        return model.fieldAccesses.stream()
                .map(access -> access.componentId.serialize() + "#" + access.method + " " + access.kind + " "
                        + access.fieldBinding)
                .toList();
    }
}
