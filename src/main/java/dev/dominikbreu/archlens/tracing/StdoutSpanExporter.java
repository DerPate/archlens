package dev.dominikbreu.archlens.tracing;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;

/**
 * OpenTelemetry {@link SpanExporter} that prints each finished span to stdout — a lightweight
 * exporter for local debugging when no OTLP collector is configured.
 */
public class StdoutSpanExporter implements SpanExporter {

    /**
     * Prints each span in {@code spans} to stdout via {@link #format(SpanData)}.
     *
     * @param spans the finished spans to export
     * @return always a successful result; this exporter cannot fail
     */
    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        for (SpanData span : spans) {
            System.out.println(format(span));
        }
        return CompletableResultCode.ofSuccess();
    }

    /**
     * No-op: every span is already printed synchronously by {@link #export}.
     *
     * @return always a successful, already-complete result
     */
    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    /**
     * No-op: this exporter holds no resources that need releasing.
     *
     * @return always a successful, already-complete result
     */
    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }

    static String format(SpanData span) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(span.getEndEpochNanos() - span.getStartEpochNanos());

        boolean isRoot = !span.getParentSpanContext().isValid();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[trace] %-36s %dms", span.getName(), durationMs));

        if (isRoot) {
            sb.append("  traceId=").append(span.getTraceId());
        }

        span.getAttributes()
                .forEach((key, value) ->
                        sb.append("  ").append(key.getKey()).append("=").append(value));

        StatusData status = span.getStatus();
        if (status.getStatusCode() == StatusCode.ERROR) {
            String desc = status.getDescription();
            if (StringUtils.isNotBlank(desc)) {
                sb.append("  error=").append(desc);
            }
        }

        return sb.toString();
    }
}
