package dev.dominikbreu.spoonmcp.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StdoutSpanExporterTest {

    private PrintStream originalOut;
    private ByteArrayOutputStream captured;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        originalOut = System.out;
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));

        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(new StdoutSpanExporter()))
                .build();
        tracer = OpenTelemetrySdk.builder().setTracerProvider(provider).build().getTracer("test");
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    void rootSpanIncludesTraceId() {
        Span span = tracer.spanBuilder("extract").startSpan();
        span.end();

        String out = captured.toString();
        assertThat(out).contains("[trace]");
        assertThat(out).contains("extract");
        assertThat(out).contains("ms");
        assertThat(out).contains("traceId=");
    }

    @Test
    void childSpanOmitsTraceId() {
        Span parent = tracer.spanBuilder("extract").startSpan();
        try (var _ = parent.makeCurrent()) {
            Span child = tracer.spanBuilder("pass1-scan").startSpan();
            child.end();
        } finally {
            parent.end();
        }

        String[] lines = captured.toString().split(System.lineSeparator());
        String childLine = lines[0]; // child ends first with SimpleSpanProcessor
        String parentLine = lines[1];
        assertThat(childLine).contains("pass1-scan").doesNotContain("traceId=");
        assertThat(parentLine).contains("extract").contains("traceId=");
    }

    @Test
    void spanAttributesAppearAsKeyValuePairs() {
        Span span = tracer.spanBuilder("pass1-scan").startSpan();
        span.setAttribute("modules", 3L);
        span.end();

        assertThat(captured.toString()).contains("modules=3");
    }

    @Test
    void errorSpanIncludesErrorField() {
        Span span = tracer.spanBuilder("extract").startSpan();
        span.setStatus(StatusCode.ERROR, "something went wrong");
        span.end();

        assertThat(captured.toString()).contains("error=something went wrong");
    }
}
