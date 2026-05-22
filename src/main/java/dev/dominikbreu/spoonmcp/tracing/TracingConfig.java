package dev.dominikbreu.spoonmcp.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracingConfig {

    private static final Logger log = LoggerFactory.getLogger(TracingConfig.class);

    private TracingConfig() {}

    /**
     * Reads {@code -Dspoon.traces=none|console|otlp} and builds an OpenTelemetry instance.
     * The caller is responsible for registering it via {@code GlobalOpenTelemetry.set()}.
     * Falls back to noop if configuration fails.
     */
    public static OpenTelemetry configure(String serviceName) {
        String mode = System.getProperty("spoon.traces", "none");
        String endpoint = System.getProperty("spoon.otlp.endpoint", "http://localhost:4317");
        try {
            if (!mode.equals("console") && !mode.equals("otlp")) {
                return OpenTelemetry.noop();
            }

            Resource resource = Resource.create(Attributes.of(
                    AttributeKey.stringKey("service.name"), serviceName));

            SdkTracerProvider tracerProvider = switch (mode) {
                case "console" -> SdkTracerProvider.builder()
                        .setResource(resource)
                        .addSpanProcessor(SimpleSpanProcessor.create(new StdoutSpanExporter()))
                        .build();
                case "otlp" -> SdkTracerProvider.builder()
                        .setResource(resource)
                        .addSpanProcessor(BatchSpanProcessor.builder(
                                OtlpGrpcSpanExporter.builder()
                                        .setEndpoint(endpoint)
                                        .build())
                                .build())
                        .build();
                default -> throw new IllegalStateException("unreachable: mode=" + mode);
            };

            return OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to configure tracing (mode={}) — using noop: {}", mode, e.getMessage());
            return OpenTelemetry.noop();
        }
    }
}
