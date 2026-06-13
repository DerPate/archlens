package dev.dominikbreu.spoonmcp.extractor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.yaml.snakeyaml.Yaml;

/** Reads and resolves Spring config properties from {@code application.properties} or {@code application.yaml}. */
public class SpringConfigResolver {

    /** Creates a resolver with default settings. */
    public SpringConfigResolver() {}

    private static final List<String> RESOURCE_FILES =
            List.of("application.properties", "application.yaml", "application.yml");

    /**
     * Loads a merged {@link Config} from all recognised config files under the given module root.
     *
     * @param moduleRoot the module root directory
     * @return the merged config (empty if no config files are found)
     */
    public Config resolve(File moduleRoot) {
        Map<String, String> values = new LinkedHashMap<>();
        File resources = new File(moduleRoot, "src/main/resources");
        if (!resources.isDirectory()) return new Config(values);

        for (String name : RESOURCE_FILES) {
            File file = new File(resources, name);
            if (!file.isFile()) continue;
            try {
                Map<String, String> parsed;
                if (name.endsWith(".properties")) {
                    parsed = readProperties(file);
                } else {
                    parsed = readYaml(file);
                }
                values.putAll(parsed);
            } catch (IOException _) {
            }
        }
        return new Config(values);
    }

    /**
     * Returns an empty config with no properties.
     *
     * @return an empty {@link Config}
     */
    public Config emptyConfig() {
        return new Config(Map.of());
    }

    /**
     * The result of resolving a Spring property placeholder expression.
     *
     * @param value the resolved value, or the original expression if unresolved
     * @param propertyKey the property key that was matched, or {@code null}
     * @param wasResolved true if the placeholder was resolved to a concrete value
     */
    public record ResolvedValue(String value, String propertyKey, boolean wasResolved) {}

    /** Immutable view of resolved Spring config properties. */
    public static final class Config {
        private final Map<String, String> values;

        Config(Map<String, String> values) {
            this.values = Map.copyOf(values);
        }

        /**
         * Returns the raw config value for the given key, or {@code null} if absent.
         *
         * @param key the property key
         * @return the value, or {@code null}
         */
        public String value(String key) {
            return values.get(key);
        }

        /**
         * Resolves a Spring placeholder expression to its concrete value, returning metadata.
         *
         * @param value the expression to resolve (e.g. {@code "${my.prop}"})
         * @return the resolved value and resolution metadata
         */
        public ResolvedValue resolveWithKey(String value) {
            if (value == null) return new ResolvedValue(null, null, false);
            if (value.startsWith("${") && value.endsWith("}") && value.indexOf("${", 2) == -1) {
                String key = value.substring(2, value.length() - 1);
                String resolved = values.get(key);
                return new ResolvedValue(resolved != null ? resolved : value, key, resolved != null);
            }
            if (value.contains("${")) {
                String result = value;
                String lastKey = null;
                boolean changed = false;
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    String token = "${" + entry.getKey() + "}";
                    if (result.contains(token)) {
                        result = result.replace(token, entry.getValue());
                        lastKey = entry.getKey();
                        changed = true;
                    }
                }
                return new ResolvedValue(result, lastKey, changed);
            }
            return new ResolvedValue(value, null, false);
        }

        /**
         * Resolves a Spring placeholder expression to its concrete value.
         *
         * @param value the expression to resolve
         * @return the resolved value, or the original expression if unresolved
         */
        public String resolve(String value) {
            return resolveWithKey(value).value();
        }
    }

    private Map<String, String> readProperties(File file) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file.toPath())) {
            props.load(in);
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            out.put(key, props.getProperty(key));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readYaml(File file) throws IOException {
        Object root;
        try (InputStream in = Files.newInputStream(file.toPath())) {
            root = new Yaml().load(in);
        }
        Map<String, String> out = new LinkedHashMap<>();
        if (!(root instanceof Map<?, ?> map)) return out;
        flatten("", (Map<String, Object>) map, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> in, Map<String, String> out) {
        for (Map.Entry<String, Object> entry : in.entrySet()) {
            String key;
            if (prefix.isEmpty()) {
                key = entry.getKey();
            } else {
                key = prefix + "." + entry.getKey();
            }
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                flatten(key, (Map<String, Object>) nested, out);
            } else {
                out.put(key, String.valueOf(value));
            }
        }
    }
}
