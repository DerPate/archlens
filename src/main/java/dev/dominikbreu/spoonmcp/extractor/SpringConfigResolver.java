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

public class SpringConfigResolver {

    private static final List<String> RESOURCE_FILES =
            List.of("application.properties", "application.yaml", "application.yml");

    public Config resolve(File moduleRoot) {
        Map<String, String> values = new LinkedHashMap<>();
        File resources = new File(moduleRoot, "src/main/resources");
        if (!resources.isDirectory()) return new Config(values);

        for (String name : RESOURCE_FILES) {
            File file = new File(resources, name);
            if (!file.isFile()) continue;
            try {
                Map<String, String> parsed = name.endsWith(".properties") ? readProperties(file) : readYaml(file);
                values.putAll(parsed);
            } catch (IOException ignored) {
            }
        }
        return new Config(values);
    }

    public static final class Config {
        private final Map<String, String> values;

        Config(Map<String, String> values) {
            this.values = Map.copyOf(values);
        }

        public String value(String key) {
            return values.get(key);
        }

        public String resolve(String value) {
            if (value == null) return null;
            if (value.startsWith("${") && value.endsWith("}") && value.indexOf("${", 2) == -1) {
                String key = value.substring(2, value.length() - 1);
                return values.getOrDefault(key, value);
            }
            // Handle embedded placeholders like "${billing.base-url}/health"
            if (value.contains("${")) {
                String result = value;
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    result = result.replace("${" + entry.getKey() + "}", entry.getValue());
                }
                return result;
            }
            return value;
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
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                flatten(key, (Map<String, Object>) nested, out);
            } else {
                out.put(key, String.valueOf(value));
            }
        }
    }
}
