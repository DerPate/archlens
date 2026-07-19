package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.build.BuildModule;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.DataSourceInfo;
import dev.dominikbreu.archlens.model.DataSourceUsage;
import dev.dominikbreu.archlens.model.ExternalSystem;
import dev.dominikbreu.archlens.model.PersistenceUnitInfo;
import dev.dominikbreu.archlens.model.PersistenceUnitUsage;
import dev.dominikbreu.archlens.model.SourceInfo;
import dev.dominikbreu.archlens.model.ids.AppId;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;

/** Extracts JPA persistence units, datasource bindings, and safe database topology facts. */
public class PersistenceTopologyExtractor {

    private static final Set<String> PERSISTENCE_CONTEXT = Set.of("PersistenceContext");
    private static final Set<String> RESOURCE = Set.of("Resource");
    private static final Set<String> DATA_SOURCE_DEFINITION = Set.of("DataSourceDefinition");

    /** Creates an extractor with secure XML parsing and bounded project-local discovery. */
    public PersistenceTopologyExtractor() {}

    /**
     * Extracts persistence configuration for one module and connects its source references.
     *
     * @param module build module whose descriptors and source types are inspected
     * @param types Spoon types belonging to the module
     * @param model architecture model to enrich
     * @param appId owning application identifier
     */
    public void extract(BuildModule module, Collection<CtType<?>> types, ArchitectureModel model, AppId appId) {
        List<Path> files = descriptorFiles(module);
        files.stream()
                .filter(PersistenceTopologyExtractor::isPersistenceXml)
                .forEach(path -> parsePersistenceXml(path, model, appId));
        files.stream()
                .filter(PersistenceTopologyExtractor::isResourceReferenceDescriptor)
                .forEach(path -> parseResourceReferences(path, model, appId));
        files.stream()
                .filter(PersistenceTopologyExtractor::isWildFlyDescriptor)
                .forEach(path -> parseWildFlyDataSources(path, model, appId));
        extractSpringDataSource(module, model, appId);
        extractSourceReferences(types, model, appId);
        reconcile(model, appId);
    }

    private void parsePersistenceXml(Path path, ArchitectureModel model, AppId appId) {
        Document document = parseXml(path);
        if (document == null) return;
        for (Element unitElement : descendants(document.getDocumentElement(), "persistence-unit")) {
            String name = attribute(unitElement, "name");
            if (blank(name)) continue;
            PersistenceUnitInfo unit = new PersistenceUnitInfo();
            unit.id = persistenceUnitId(appId, name);
            unit.name = name;
            unit.appId = appId;
            unit.provider = childText(unitElement, "provider");
            unit.transactionType = attribute(unitElement, "transaction-type");
            unit.jtaDataSource = childText(unitElement, "jta-data-source");
            unit.nonJtaDataSource = childText(unitElement, "non-jta-data-source");
            unit.managedClasses.addAll(childTexts(unitElement, "class"));
            unit.mappingFiles.addAll(childTexts(unitElement, "mapping-file"));
            collectPlaceholders(unit, name, unit.provider, unit.jtaDataSource, unit.nonJtaDataSource);
            unit.managedClasses.forEach(value -> collectPlaceholders(unit, value));
            unit.mappingFiles.forEach(value -> collectPlaceholders(unit, value));
            unit.source = source(path, "persistence.xml", 1.0);
            upsertPersistenceUnit(model, unit);
        }
    }

    private void parseResourceReferences(Path path, ArchitectureModel model, AppId appId) {
        Document document = parseXml(path);
        if (document == null) return;
        for (Element ref : descendants(document.getDocumentElement(), "resource-ref")) {
            String type = childText(ref, "res-type");
            if (!blank(type) && !type.endsWith("DataSource")) continue;
            String lookup = firstNonBlank(
                    childText(ref, "lookup-name"), childText(ref, "mapped-name"), childText(ref, "res-ref-name"));
            if (blank(lookup)) continue;
            addDataSourceUsage(model, appId, null, lookup, source(path, "resource-ref", 0.95));
        }
    }

