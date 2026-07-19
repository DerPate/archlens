package dev.dominikbreu.archlens.extractor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.yaml.snakeyaml.Yaml;

/** Reads and flattens {@code application.properties}/{@code .yaml}/{@code .yml} resource files. */
final class PropertyFileReader {

    private static final List<String> RESOURCE_FILES =
            List.of("application.properties", "application.yaml", "application.yml");

    private PropertyFileReader() {}

    /**
     * Reads and flattens every {@code application.*} resource file under {@code resourcesDir},
     * merging keys across files (first file wins on conflict).
     *
     * @param resourcesDir the module's {@code src/main/resources} directory
     * @return flattened dotted-key to string-value map; empty when the directory is absent
     */
    static Map<String, String> readAll(File resourcesDir) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (!resourcesDir.isDirectory()) return merged;
        for (String name : RESOURCE_FILES) {
            File file = new File(resourcesDir, name);
            if (!file.isFile()) continue;
            try {
                Map<String, String> flat = name.endsWith(".properties") ? readProperties(file) : readYaml(file);
                for (Map.Entry<String, String> entry : flat.entrySet()) {
                    merged.putIfAbsent(entry.getKey(), entry.getValue());
                }
            } catch (IOException _) {
            }
        }
        return merged;
    }

    private static Map<String, String> readProperties(File file) throws IOException {
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
    private static Map<String, String> readYaml(File file) throws IOException {
        Yaml yaml = new Yaml();
        Object root;
        try (InputStream in = Files.newInputStream(file.toPath())) {
            root = yaml.load(in);
        }
        Map<String, String> out = new LinkedHashMap<>();
        if (!(root instanceof Map<?, ?> map)) return out;
        Map<String, Object> flat = new LinkedHashMap<>();
        flatten("", (Map<String, Object>) map, flat);
        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            out.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> in, Map<String, Object> out) {
        for (Map.Entry<String, Object> entry : in.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                flatten(key, (Map<String, Object>) nested, out);
            } else {
                out.put(key, value);
            }
        }
    }
}
