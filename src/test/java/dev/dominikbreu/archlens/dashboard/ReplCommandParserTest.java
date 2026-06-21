package dev.dominikbreu.archlens.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReplCommandParserTest {

    @Test
    void parse_toolNameOnly_hasNoArgs() throws ReplParseException {
        ParsedCommand command = ReplCommandParser.parse("list_apps");

        assertThat(command.toolName()).isEqualTo("list_apps");
        assertThat(command.args()).isEmpty();
    }

    @Test
    void parse_simpleKeyValuePairs() throws ReplParseException {
        ParsedCommand command = ReplCommandParser.parse("find_entrypoints appId=core httpMethod=GET");

        assertThat(command.toolName()).isEqualTo("find_entrypoints");
        assertThat(command.args()).containsEntry("appId", "core").containsEntry("httpMethod", "GET");
    }

    @Test
    void parse_quotedValueWithEmbeddedSpace() throws ReplParseException {
        ParsedCommand command = ReplCommandParser.parse("call_flow entrypointName=\"GET /account\"");

        assertThat(command.args()).containsEntry("entrypointName", "GET /account");
    }

    @Test
    void parse_jsonArrayValue() throws ReplParseException {
        ParsedCommand command = ReplCommandParser.parse("index_workspace paths=[\"./repo\", \"./other\"]");

        assertThat(command.args()).containsKey("paths");
        @SuppressWarnings("unchecked")
        List<String> paths = (List<String>) command.args().get("paths");
        assertThat(paths).containsExactly("./repo", "./other");
    }

    @Test
    void parse_jsonObjectValue() throws ReplParseException {
        ParsedCommand command = ReplCommandParser.parse(
                "query_architecture_graph action=find_nodes filters={\"confidence\":\"<=0.6\"}");

        assertThat(command.args()).containsKey("filters");
        @SuppressWarnings("unchecked")
        Map<String, String> filters = (Map<String, String>) command.args().get("filters");
        assertThat(filters).containsEntry("confidence", "<=0.6");
    }

    @Test
    void parse_emptyLine_throws() {
        assertThatThrownBy(() -> ReplCommandParser.parse("   ")).isInstanceOf(ReplParseException.class);
    }

    @Test
    void parse_tokenWithoutEquals_throws() {
        assertThatThrownBy(() -> ReplCommandParser.parse("find_entrypoints badtoken"))
                .isInstanceOf(ReplParseException.class)
                .hasMessageContaining("badtoken");
    }

    @Test
    void parse_unterminatedQuote_throws() {
        assertThatThrownBy(() -> ReplCommandParser.parse("call_flow entrypointName=\"GET /account"))
                .isInstanceOf(ReplParseException.class);
    }

    @Test
    void parse_invalidJsonValue_throws() {
        assertThatThrownBy(() -> ReplCommandParser.parse("index_workspace paths=[not json]"))
                .isInstanceOf(ReplParseException.class);
    }
}