    private void parseWildFlyDataSources(Path path, ArchitectureModel model, AppId appId) {
        Document document = parseXml(path);
        if (document == null) return;
        List<Element> declarations = new ArrayList<>();
        declarations.addAll(descendants(document.getDocumentElement(), "datasource"));
        declarations.addAll(descendants(document.getDocumentElement(), "xa-datasource"));
        for (Element element : declarations) {
            String jndiName = firstNonBlank(attribute(element, "jndi-name"), childText(element, "jndi-name"));
            String poolName = firstNonBlank(attribute(element, "pool-name"), attribute(element, "name"));
            if (blank(jndiName) && blank(poolName)) continue;
            String rawUrl = childText(element, "connection-url");
            if (blank(rawUrl)) {
                rawUrl = xaUrl(element);
            }
            DataSourceInfo dataSource = new DataSourceInfo();
            dataSource.name = firstNonBlank(poolName, jndiName);
            dataSource.appId = appId;
            dataSource.jndiName = jndiName;
            dataSource.driver = firstNonBlank(childText(element, "driver"), attribute(element, "driver-name"));
            dataSource.endpoint = sanitizeEndpoint(rawUrl);
            dataSource.databaseKind = databaseKind(rawUrl, dataSource.driver);
            dataSource.declarationKind = "wildfly-config";
            dataSource.unresolved = blank(dataSource.endpoint) && containsPlaceholder(rawUrl);
            dataSource.source = source(path, "wildfly-config", 1.0);
            dataSource.id = dataSourceId(appId, firstNonBlank(jndiName, dataSource.name));
            upsertDataSource(model, dataSource);
        }
    }

    private void extractSpringDataSource(BuildModule module, ArchitectureModel model, AppId appId) {
        for (Path path : springConfigFiles(module)) {
            Map<String, String> values = readSpringValues(path);
            String jndi = values.get("spring.datasource.jndi-name");
            String rawUrl = values.get("spring.datasource.url");
            String driver = values.get("spring.datasource.driver-class-name");
            if (blank(jndi) && blank(rawUrl) && blank(driver)) continue;
            DataSourceInfo dataSource = new DataSourceInfo();
            dataSource.name = firstNonBlank(jndi, "spring.datasource");
            dataSource.appId = appId;
            dataSource.jndiName = jndi;
            dataSource.driver = driver;
            dataSource.endpoint = sanitizeEndpoint(rawUrl);
            dataSource.databaseKind = databaseKind(rawUrl, driver);
            dataSource.declarationKind = "spring-config";
            dataSource.unresolved = containsPlaceholder(jndi) || containsPlaceholder(rawUrl);
            dataSource.source = source(path, "spring-config", 1.0);
            dataSource.id = dataSourceId(appId, dataSource.name);
            upsertDataSource(model, dataSource);
            addDataSourceUsage(model, appId, null, dataSource.name, dataSource.source);
        }
    }

    private void extractSourceReferences(Collection<CtType<?>> types, ArchitectureModel model, AppId appId) {
        for (CtType<?> type : types) {
            ComponentId componentId = ComponentId.of(type.getQualifiedName());
            for (CtAnnotation<?> annotation : type.getAnnotations()) {
                extractAnnotation(annotation, type, componentId, model, appId);
            }
            for (CtField<?> field : type.getFields()) {
                for (CtAnnotation<?> annotation : field.getAnnotations()) {
                    extractAnnotation(annotation, field, componentId, model, appId);
                }
                if (isEntityManager(field)
                        && model.persistenceUnits.stream()
                                        .filter(unit -> appId.equals(unit.appId))
                                        .count()
                                == 1) {
                    String unitName = model.persistenceUnits.stream()
                            .filter(unit -> appId.equals(unit.appId))
                            .findFirst()
                            .map(unit -> unit.name)
                            .orElse("");
                    addPersistenceUsage(model, appId, componentId, unitName, source(field, "type-relation", 0.8));
                }
            }
            for (CtMethod<?> method : type.getMethods()) {
                for (CtAnnotation<?> annotation : method.getAnnotations()) {
                    extractAnnotation(annotation, method, componentId, model, appId);
                }
                for (CtParameter<?> parameter : method.getParameters()) {
                    for (CtAnnotation<?> annotation : parameter.getAnnotations()) {
                        extractAnnotation(annotation, parameter, componentId, model, appId);
                    }
                }
            }
        }
    }

