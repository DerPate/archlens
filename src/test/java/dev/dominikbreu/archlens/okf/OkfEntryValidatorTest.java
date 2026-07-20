package dev.dominikbreu.archlens.okf;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OkfEntryValidatorTest {
    @Test
    void acceptsGeneratedConceptAndCurrentSnippets() {
        String concept = """
                ---
                type: Architecture Investigation
                archlens_generated: true
                archlens_semantic_key: abc123
                ---
                # Question
                What changes?
                """;
        OkfEntryValidator validator = new OkfEntryValidator();
        validator.validateConcept(concept, "abc123");
        validator.validateIndexEntry(
                "<!-- archlens:abc123 -->\n- [Title](investigations/impact/title-abc123.md) - Description");
        validator.validateLogEntry("- **Creation**: Added [Title](investigations/impact/title-abc123.md).");
    }

    @Test
    void rejectsWrongKeyAndMalformedInsertedEntries() {
        OkfEntryValidator validator = new OkfEntryValidator();
        assertThatThrownBy(() -> validator.validateConcept("---\ntype: Architecture Investigation\n---\n", "abc"))
                .hasMessageContaining("archlens_generated");
        assertThatThrownBy(() -> validator.validateIndexEntry("Title without link"))
                .hasMessageContaining("index entry");
    }

    @Test
    void rejectsAdditionalIndexLinesAndMalformedFrontmatterClose() {
        OkfEntryValidator validator = new OkfEntryValidator();

        assertThatThrownBy(() -> validator.validateIndexEntry(
                        "<!-- archlens:abc123 -->\n"
                                + "- [Title](investigations/impact/title-abc123.md) - Description\n"
                                + "extra"))
                .hasMessageContaining("index entry");
        assertThatThrownBy(() -> validator.validateIndexEntry(
                        "<!-- archlens:abc123 -->\n"
                                + "- [Title](investigations/impact/title-abc123.md) - Description\n\n"))
                .hasMessageContaining("index entry");
        assertThatThrownBy(() -> validator.validateConcept(
                        """
                        ---
                        type: Architecture Investigation
                        archlens_generated: true
                        archlens_semantic_key: abc123
                        ---corrupted
                        # Question
                        """,
                        "abc123"))
                .hasMessageContaining("frontmatter");
    }
}
