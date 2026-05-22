package dev.dominikbreu.spoonmcp.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class TracingConfigTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty("spoon.traces");
        System.clearProperty("spoon.otlp.endpoint");
    }

    @Test
    void noneModeReturnsNoop() {
        System.setProperty("spoon.traces", "none");
        OpenTelemetry otel = TracingConfig.configure("test-service");

        Span span = otel.getTracer("test").spanBuilder("my-span").startSpan();
        assertThat(span.getSpanContext().isValid()).isFalse();
        span.end();
    }

    @Test
    void defaultModeIsNoop() {
        OpenTelemetry otel = TracingConfig.configure("test-service");

        Span span = otel.getTracer("test").spanBuilder("my-span").startSpan();
        assertThat(span.getSpanContext().isValid()).isFalse();
        span.end();
    }

    @Test
    void consoleModeProducesValidSpans() {
        System.setProperty("spoon.traces", "console");

        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
        try {
            OpenTelemetry otel = TracingConfig.configure("test-service");

            Span span = otel.getTracer("test").spanBuilder("my-span").startSpan();
            assertThat(span.getSpanContext().isValid()).isTrue();
            span.end();

            if (otel instanceof OpenTelemetrySdk sdk) {
                sdk.getSdkTracerProvider().forceFlush();
            }

            assertThat(captured.toString()).contains("[trace]").contains("my-span");
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void otlpModeProducesValidSpans() {
        System.setProperty("spoon.traces", "otlp");
        System.setProperty("spoon.otlp.endpoint", "http://localhost:4317");

        OpenTelemetry otel = TracingConfig.configure("test-service");

        Span span = otel.getTracer("test").spanBuilder("my-span").startSpan();
        assertThat(span.getSpanContext().isValid()).isTrue();
        span.end();
    }

    @Test
    void unknownModeDefaultsToNoop() {
        System.setProperty("spoon.traces", "garbage");
        OpenTelemetry otel = TracingConfig.configure("test-service");

        Span span = otel.getTracer("test").spanBuilder("my-span").startSpan();
        assertThat(span.getSpanContext().isValid()).isFalse();
        span.end();
    }
}
