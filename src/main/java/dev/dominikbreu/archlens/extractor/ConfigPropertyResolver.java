package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.ConfigProperty;
import dev.dominikbreu.archlens.model.SourceInfo;
import dev.dominikbreu.archlens.model.ids.AppId;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Resolves non-secret configuration properties from {@code application.properties}/{@code .yaml}/
 * {@code .yml} inside a module's {@code src/main/resources} directory.
 *
 * <p>This is the second (and, deliberately, still narrowly-scoped) configuration file reader in
 * the extractor pipeline, alongside {@link MessagingConfigResolver}. Both share
 * {@link PropertyFileReader} for the actual file I/O. Any property whose key looks secret-like is
 * dropped entirely — never projected as key or value — mirroring
 * {@code PersistenceTopologyExtractor.isSecretKey}.
 */
public class ConfigPropertyResolver {

    private static final List<String> SECRET_MARKERS =
            List.of("password", "username", "credential", "secret", "token", "apikey", "api-key", "private-key");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^}]+}");
    private static final String RESOURCES_SUBDIR = "src/main/resources";

    /**
     * Resolves and appends non-secret configuration properties for one module.
     *
     * @param moduleRoot module root directory (the one containing {@code src/main/resources})
     * @param appId the application/module id these properties belong to
     * @param model the architecture model to append discovered properties to
     */
    public void resolve(File moduleRoot, AppId appId, ArchitectureModel model) {
        File resources = new File(moduleRoot, RESOURCES_SUBDIR);
        Map<String, String> flat = PropertyFileReader.readAll(resources);
        for (Map.Entry<String, String> entry : flat.entrySet()) {
            if (isSecretKey(entry.getKey())) continue;
            ConfigProperty property = new ConfigProperty();
            property.id = "config:" + appId.serialize() + ":" + entry.getKey();
            property.key = entry.getKey();
            property.value = entry.getValue();
            property.resolved = entry.getValue() == null
                    || !PLACEHOLDER.matcher(entry.getValue()).find();
            property.appId = appId;
            property.sourceFile = resourceFileName(resources);
            property.source = new SourceInfo(property.sourceFile, 0, "config-file", 0.8);
            model.configProperties.add(property);
        }
    }

    private static boolean isSecretKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return SECRET_MARKERS.stream().anyMatch(lower::contains);
    }

    private static String resourceFileName(File resources) {
        for (String candidate : List.of("application.properties", "application.yaml", "application.yml")) {
            if (new File(resources, candidate).isFile()) return candidate;
        }
        return "application.properties";
    }
}
