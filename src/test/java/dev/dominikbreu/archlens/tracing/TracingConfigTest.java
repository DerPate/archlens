package dev.dominikbreu.archlens.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TracingConfigTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty("archlens.traces");
        System.clearProperty("archlens.otlp.endpoint");
    }

    @Test
    void noneModeReturnsNoop() {
        System.setProperty("archlens.traces", "none");
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
        System.setProperty("archlens.traces", "console");

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
        System.setProperty("archlens.traces", "otlp");
        System.setProperty("archlens.otlp.endpoint", "http://localhost:4317");

        OpenTelemetry otel = TracingConfig.configure("test-service");
        try {
            Span span = otel.getTracer("test").spanBuilder("my-span").startSpan();
            assertThat(span.getSpanContext().isValid()).isTrue();
            span.end();
        } finally {
            // Otherwise the BatchSpanProcessor's OkHttp dispatcher thread keeps
            // retrying against localhost:4317 (nothing listens there in tests)
            // and is still alive at JVM shutdown, racing class unloading and
            // surfacing as a spurious NoClassDefFoundError in the runner logs.
            if (otel instanceof OpenTelemetrySdk sdk) {
                sdk.getSdkTracerProvider().shutdown();
            }
        }
    }

    @Test
    void unknownModeDefaultsToNoop() {
        System.setProperty("archlens.traces", "garbage");
        OpenTelemetry otel = TracingConfig.configure("test-service");

        Span span = otel.getTracer("test").spanBuilder("my-span").startSpan();
        assertThat(span.getSpanContext().isValid()).isFalse();
        span.end();
    }
}