    private void extractAnnotation(
            CtAnnotation<?> annotation,
            CtElement element,
            ComponentId componentId,
            ArchitectureModel model,
            AppId appId) {
        String simpleName = annotation.getAnnotationType().getSimpleName();
        if (PERSISTENCE_CONTEXT.contains(simpleName)) {
            addPersistenceUsage(
                    model,
                    appId,
                    componentId,
                    annotationString(annotation, "unitName"),
                    source(element, "annotation", 1.0));
        } else if (RESOURCE.contains(simpleName)) {
            String lookup =
                    firstNonBlank(annotationString(annotation, "lookup"), annotationString(annotation, "mappedName"));
            String name = annotationString(annotation, "name");
            if (isDataSourceElement(element) || looksLikeDataSourceName(lookup) || looksLikeDataSourceName(name)) {
                addDataSourceUsage(
                        model, appId, componentId, firstNonBlank(lookup, name), source(element, "annotation", 0.95));
            }
        } else if (DATA_SOURCE_DEFINITION.contains(simpleName)) {
            String name = annotationString(annotation, "name");
            if (blank(name)) return;
            String rawUrl = annotationString(annotation, "url");
            DataSourceInfo dataSource = new DataSourceInfo();
            dataSource.id = dataSourceId(appId, name);
            dataSource.name = name;
            dataSource.appId = appId;
            dataSource.jndiName = name;
            dataSource.driver = firstNonBlank(
                    annotationString(annotation, "className"), annotationString(annotation, "driverClassName"));
            dataSource.endpoint = sanitizeEndpoint(rawUrl);
            dataSource.databaseKind = databaseKind(rawUrl, dataSource.driver);
            dataSource.declarationKind = "annotation";
            dataSource.unresolved = containsPlaceholder(rawUrl);
            dataSource.source = source(element, "annotation", 1.0);
            upsertDataSource(model, dataSource);
        }
    }

    private void reconcile(ArchitectureModel model, AppId appId) {
        List<PersistenceUnitInfo> units = model.persistenceUnits.stream()
                .filter(unit -> appId.equals(unit.appId))
                .toList();
        for (PersistenceUnitInfo unit : units) {
            for (String reference :
                    List.of(Objects.toString(unit.jtaDataSource, ""), Objects.toString(unit.nonJtaDataSource, ""))) {
                if (blank(reference)) continue;
                ensureDataSource(model, appId, reference, unit.source);
            }
        }
        for (DataSourceUsage usage : model.dataSourceUsages.stream()
                .filter(item -> appId.equals(item.appId))
                .toList()) {
            ensureDataSource(model, appId, usage.dataSourceName, usage.source);
        }
        for (PersistenceUnitUsage usage : model.persistenceUnitUsages.stream()
                .filter(item -> appId.equals(item.appId))
                .toList()) {
            if (blank(usage.unitName) && units.size() == 1) usage.unitName = units.getFirst().name;
        }
        for (DataSourceInfo dataSource : model.dataSources.stream()
                .filter(item -> appId.equals(item.appId))
                .toList()) {
            if (blank(dataSource.endpoint)) continue;
            String id = externalSystemId(appId, dataSource.endpoint);
            if (model.externalSystems.stream().anyMatch(system -> id.equals(system.id))) continue;
            ExternalSystem database = new ExternalSystem();
            database.id = id;
            database.name = dataSource.endpoint;
            database.kind = "DATABASE";
            database.technology = dataSource.databaseKind;
            database.source = dataSource.source;
            model.externalSystems.add(database);
        }
    }

    private static void ensureDataSource(
            ArchitectureModel model, AppId appId, String reference, SourceInfo referenceSource) {
        if (findDataSource(model, appId, reference) != null) return;
        DataSourceInfo unresolved = new DataSourceInfo();
        unresolved.id = dataSourceId(appId, reference);
        unresolved.name = reference;
        unresolved.appId = appId;
        unresolved.jndiName = reference;
        unresolved.declarationKind = "unresolved-reference";
        unresolved.unresolved = true;
        unresolved.source = referenceSource;
        upsertDataSource(model, unresolved);
    }

    private static DataSourceInfo findDataSource(ArchitectureModel model, AppId appId, String reference) {
        String normalized = normalizeJndi(reference);
        return model.dataSources.stream()
                .filter(dataSource -> appId.equals(dataSource.appId))
                .filter(dataSource -> normalized.equals(normalizeJndi(dataSource.jndiName))
                        || normalized.equals(normalizeJndi(dataSource.name))
                        || dataSource.aliases.stream()
                                .map(PersistenceTopologyExtractor::normalizeJndi)
                                .anyMatch(normalized::equals))
                .findFirst()
                .orElse(null);
    }

