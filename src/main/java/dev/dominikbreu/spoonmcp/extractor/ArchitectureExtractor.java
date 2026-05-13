package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.scanner.SpoonScanner;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;

/**
 * Coordinates source scanning, framework extraction, dependency extraction, and model enrichment.
 */
public class ArchitectureExtractor {

    private final SpoonScanner scanner = new SpoonScanner();
    private final QuarkusExtractor quarkusExtractor = new QuarkusExtractor();
    private final JavaEEExtractor javaEEExtractor = new JavaEEExtractor();
    private final GenericJavaExtractor genericJavaExtractor = new GenericJavaExtractor();
    private final DependencyExtractor dependencyExtractor = new DependencyExtractor();
    private final ContainerInferrer containerInferrer = new ContainerInferrer();
    private final InternalModuleClassifier moduleClassifier = new InternalModuleClassifier();
    private final EventBusExtractor eventBusExtractor = new EventBusExtractor();
    private final RuntimeFlowInferrer runtimeFlowInferrer = new RuntimeFlowInferrer();
    private final MessagingConfigResolver messagingConfigResolver = new MessagingConfigResolver();
    private final ExternalSystemInferrer externalSystemInferrer = new ExternalSystemInferrer();
    private final CallGraphExtractor callGraphExtractor = new CallGraphExtractor();
    private final DataFlowTracer dataFlowTracer = new DataFlowTracer();

    /** Creates an extractor with the default scanner and extraction passes. */
    public ArchitectureExtractor() {}

    /**
     * Extracts an architecture model from one or more project roots.
     *
     * @param projectPaths project or workspace roots to analyze
     * @return extracted architecture model
     */
    public ArchitectureModel extract(List<String> projectPaths) {
        ArchitectureModel model = new ArchitectureModel(String.join(",", projectPaths));

        // Pass 1: components + entrypoints per project/module, with WAR role assignment
        Map<String, CtModel> ctModels = new LinkedHashMap<>();
        for (String path : projectPaths) {
            resolveAndRegisterModules(new File(path), null, model, ctModels);
        }

        // Pass 2: injection dependencies (needs all components to be known)
        for (CtModel ctModel : ctModels.values()) {
            dependencyExtractor.extract(ctModel, model);
        }
        eventBusExtractor.linkCrossModuleEvents(model);

        // Pass 2b: call graph — actual method invocations between components
        for (CtModel ctModel : ctModels.values()) {
            callGraphExtractor.extract(ctModel, model);
        }

        // Pass 2c: data-flow tracing — parameter propagation to sinks
        model.dataFlowPaths.addAll(dataFlowTracer.trace(model));

        // Pass 3: container inference
        model.containers.addAll(containerInferrer.infer(model.components));

        // Pass 4: messaging broker resolution + external system inference
        externalSystemInferrer.infer(model);

        for (Entrypoint entrypoint : model.entrypoints) {
            RuntimeFlow flow = runtimeFlowInferrer.infer(entrypoint.id, 5, model);
            if (flow != null) {
                model.runtimeFlows.add(flow);
            }
        }

        return model;
    }

    private void applyMessagingBrokers(
            ArchitectureModel model, String appId, Map<String, MessagingConfigResolver.ChannelConfig> resolved) {
        // Pass A: apply config-resolved broker + topic.
        for (Entrypoint ep : model.entrypoints) {
            if (!appId.equals(componentModule(model, ep.componentId))) continue;
            if (ep.channelName == null) continue;
            MessagingConfigResolver.ChannelConfig cfg = resolved.get(ep.channelName);
            if (cfg == null) continue;
            ep.broker = cfg.broker;
            if (cfg.topic != null) ep.topic = cfg.topic;
        }
        for (InterfaceEntry iface : model.interfaces) {
            if (!appId.equals(iface.module)) continue;
            if (iface.path == null) continue;
            MessagingConfigResolver.ChannelConfig cfg = resolved.get(iface.path);
            if (cfg == null) continue;
            iface.broker = cfg.broker;
            if (cfg.topic != null) iface.topic = cfg.topic;
        }

        // Pass B: tag in-memory channels — channel referenced by both an @Incoming consumer
        // and an @Outgoing producer in this app, with no connector property.
        Set<String> producerChannels = new HashSet<>();
        Set<String> consumerChannels = new HashSet<>();
        for (Entrypoint ep : model.entrypoints) {
            if (!appId.equals(componentModule(model, ep.componentId))) continue;
            if (ep.channelName == null) continue;
            if (ep.type == EntrypointType.MESSAGING_PRODUCER) producerChannels.add(ep.channelName);
            if (ep.type == EntrypointType.MESSAGING_CONSUMER) consumerChannels.add(ep.channelName);
        }
        Set<String> inMemory = new HashSet<>(producerChannels);
        inMemory.retainAll(consumerChannels);
        // Only re-tag channels with no resolved connector.
        inMemory.removeIf(ch -> {
            MessagingConfigResolver.ChannelConfig cfg = resolved.get(ch);
            return cfg != null && cfg.broker != MessagingBroker.UNKNOWN;
        });
        if (inMemory.isEmpty()) return;

        for (Entrypoint ep : model.entrypoints) {
            if (!appId.equals(componentModule(model, ep.componentId))) continue;
            if (ep.channelName != null && inMemory.contains(ep.channelName)) {
                ep.broker = MessagingBroker.IN_MEMORY;
            }
        }
        for (InterfaceEntry iface : model.interfaces) {
            if (!appId.equals(iface.module)) continue;
            if (iface.path != null && inMemory.contains(iface.path)) {
                iface.broker = MessagingBroker.IN_MEMORY;
            }
        }
    }

