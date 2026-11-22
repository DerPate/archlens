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

    private static final String REVIEW_KEYS = """
            verified:
            - { by: "human:reviewer", at: "2026-08-01T09:00:00Z" }
            x-team-owner: payments
            """;

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

    @Test
    void refreshWithSameContentKeepsReviewAndGenerationTime() throws Exception {
        String reviewed = reviewed(CONCEPT);
        seedConcept(reviewed);

        OkfBundleWriter.WriteOutcome outcome = writer().write(refresh(laterRun(CONCEPT)));

        // Only the freshness deadline moves: nothing a reviewer looked at has changed.
        assertThat(conceptPath()).hasContent(reviewed.replace("2026-10-17", "2026-12-01"));
        assertThat(outcome.warnings()).isEmpty();
    }

    @Test
    void refreshWithChangedContentKeepsForeignKeysButRequiresReviewAgain() throws Exception {
        seedConcept(reviewed(CONCEPT));
        String regenerated = laterRun(CONCEPT).replace("What changes?", "Something else changes.");

        OkfBundleWriter.WriteOutcome outcome = writer().write(refresh(regenerated));

        assertThat(conceptPath())
                .hasContent(regenerated.replace("\n---\n# Question", "\n" + REVIEW_KEYS + "---\n# Question"));
        assertThat(outcome.warnings()).singleElement().asString().contains("verification");
    }

    @Test
    void refreshTreatsAChangedAnswerStatusAsChangedContent() throws Exception {
        seedConcept(reviewed(withAnswerStatus(CONCEPT, "resolved")));

        writer().write(refresh(withAnswerStatus(laterRun(CONCEPT), "partial")));

        assertThat(conceptPath())
                .content()
                .contains("at: '2026-09-02T12:00:00Z'", "status: draft", "archlens_status: partial")
                .doesNotContain("status: stable");
    }

    @Test
    void refreshKeepsCuratedDescriptiveFieldsAndUpdatesTheStatusTag() throws Exception {
        String header = "type: Architecture Investigation\ntitle: %s\ntags:\n- %s\n";
        seedConcept(withAnswerStatus(CONCEPT, "resolved")
                .replace(
                        "type: Architecture Investigation\n",
                        header.formatted("Curated title", "resolved\n- payments")));

        writer().write(refresh(withAnswerStatus(laterRun(CONCEPT), "partial")
                .replace("type: Architecture Investigation\n", header.formatted("Generated title", "partial"))));

        assertThat(conceptPath())
                .content()
                .contains("title: Curated title", "- payments", "- partial")
                .doesNotContain("Generated title", "- resolved");
    }

    @Test
    void refreshDoesNotReviveADeprecatedConcept() throws Exception {
        seedConcept(CONCEPT.replace("status: draft", "status: deprecated\nsuperseded_by: /other.md"));

        writer().write(refresh(laterRun(CONCEPT).replace("What changes?", "Something else changes.")));

        assertThat(conceptPath()).content().contains("status: deprecated", "superseded_by: /other.md");
    }

    @Test
    void refreshDoesNotReinterpretValuesItDoesNotOwn() throws Exception {
        // YAML 1.1 would read these as false, 83, and a timestamp if they were parsed and dumped again.
        String foreign = "x-country: NO\nx-code: 0123\nx-day: 2026-09-17 # checked by hand\n";
        seedConcept(CONCEPT.replace("status: draft\n", "status: draft\n" + foreign));

        writer().write(refresh(laterRun(CONCEPT).replace("What changes?", "Something else changes.")));

        assertThat(conceptPath()).content().contains(foreign);
    }

    @Test
    void addsLogSectionBelowTheTitleOfAForeignLog() throws Exception {
        String existing = "# Update Log\n\nCurated by the team.\n\n## 2026-07-19\n\n- **Update**: Older entry.\n";
        Files.writeString(tempDir.resolve("log.md"), existing);

        writer().write(request(false));

        assertThat(tempDir.resolve("log.md"))
                .hasContent(
                        existing.replace("## 2026-07-19", "## 2026-07-20\n\n" + creationEntry() + "\n\n## 2026-07-19"));
    }

    @Test
    void keepsLogNewestFirst() throws Exception {
        Files.writeString(
                tempDir.resolve("log.md"),
                "# Update Log\n\n## 2026-08-01\n\n- Later.\n\n## 2026-07-01\n\n- Earlier.\n");

        writer().write(request(false));

        assertThat(Files.readAllLines(tempDir.resolve("log.md")).stream().filter(line -> line.startsWith("## ")))
                .containsExactly("## 2026-08-01", "## 2026-07-20", "## 2026-07-01");
    }

    @Test
    void addsFirstSectionAfterTheTextOfALogWithoutSections() throws Exception {
        Files.writeString(tempDir.resolve("log.md"), "# Update Log\n\nCurated by the team.\n");

        writer().write(request(false));

        assertThat(tempDir.resolve("log.md"))
                .hasContent("# Update Log\n\nCurated by the team.\n\n## 2026-07-20\n\n" + creationEntry() + "\n");
    }

    @Test
    void escapesBracketsInLinkText() throws Exception {
        writer().write(new OkfBundleWriter.WriteRequest(
                tempDir,
                tempDir.relativize(conceptPath()),
                KEY,
                "impact",
                "Impact - byte[] \\ raw",
                "Compiled evidence.",
                CONCEPT,
                LocalDate.parse("2026-07-20"),
                false));

        String link = "[Impact - byte\\[\\] \\\\ raw](";
        assertThat(tempDir.resolve("index.md")).content().contains(link);
        assertThat(tempDir.resolve("log.md")).content().contains(link);
    }

    private static String reviewed(String concept) {
        return concept.replace("status: draft", "status: stable")
                .replace("\n---\n# Question", "\n" + REVIEW_KEYS + "---\n# Question");
    }

    private static String laterRun(String concept) {
        return concept.replace("2026-07-19T12:00:00Z", "2026-09-02T12:00:00Z").replace("2026-10-17", "2026-12-01");
    }

    private static String withAnswerStatus(String concept, String status) {
        return concept.replace("archlens_generated: true", "archlens_status: " + status + "\narchlens_generated: true");
    }

    private void seedConcept(String markdown) throws Exception {
        Files.createDirectories(conceptPath().getParent());
        Files.writeString(conceptPath(), markdown);
    }

    private OkfBundleWriter.WriteRequest refresh(String markdown) {
        return new OkfBundleWriter.WriteRequest(
                tempDir,
                tempDir.relativize(conceptPath()),
                KEY,
                "impact",
                "Impact investigation",
                "Compiled evidence.",
                markdown,
                LocalDate.parse("2026-09-02"),
                true);
    }

    private String creationEntry() {
        return "- **Creation**: Added [Impact investigation](investigations/impact/orders-" + KEY.substring(0, 12)
                + ".md).";
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
