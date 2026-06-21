package dev.dominikbreu.archlens.extractor.sourcefacts;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.extractor.ExtractorTestBase;
import dev.dominikbreu.archlens.tracing.StdoutSpanExporter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SourceFactTracingTest extends ExtractorTestBase {

    @AfterEach
    void resetGlobalTracing() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void emitsSourceFactPhaseSpansAndCounts() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try (PrintStream redirected = new PrintStream(captured)) {
            System.setOut(redirected);
            GlobalOpenTelemetry.resetForTest();
            SdkTracerProvider provider = SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(new StdoutSpanExporter()))
                    .build();
            GlobalOpenTelemetry.set(
                    OpenTelemetrySdk.builder().setTracerProvider(provider).build());

            new SourceFactIndexBuilder().build(scan("quarkus-sample"), "quarkus-sample", 1);

            provider.forceFlush();
        } finally {
            System.setOut(originalOut);
        }

        assertThat(captured.toString())
                .contains("sourcefacts.build")
                .contains("sourcefacts.members")
                .contains("sourcefacts.inheritance")
                .contains("sourcefacts.invocations")
                .contains("sourcefacts.assignments")
                .contains("sourcefacts.returns")
                .contains("sourcefacts.injection")
                .contains("type-count=")
                .contains("method-count=")
                .contains("field-count=")
                .contains("invocation-count=")
                .contains("assignment-count=")
                .contains("return-fact-count=")
                .contains("injection-point-count=");
    }
}