    private String componentModule(ArchitectureModel model, String componentId) {
        if (componentId == null) return null;
        for (Component c : model.components) if (componentId.equals(c.id)) return c.module;
        return null;
    }

    /**
     * Recursively walks the Maven module tree, registers each leaf as an AppEntry,
     * and assigns roles based on packaging hierarchy.
     *
     * WAR parent  → role=deployment_unit
     * JAR/WAR child of a WAR parent → role=internal_module, parentAppId set
     * Everything else → role=deployment_unit (standalone)
     */
    private void resolveAndRegisterModules(
            File root, String parentWarAppId, ArchitectureModel model, Map<String, CtModel> ctModels) {
        List<String> submoduleNames = scanner.readMavenModules(root);
        String packagingType = detectPackagingType(root.getAbsolutePath());

        if (!submoduleNames.isEmpty()) {
            // This is a parent POM
            String thisAppId = null;
            if ("war".equals(packagingType)) {
                // A WAR parent = deployment_unit; children become internal_module
                AppEntry warApp = buildAppEntry(root.getAbsolutePath());
                warApp.role = "deployment_unit";
                if (!model.applications.stream().anyMatch(a -> a.id.equals(warApp.id))) {
                    model.applications.add(warApp);
                }
                thisAppId = warApp.id;
            }

            for (String sub : submoduleNames) {
                File subDir = new File(root, sub);
                resolveAndRegisterModules(subDir, thisAppId, model, ctModels);
            }
            return;
        }

        // Leaf module — register and scan
        AppEntry app = buildAppEntry(root.getAbsolutePath());
        if (model.applications.stream().anyMatch(a -> a.id.equals(app.id))) return;

        if (parentWarAppId != null) {
            app.role = "internal_module";
            app.parentAppId = parentWarAppId;
            // Link this module to the parent WAR entry
            model.applications.stream()
                    .filter(a -> a.id.equals(parentWarAppId))
                    .findFirst()
                    .ifPresent(war -> war.componentIds.addAll(app.componentIds));
        } else {
            app.role = "deployment_unit";
        }

        model.applications.add(app);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(21);
        launcher.getEnvironment().setShouldCompile(false);
        scanner.addSourceRoots(launcher, root);
        launcher.buildModel();
        CtModel ctModel = launcher.getModel();
        ctModels.put(app.id, ctModel);

        Collection<CtType<?>> types = ctModel.getAllTypes();
        String tech = detectTechnology(types, root.getAbsolutePath());
        app.technology = tech;

        // Apply heuristics to refine role: internal_module vs technical_library
        if ("internal_module".equals(app.role)) {
            app.role = moduleClassifier.classify(types, app);
        }

        switch (tech) {
            case "javaee" -> javaEEExtractor.extract(types, model, app.id);
            case "quarkus" -> quarkusExtractor.extract(types, model, app.id);
            default -> {
                quarkusExtractor.extract(types, model, app.id);
                javaEEExtractor.extract(types, model, app.id);
                genericJavaExtractor.extract(types, model, app.id);
            }
        }
        eventBusExtractor.extract(types, model, app.id);

        Map<String, MessagingConfigResolver.ChannelConfig> resolved = messagingConfigResolver.resolve(root);
        applyMessagingBrokers(model, app.id, resolved);
    }

    private AppEntry buildAppEntry(String path) {
        AppEntry app = new AppEntry();
        File dir = new File(path);
        app.id = "app:" + dir.getName();
        app.name = dir.getName();
        app.rootPath = path;
        app.packagingType = detectPackagingType(path);
        return app;
    }

    private String detectPackagingType(String path) {
        File root = new File(path);
        if (!new File(root, "pom.xml").exists()) return "unknown";
        return scanner.readPackagingType(root);
    }

    private String detectTechnology(Collection<CtType<?>> types, String path) {
        File pom = new File(path, "pom.xml");
        if (pom.exists()) {
            try {
                String content = Files.readString(pom.toPath()).toLowerCase();
                if (content.contains("quarkus")) return "quarkus";
                if (content.contains("wildfly")
                        || content.contains("jboss")
                        || content.contains("javaee")
                        || content.contains("java-ee")) return "javaee";
            } catch (Exception ignored) {
            }
        }

        long quarkusHints = types.stream()
                .flatMap(t -> t.getAnnotations().stream())
                .filter(a -> a.getAnnotationType().getQualifiedName().startsWith("io.quarkus"))
                .count();
        if (quarkusHints > 0) return "quarkus";

        long ejbHints = types.stream()
                .flatMap(t -> t.getAnnotations().stream())
                .filter(a -> {
                    String qn = a.getAnnotationType().getQualifiedName();
                    return qn.startsWith("javax.ejb") || qn.startsWith("jakarta.ejb");
                })
                .count();
        if (ejbHints > 0) return "javaee";

        return "unknown";
    }
}
