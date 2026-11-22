package dev.dominikbreu.archlens.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Fails when the website's tool documentation drifts from the registered MCP schemas. */
class SiteToolDocsTest {
    private static final Pattern ROW = Pattern.compile("<tr>\\s*<td>([^<]+)</td>");
    private static final Pattern CARD = Pattern.compile("tool-card-name\">([a-z0-9_]+)<");

    static Stream<Arguments> documentedTools() {
        return new McpServer()
                .buildToolSpecifications().stream()
                        .map(spec -> spec.tool())
                        .filter(tool -> Files.exists(page(tool.name())))
                        .map(tool -> Arguments.of(
                                tool.name(), ((Map<?, ?>) tool.inputSchema().get("properties")).keySet()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentedTools")
    void pageListsExactlyTheToolsArguments(String tool, Set<String> arguments) throws IOException {
        Set<String> documented = ROW.matcher(Files.readString(page(tool)))
                .results()
                .flatMap(row -> Arrays.stream(row.group(1).split("/")))
                .map(String::strip)
                .collect(Collectors.toSet());
        assertThat(documented).containsExactlyInAnyOrderElementsOf(arguments);
    }

    @Test
    void landingPageListsExactlyTheRegisteredTools() throws IOException {
        List<String> cards = CARD.matcher(Files.readString(Path.of("site/src/pages/index.astro")))
                .results()
                .map(card -> card.group(1))
                .toList();
        assertThat(cards)
                .containsExactlyInAnyOrderElementsOf(new McpServer()
                        .buildToolSpecifications().stream()
                                .map(spec -> spec.tool().name())
                                .toList());
    }

    private static Path page(String tool) {
        return Path.of("site/src/pages/tools", tool.replace('_', '-') + ".astro");
    }
}
