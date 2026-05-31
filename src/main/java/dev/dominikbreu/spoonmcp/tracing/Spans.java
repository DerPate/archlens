package dev.dominikbreu.spoonmcp.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import java.util.function.Supplier;

/**
 * Runs work inside an OpenTelemetry span: makes the span current for the duration, records and
 * marks any thrown {@link RuntimeException}, and always ends the span. Replaces the repeated
 * {@code startSpan() / try (makeCurrent) / catch record / finally end} boilerplate and avoids
 * nesting those try-with-resources statements.
 *
 * <p>Inside {@code body}, use {@link Span#current()} to set attributes on the active span.
 */
public final class Spans {

    private Spans() {}

    private static Tracer tracer() {
        return GlobalOpenTelemetry.getTracer("dev.dominikbreu.spoonmcp");
    }

    /**
     * Runs {@code body} inside a span named {@code spanName} and returns its result.
     *
     * @param spanName span name
     * @param body work to run with the span current
     * @param <T> result type
     * @return the value produced by {@code body}
     */
    public static <T> T traced(String spanName, Supplier<T> body) {
        Span span = tracer().spanBuilder(spanName).startSpan();
        try (var _ = span.makeCurrent()) {
            return body.get();
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Runs {@code body} inside a span named {@code spanName}.
     *
     * @param spanName span name
     * @param body work to run with the span current
     */
    public static void traced(String spanName, Runnable body) {
        traced(spanName, () -> {
            body.run();
            return null;
        });
    }
}