    private static void upsertPersistenceUnit(ArchitectureModel model, PersistenceUnitInfo candidate) {
        model.persistenceUnits.removeIf(existing -> candidate.id.equals(existing.id));
        model.persistenceUnits.add(candidate);
    }

    private static void upsertDataSource(ArchitectureModel model, DataSourceInfo candidate) {
        DataSourceInfo existing =
                findDataSource(model, candidate.appId, firstNonBlank(candidate.jndiName, candidate.name));
        if (existing == null) {
            model.dataSources.add(candidate);
            return;
        }
        existing.id = candidate.id;
        existing.name = firstNonBlank(candidate.name, existing.name);
        existing.jndiName = firstNonBlank(candidate.jndiName, existing.jndiName);
        existing.driver = firstNonBlank(candidate.driver, existing.driver);
        existing.endpoint = firstNonBlank(candidate.endpoint, existing.endpoint);
        existing.databaseKind = firstNonBlank(candidate.databaseKind, existing.databaseKind);
        existing.declarationKind = firstNonBlank(candidate.declarationKind, existing.declarationKind);
        existing.unresolved = candidate.unresolved;
        existing.source = candidate.source != null ? candidate.source : existing.source;
        candidate.aliases.stream()
                .filter(alias -> !existing.aliases.contains(alias))
                .forEach(existing.aliases::add);
    }

    private static void addPersistenceUsage(
            ArchitectureModel model, AppId appId, ComponentId componentId, String unitName, SourceInfo source) {
        boolean duplicate = model.persistenceUnitUsages.stream()
                .anyMatch(usage -> Objects.equals(componentId, usage.componentId)
                        && appId.equals(usage.appId)
                        && Objects.equals(Objects.toString(unitName, ""), Objects.toString(usage.unitName, "")));
        if (duplicate) return;
        PersistenceUnitUsage usage = new PersistenceUnitUsage();
        usage.appId = appId;
        usage.componentId = componentId;
        usage.unitName = Objects.toString(unitName, "");
        usage.source = source;
        model.persistenceUnitUsages.add(usage);
    }

    private static void addDataSourceUsage(
            ArchitectureModel model, AppId appId, ComponentId componentId, String name, SourceInfo source) {
        if (blank(name)) return;
        boolean duplicate = model.dataSourceUsages.stream()
                .anyMatch(usage -> Objects.equals(componentId, usage.componentId)
                        && appId.equals(usage.appId)
                        && normalizeJndi(name).equals(normalizeJndi(usage.dataSourceName)));
        if (duplicate) return;
        DataSourceUsage usage = new DataSourceUsage();
        usage.appId = appId;
        usage.componentId = componentId;
        usage.dataSourceName = name;
        usage.source = source;
        model.dataSourceUsages.add(usage);
    }

    private static List<Path> descriptorFiles(BuildModule module) {
        Set<Path> files = new LinkedHashSet<>();
        for (File resourceRoot : module.resourceRoots()) {
            collectFiles(resourceRoot.toPath(), 8, files);
        }
        collectFiles(module.root().toPath(), 8, files);
        return files.stream()
                .filter(PersistenceTopologyExtractor::isRelevantDescriptor)
                .sorted()
                .toList();
    }

    private static void collectFiles(Path root, int maxDepth, Set<Path> files) {
        if (!Files.isDirectory(root)) return;
        try (var paths = Files.find(root, maxDepth, (path, attrs) -> attrs.isRegularFile())) {
            paths.filter(path -> !isGeneratedPath(path)).forEach(files::add);
        } catch (IOException _) {
        }
    }

