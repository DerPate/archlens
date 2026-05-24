package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.tracing.StdoutSpanExporter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ArchitectureExtractorTracingTest extends ExtractorTestBase {

    @AfterEach
    void resetGlobalTracing() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void reusesPassOneModelAndEmitsDetailedPassTwoSpans() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
        try {
            GlobalOpenTelemetry.resetForTest();
            SdkTracerProvider provider = SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(new StdoutSpanExporter()))
                    .build();
            GlobalOpenTelemetry.set(OpenTelemetrySdk.builder().setTracerProvider(provider).build());

            new ArchitectureExtractor().extract(List.of(projectPath("plain-java-sample")));

            provider.forceFlush();
        } finally {
            System.setOut(originalOut);
        }

        assertThat(captured.toString())
                .contains("ctmodel.build")
                .contains("phase=pass1-scan")
                .doesNotContain("phase=pass2-callgraph")
                .contains("pass2-enrichment")
                .contains("dependency.extract")
                .contains("objectflow.build")
                .contains("sourcefacts.build")
                .contains("sourcefacts.members")
                .contains("sourcefacts.inheritance");
        assertThat(captured.toString().split("ctmodel\\.build", -1).length - 1).isEqualTo(1);
    }
}
