package dev.dominikbreu.archlens.okf;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OkfEntryValidatorTest {
    private static final String OKF_V02_FIELDS = """
            generated:
              by: process:archlens
              at: '2026-07-19T12:00:00Z'
            status: draft
            stale_after: '2026-10-17'
            """;

    @Test
    void acceptsGeneratedConceptAndCurrentSnippets() {
        String concept = """
                ---
                type: Architecture Investigation
                %sarchlens_generated: true
                archlens_semantic_key: abc123
                ---
                # Question
                What changes?
                """.formatted(OKF_V02_FIELDS);
        OkfEntryValidator validator = new OkfEntryValidator();
        validator.validateConcept(concept, "abc123");
        validator.validateIndexEntry(
                "<!-- archlens:abc123 -->\n- [Title](investigations/impact/title-abc123.md) - Description");
        validator.validateLogEntry("- **Creation**: Added [Title](investigations/impact/title-abc123.md).");
    }

    @Test
    void rejectsWrongKeyAndMalformedInsertedEntries() {
        OkfEntryValidator validator = new OkfEntryValidator();
        assertThatThrownBy(() -> validator.validateConcept("""
                        ---
                        type: Architecture Investigation
                        %s---
                        """.formatted(OKF_V02_FIELDS), "abc"))
                .hasMessageContaining("archlens_generated");
        assertThatThrownBy(() -> validator.validateIndexEntry("Title without link"))
                .hasMessageContaining("index entry");
    }

    @Test
    void rejectsMissingOkfV02ProvenanceFields() {
        OkfEntryValidator validator = new OkfEntryValidator();

        assertThatThrownBy(() -> validator.validateConcept("""
                        ---
                        type: Architecture Investigation
                        status: draft
                        stale_after: '2026-10-17'
                        archlens_generated: true
                        archlens_semantic_key: abc123
                        ---
                        """, "abc123")).hasMessageContaining("generated");

        assertThatThrownBy(() -> validator.validateConcept("""
                        ---
                        type: Architecture Investigation
                        generated:
                          by: process:archlens
                          at: '2026-07-19T12:00:00Z'
                        stale_after: '2026-10-17'
                        archlens_generated: true
                        archlens_semantic_key: abc123
                        ---
                        """, "abc123")).hasMessageContaining("status");

        assertThatThrownBy(() -> validator.validateConcept("""
                        ---
                        type: Architecture Investigation
                        generated:
                          by: process:archlens
                        status: draft
                        stale_after: '2026-10-17'
                        archlens_generated: true
                        archlens_semantic_key: abc123
                        ---
                        """, "abc123")).hasMessageContaining("generated.at");
    }

    @Test
    void rejectsAdditionalIndexLinesAndMalformedFrontmatterClose() {
        OkfEntryValidator validator = new OkfEntryValidator();

        assertThatThrownBy(() -> validator.validateIndexEntry("<!-- archlens:abc123 -->\n"
                        + "- [Title](investigations/impact/title-abc123.md) - Description\n"
                        + "extra"))
                .hasMessageContaining("index entry");
        assertThatThrownBy(() -> validator.validateIndexEntry("<!-- archlens:abc123 -->\n"
                        + "- [Title](investigations/impact/title-abc123.md) - Description\n\n"))
                .hasMessageContaining("index entry");
        assertThatThrownBy(() -> validator.validateConcept("""
                        ---
                        type: Architecture Investigation
                        archlens_generated: true
                        archlens_semantic_key: abc123
                        ---corrupted
                        # Question
                        """, "abc123")).hasMessageContaining("frontmatter");
    }
}
