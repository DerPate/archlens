package dev.dominikbreu.archlens.okf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OkfBundleWriterTest {
    private static final String KEY = "a".repeat(64);
    private static final String CONCEPT = """
            ---
            type: Architecture Investigation
            generated:
              by: process:archlens
              at: '2026-07-19T12:00:00Z'
            status: draft
            stale_after: '2026-10-17'
            archlens_generated: true
            archlens_semantic_key: %s
            ---
            # Question
            What changes?
            """.formatted(KEY);

    @TempDir
    Path tempDir;

    @Test
    void createsConceptIndexAndLog() throws Exception {
        OkfBundleWriter.WriteOutcome outcome = writer().write(request(false));

        assertThat(outcome.status()).isEqualTo("created");
        assertThat(outcome.conceptPath()).hasContent(CONCEPT);
        assertThat(outcome.indexPath()).content().contains("<!-- archlens:" + KEY + " -->");
        assertThat(outcome.logPath()).content().contains("**Creation**");
    }

    @Test
    void freshIndexDeclaresOkfVersion() throws Exception {
        OkfBundleWriter.WriteOutcome outcome = writer().write(request(false));

        assertThat(outcome.indexPath())
                .content()
                .startsWith("---\nokf_version: \"0.2\"\n---\n\n# Architecture Investigations");
    }

    @Test
    void backfillsOkfVersionOnExistingIndexWithoutFrontmatter() throws Exception {
        Files.writeString(
                tempDir.resolve("index.md"),
                "# Architecture Investigations\n\n## Impact\n\n<!-- archlens:unrelated -->\n- [Other](other.md) - Keep this.\n");

        writer().write(request(false));

        assertThat(tempDir.resolve("index.md"))
                .content()
                .startsWith("---\nokf_version: \"0.2\"\n---\n\n# Architecture Investigations")
                .contains("<!-- archlens:unrelated -->\n- [Other](other.md) - Keep this.");
    }

    @Test
    void leavesExistingOkfVersionFrontmatterUntouched() throws Exception {
        Files.writeString(
                tempDir.resolve("index.md"),
                "---\nokf_version: \"0.2\"\ncustom: kept\n---\n\n# Architecture Investigations\n");

        writer().write(request(false));

        assertThat(tempDir.resolve("index.md"))
                .content()
                .startsWith("---\nokf_version: \"0.2\"\ncustom: kept\n---\n\n# Architecture Investigations");
    }

    @Test
    void existingGeneratedConceptRequiresExplicitOverwrite() throws Exception {
        writer().write(request(false));

        OkfBundleWriter.WriteOutcome preview = writer().write(request(false));

        assertThat(preview.status()).isEqualTo("overwrite-required");
        assertThat(preview.warnings()).singleElement().asString().contains("allowOverwrite");
        assertThat(preview.logPath()).content().doesNotContain("**Refresh**");

        OkfBundleWriter.WriteOutcome updated = writer().write(request(true));

        assertThat(updated.status()).isEqualTo("updated");
        assertThat(updated.logPath()).content().contains("**Refresh**");
    }

    @Test
    void refusesNonGeneratedAndDifferentlyKeyedTargets() throws Exception {
        Files.createDirectories(conceptPath().getParent());
        Files.writeString(conceptPath(), "human authored");

        assertThatThrownBy(() -> writer().write(request(true))).hasMessageContaining("not ArchLens-generated");

        Files.writeString(conceptPath(), CONCEPT.replace(KEY, "b".repeat(64)));

        assertThatThrownBy(() -> writer().write(request(true))).hasMessageContaining("different semantic key");
    }

    @Test
    void replacesOnlyItsIndexBulletAndPreservesOlderLogSections() throws Exception {
        Files.writeString(
                tempDir.resolve("index.md"),
                "# Architecture Investigations\n\n## Impact\n\n"
                        + "<!-- archlens:"
                        + KEY
                        + " -->\n- [Old](old.md) - Old description\n\n"
                        + "<!-- archlens:unrelated -->\n- [Other](other.md) - Keep this.\n");
        Files.writeString(
                tempDir.resolve("log.md"),
                "# Architecture Investigation Log\n\n## 2026-07-19\n\n- **Creation**: Older entry.\n");

        writer().write(request(false));

        assertThat(tempDir.resolve("index.md"))
                .content()
                .contains("- [Impact investigation](investigations/impact/orders-" + KEY.substring(0, 12)
                        + ".md) - Compiled evidence.")
                .contains("<!-- archlens:unrelated -->\n- [Other](other.md) - Keep this.")
                .doesNotContain("[Old](old.md)");
        assertThat(tempDir.resolve("log.md"))
                .content()
                .contains("## 2026-07-20\n\n- **Creation**")
                .contains("## 2026-07-19\n\n- **Creation**: Older entry.");
    }

    @Test
    void preservesNonBulletContentAfterMatchingIndexMarker() throws Exception {
        Files.writeString(
                tempDir.resolve("index.md"),
                "# Architecture Investigations\n\n<!-- archlens:" + KEY + " -->\nHuman note that must stay.\n");

        writer().write(request(false));

        assertThat(tempDir.resolve("index.md"))
                .content()
                .contains("<!-- archlens:" + KEY + " -->\nHuman note that must stay.")
                .contains("- [Impact investigation](investigations/impact/orders-" + KEY.substring(0, 12)
                        + ".md) - Compiled evidence.");
    }

    private OkfBundleWriter writer() {
        return new OkfBundleWriter();
    }

    private OkfBundleWriter.WriteRequest request(boolean allowOverwrite) {
        return new OkfBundleWriter.WriteRequest(
                tempDir,
                Path.of("investigations", "impact", "orders-" + KEY.substring(0, 12) + ".md"),
                KEY,
                "impact",
                "Impact investigation",
                "Compiled evidence.",
                CONCEPT,
                LocalDate.parse("2026-07-20"),
                allowOverwrite);
    }

    private Path conceptPath() {
        return tempDir.resolve("investigations").resolve("impact").resolve("orders-" + KEY.substring(0, 12) + ".md");
    }
}