    private static boolean isGeneratedPath(Path path) {
        for (Path part : path) {
            String value = part.toString();
            if ("target".equals(value) || "build".equals(value) || "bin".equals(value) || ".git".equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRelevantDescriptor(Path path) {
        return isPersistenceXml(path) || isResourceReferenceDescriptor(path) || isWildFlyDescriptor(path);
    }

    private static boolean isPersistenceXml(Path path) {
        return "persistence.xml".equalsIgnoreCase(fileName(path));
    }

    private static boolean isResourceReferenceDescriptor(Path path) {
        String name = fileName(path).toLowerCase(Locale.ROOT);
        return "web.xml".equals(name) || "ejb-jar.xml".equals(name) || "application.xml".equals(name);
    }

    private static boolean isWildFlyDescriptor(Path path) {
        String name = fileName(path).toLowerCase(Locale.ROOT);
        return "standalone.xml".equals(name) || "domain.xml".equals(name) || name.endsWith("-ds.xml");
    }

    private static List<Path> springConfigFiles(BuildModule module) {
        Set<Path> result = new LinkedHashSet<>();
        for (File root : module.resourceRoots()) {
            for (String name : List.of("application.properties", "application.yml", "application.yaml")) {
                Path candidate = root.toPath().resolve(name);
                if (Files.isRegularFile(candidate)) result.add(candidate);
            }
        }
        return result.stream().sorted().toList();
    }

    private static Map<String, String> readSpringValues(Path path) {
        if (!fileName(path).endsWith(".properties")) {
            return readSimpleYaml(path);
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException _) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("spring.datasource.") && !isSecretKey(key)) {
                result.put(key, properties.getProperty(key));
            }
        }
        return result;
    }

    private static Map<String, String> readSimpleYaml(Path path) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            List<String> prefix = new ArrayList<>();
            for (String rawLine : Files.readAllLines(path)) {
                String line = rawLine.stripTrailing();
                if (line.isBlank() || line.stripLeading().startsWith("#") || !line.contains(":")) continue;
                int indent = line.length() - line.stripLeading().length();
                int depth = indent / 2;
                while (prefix.size() > depth) prefix.removeLast();
                String stripped = line.stripLeading();
                int colon = stripped.indexOf(':');
                String key = stripped.substring(0, colon).strip();
                String value = stripped.substring(colon + 1).strip();
                if (value.isEmpty()) {
                    if (prefix.size() == depth) prefix.add(key);
                    continue;
                }
                String fullKey = String.join(".", prefix) + (prefix.isEmpty() ? "" : ".") + key;
                if (fullKey.startsWith("spring.datasource.") && !isSecretKey(fullKey)) {
                    result.put(fullKey, unquote(value));
                }
            }
        } catch (IOException _) {
        }
        return result;
    }

    private static boolean isSecretKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("password") || lower.contains("username") || lower.contains("credential");
    }

    private static Document parseXml(Path path) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            try (InputStream input = Files.newInputStream(path)) {
                return factory.newDocumentBuilder().parse(input);
            }
        } catch (IOException | SAXException | ParserConfigurationException | IllegalArgumentException _) {
            return null;
        }
    }

    private static String fileName(Path path) {
        Path fileName = path != null ? path.getFileName() : null;
        return fileName != null ? fileName.toString() : "";
    }

    private static List<Element> descendants(Element root, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList all = root.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < all.getLength(); i++) {
            if (all.item(i) instanceof Element element) result.add(element);
        }
        if (result.isEmpty()) {
            NodeList plain = root.getElementsByTagName(localName);
            for (int i = 0; i < plain.getLength(); i++) {
                if (plain.item(i) instanceof Element element) result.add(element);
            }
        }
        return result;
    }

    private static List<String> childTexts(Element parent, String localName) {
        List<String> result = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && localName.equals(localName(element))) {
                String value = element.getTextContent().strip();
                if (!value.isBlank()) result.add(value);
            }
        }
        return result;
    }

    private static String childText(Element parent, String localName) {
        return childTexts(parent, localName).stream().findFirst().orElse(null);
    }

    private static String xaUrl(Element element) {
        for (Element property : descendants(element, "xa-datasource-property")) {
            if ("url".equalsIgnoreCase(attribute(property, "name"))) {
                return property.getTextContent().strip();
            }
        }
        return null;
    }

    private static String attribute(Element element, String name) {
        String value = element.getAttribute(name);
        return blank(value) ? null : value.strip();
    }

    private static String localName(Element element) {
        if (element.getLocalName() != null) return element.getLocalName();
        String name = element.getTagName();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private static String annotationString(CtAnnotation<?> annotation, String key) {
        try {
            CtExpression<?> expression = annotation.getValue(key);
            if (expression == null) return null;
            if (expression instanceof CtLiteral<?> literal) {
                return Objects.toString(literal.getValue(), null);
            }
            return unquote(expression.toString());
        } catch (Exception _) {
            return null;
        }
    }

    private static boolean isEntityManager(CtField<?> field) {
        String qualified = field.getType() != null ? field.getType().getQualifiedName() : "";
        return qualified.endsWith(".EntityManager") || "EntityManager".equals(qualified);
    }

    private static boolean isDataSourceElement(CtElement element) {
        if (element instanceof CtField<?> field && field.getType() != null) {
            return field.getType().getQualifiedName().endsWith(".DataSource");
        }
        if (element instanceof CtParameter<?> parameter && parameter.getType() != null) {
            return parameter.getType().getQualifiedName().endsWith(".DataSource");
        }
        return false;
    }

    private static boolean looksLikeDataSourceName(String value) {
        if (blank(value)) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("jdbc") || lower.contains("datasource");
    }

    private static SourceInfo source(CtElement element, String derivedFrom, double confidence) {
        if (element.getPosition().isValidPosition()) {
            return new SourceInfo(
                    element.getPosition().getFile().getAbsolutePath(),
                    element.getPosition().getLine(),
                    derivedFrom,
                    confidence);
        }
        return new SourceInfo("unknown", 0, derivedFrom, confidence);
    }

    private static SourceInfo source(Path path, String derivedFrom, double confidence) {
        return new SourceInfo(path.toAbsolutePath().normalize().toString(), 0, derivedFrom, confidence);
    }

    private static void collectPlaceholders(PersistenceUnitInfo unit, String... values) {
        for (String value : values) {
            if (containsPlaceholder(value) && !unit.unresolvedPlaceholders.contains(value)) {
                unit.unresolvedPlaceholders.add(value);
            }
        }
    }

    static String sanitizeEndpoint(String rawUrl) {
        if (blank(rawUrl) || containsPlaceholder(rawUrl)) return null;
        String value = rawUrl.strip();
        if (value.toLowerCase(Locale.ROOT).contains("credential-store")) return null;
        if (value.startsWith("jdbc:")) value = value.substring("jdbc:".length());
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int semicolon = value.indexOf(';');
        if (semicolon >= 0) value = value.substring(0, semicolon);
        try {
            URI uri = URI.create(value);
            if (uri.getHost() != null) {
                StringBuilder safe = new StringBuilder();
                if (uri.getScheme() != null) safe.append(uri.getScheme()).append("://");
                safe.append(uri.getHost());
                if (uri.getPort() >= 0) safe.append(':').append(uri.getPort());
                if (uri.getPath() != null) safe.append(uri.getPath());
                return safe.toString();
            }
        } catch (IllegalArgumentException _) {
        }
        int userInfo = value.indexOf('@');
        if (userInfo >= 0) {
            int scheme = value.indexOf("//");
            value = scheme >= 0
                    ? value.substring(0, scheme + 2) + value.substring(userInfo + 1)
                    : value.substring(userInfo + 1);
        }
        return value;
    }

    private static String databaseKind(String rawUrl, String driver) {
        String value = (Objects.toString(rawUrl, "") + " " + Objects.toString(driver, "")).toLowerCase(Locale.ROOT);
        for (String kind : List.of("postgresql", "mariadb", "mysql", "oracle", "sqlserver", "db2", "h2")) {
            if (value.contains(kind)) return kind;
        }
        return null;
    }

    static String normalizeJndi(String value) {
        if (blank(value)) return "";
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        for (String prefix : List.of("java:comp/env/", "java:/", "java:jboss/")) {
            if (normalized.startsWith(prefix)) normalized = normalized.substring(prefix.length());
        }
        return normalized;
    }

    static String persistenceUnitId(AppId appId, String name) {
        return "persistence-unit:" + appId.serialize() + ":" + name;
    }

    static String dataSourceId(AppId appId, String name) {
        return "datasource:" + appId.serialize() + ":" + normalizeJndi(name);
    }

    static String externalSystemId(AppId appId, String endpoint) {
        return "external:database:" + appId.serialize() + ":" + endpoint;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!blank(value)) return value;
        }
        return null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean containsPlaceholder(String value) {
        return value != null && (value.contains("${") || value.contains("#{"));
    }

    private static String unquote(String value) {
        if (value == null || value.length() < 2) return value;
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
