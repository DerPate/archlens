package dev.dominikbreu.spoonmcp.model;

import tools.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional naming configuration that maps entrypoint IDs to human-readable use case names.
 *
 * <p>Example JSON ({@code use-cases.json}):
 * <pre>{@code
 * {
 *   "names": {
 *     "ep:com.example.OrderResource#createOrder": "Create Order",
 *     "ep:com.example.DeviceConsumer#handle:msg-in:device-events": "Process Device Event"
 *   }
 * }
 * }</pre>
 */
public class UseCaseNamingConfig {

    /** Maps entrypoint ID to a human-readable use case name. */
    public Map<String, String> names = new LinkedHashMap<>();

    /** Creates an empty config (all use case names are auto-derived). */
    public UseCaseNamingConfig() {}

    /**
     * Returns the configured name for the given entrypoint ID, or the auto-derived default.
     *
     * @param entrypointId entrypoint identifier to look up
     * @param defaultName  fallback name when no mapping is configured
     * @return resolved display name
     */
    public String resolveName(String entrypointId, String defaultName) {
        return names.getOrDefault(entrypointId, defaultName);
    }

    /**
     * Loads a naming config from a JSON file.
     *
     * @param mapper Jackson object mapper
     * @param path   path to the JSON file
     * @return loaded config
     * @throws IOException when the file cannot be read or parsed
     */
    public static UseCaseNamingConfig loadFrom(ObjectMapper mapper, String path) throws IOException {
        return mapper.readValue(new File(path), UseCaseNamingConfig.class);
    }

    /**
     * Returns an empty config with no name mappings.
     *
     * @return new empty {@link UseCaseNamingConfig}
     */
    public static UseCaseNamingConfig empty() {
        return new UseCaseNamingConfig();
    }
}
