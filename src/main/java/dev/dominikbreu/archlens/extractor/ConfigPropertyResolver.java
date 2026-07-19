package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.ConfigProperty;
import dev.dominikbreu.archlens.model.SourceInfo;
import dev.dominikbreu.archlens.model.ids.AppId;
import java.io.File;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Resolves non-secret configuration properties from {@code application.properties}/{@code .yaml}/
 * {@code .yml} inside a module's {@code src/main/resources} directory.
 *
 * <p>This is the second (and, deliberately, still narrowly-scoped) configuration file reader in
 * the extractor pipeline, alongside {@link MessagingConfigResolver}. Both share
 * {@link PropertyFileReader} for the actual file I/O. Any property whose key looks secret-like is
 * dropped entirely — never projected as key or value — via the shared {@link SecretKeyFilter}.
 */
public class ConfigPropertyResolver {

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
        resolve(PropertyFileReader.readAll(resources), appId, model);
    }

    void resolve(Map<String, PropertyFileReader.PropertyEntry> flat, AppId appId, ArchitectureModel model) {
        for (Map.Entry<String, PropertyFileReader.PropertyEntry> entry : flat.entrySet()) {
            if (SecretKeyFilter.isSecretKey(entry.getKey())) continue;
            PropertyFileReader.PropertyEntry propertyEntry = entry.getValue();
            ConfigProperty property = new ConfigProperty();
            property.id = "config:" + appId.serialize() + ":" + entry.getKey();
            property.key = entry.getKey();
            property.value = propertyEntry.value();
            property.resolved = propertyEntry.value() == null
                    || !PLACEHOLDER.matcher(propertyEntry.value()).find();
            property.appId = appId;
            property.sourceFile = propertyEntry.sourceFile();
            property.source = new SourceInfo(property.sourceFile, 0, "config-file", 0.8);
            model.configProperties.add(property);
        }
    }
}
