package dev.dominikbreu.spoonmcp.tracing;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class StdoutSpanExporter implements SpanExporter {

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        for (SpanData span : spans) {
            System.out.println(format(span));
        }
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

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
            if (desc != null && !desc.isBlank()) {
                sb.append("  error=").append(desc);
            }
        }

        return sb.toString();
    }
}
