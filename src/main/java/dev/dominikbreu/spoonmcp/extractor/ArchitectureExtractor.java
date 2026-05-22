package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.build.BuildMetadataService;
import dev.dominikbreu.spoonmcp.build.BuildModule;
import dev.dominikbreu.spoonmcp.build.BuildProject;
import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.scanner.SpoonScanner;
import dev.dominikbreu.spoonmcp.extractor.objectflow.ObjectFlowIndex;
import dev.dominikbreu.spoonmcp.extractor.objectflow.ObjectFlowIndexBuilder;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
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
    private final DataFlowTracer dataFlowTracer = new DataFlowTracer();
    private final BuildMetadataService buildMetadataService = new BuildMetadataService();

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
        Span extractSpan = tracer().spanBuilder("extract")
                .setAttribute("workspace-path", model.workspacePath)
                .startSpan();
        try (Scope extractScope = extractSpan.makeCurrent()) {

            // Pass 1: components + entrypoints per project/module, with WAR role assignment
            Map<String, CtModel> ctModels = new LinkedHashMap<>();
            Span pass1 = tracer().spanBuilder("pass1-scan").startSpan();
            try (Scope s = pass1.makeCurrent()) {
                for (String path : projectPaths) {
                    BuildProject project = buildMetadataService.detect(new File(path));
                    registerBuildProject(project, model, ctModels);
                }
                pass1.setAttribute("modules", (long) ctModels.size());
            } finally {
                pass1.end();
            }

            // Pass 2: injection dependencies (needs all components to be known)
            Span pass2 = tracer().spanBuilder("pass2-deps").startSpan();
            try (Scope s = pass2.makeCurrent()) {
                for (CtModel ctModel : ctModels.values()) {
                    dependencyExtractor.extract(ctModel, model);
                }
                eventBusExtractor.linkCrossModuleEvents(model);
            } finally {
                pass2.end();
            }

            // Pass 2b: call graph — actual method invocations between components
            Span pass2b = tracer().spanBuilder("pass2b-callgraph").startSpan();
            try (Scope s = pass2b.makeCurrent()) {
                pass2b.setAttribute("modules", (long) ctModels.size());
                for (CtModel ctModel : ctModels.values()) {
                    ObjectFlowIndex objectFlowIndex = new ObjectFlowIndexBuilder().build(ctModel, model);
                    new CallGraphExtractor(objectFlowIndex).extract(ctModel, model);
                }
            } finally {
                pass2b.end();
            }

            // Pass 2c: data-flow tracing — parameter propagation to sinks
            Span pass2c = tracer().spanBuilder("pass2c-dataflow").startSpan();
            try (Scope s = pass2c.makeCurrent()) {
                List<DataFlowPath> paths = dataFlowTracer.trace(model);
                model.dataFlowPaths.addAll(paths);
                pass2c.setAttribute("paths-found", (long) paths.size());
            } finally {
                pass2c.end();
            }

            // Pass 3: container inference
            // Pass 4: messaging broker resolution + external system inference
            Span pass34 = tracer().spanBuilder("pass3-4-runtime").startSpan();
            try (Scope s = pass34.makeCurrent()) {
                model.containers.addAll(containerInferrer.infer(model.components));
                externalSystemInferrer.infer(model);
                for (Entrypoint entrypoint : model.entrypoints) {
                    RuntimeFlow flow = runtimeFlowInferrer.infer(entrypoint.id, 5, model);
                    if (flow != null) {
                        model.runtimeFlows.add(flow);
                    }
                }
            } finally {
                pass34.end();
            }

        } catch (RuntimeException e) {
            extractSpan.recordException(e);
            extractSpan.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            extractSpan.end();
        }
        return model;
    }

    private static Tracer tracer() {
        return GlobalOpenTelemetry.getTracer("dev.dominikbreu.spoonmcp");
    }

    private void registerBuildProject(BuildProject project, ArchitectureModel model, Map<String, CtModel> ctModels) {
        List<BuildModule> modules = project.modules();

        // Pre-register WAR parent when all modules share the same parent name and the
        // project root has WAR packaging — this mirrors the old Maven parent-pom handling.
        if (!modules.isEmpty()) {
            String sharedParent = modules.get(0).parentName();
            if (sharedParent != null
                    && modules.stream().allMatch(m -> sharedParent.equals(m.parentName()))) {
                String rootPackaging = detectMavenPackagingType(project.root().getAbsolutePath());
                if ("war".equals(rootPackaging)) {
                    String parentId = "app:" + sharedParent;
                    if (model.applications.stream().noneMatch(a -> a.id.equals(parentId))) {
                        AppEntry parent = new AppEntry();
                        parent.id = parentId;
                        parent.name = sharedParent;
                        parent.rootPath = project.root().getAbsolutePath();
                        parent.packagingType = rootPackaging;
                        parent.role = "deployment_unit";
                        model.applications.add(parent);
                    }
                }
            }
        }

        for (BuildModule module : modules) {
            registerBuildModule(module, model, ctModels);
        }
    }

    private void registerBuildModule(BuildModule module, ArchitectureModel model, Map<String, CtModel> ctModels) {
        AppEntry app = buildAppEntry(module);
        if (model.applications.stream().anyMatch(a -> a.id.equals(app.id))) return;
        app.role = "deployment_unit";

        // Assign internal_module role when parent is a WAR deployment unit
        if (module.parentName() != null) {
            model.applications.stream()
                    .filter(a -> a.name.equals(module.parentName()) && "war".equals(a.packagingType))
                    .findFirst()
                    .ifPresent(parent -> {
                        app.role = "internal_module";
                        app.parentAppId = parent.id;
                    });
        }

        model.applications.add(app);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(21);
        launcher.getEnvironment().setShouldCompile(false);
        scanner.addSourceRoots(launcher, module);
        launcher.buildModel();
        CtModel ctModel = launcher.getModel();
        ctModels.put(app.id, ctModel);

        Collection<CtType<?>> types = ctModel.getAllTypes();
        String tech = detectTechnology(types, module);
        app.technology = tech;

        // Apply heuristics to refine role: internal_module vs technical_library
        if ("internal_module".equals(app.role)) {
            app.role = moduleClassifier.classify(types, app);
        }

        dispatchExtractors(types, model, app.id, module, tech);

        Map<String, MessagingConfigResolver.ChannelConfig> resolved = messagingConfigResolver.resolve(module.root());
        applyMessagingBrokers(model, app.id, resolved);
    }

    private AppEntry buildAppEntry(BuildModule module) {
        AppEntry app = new AppEntry();
        app.id = "app:" + module.name();
        app.name = module.name();
        app.rootPath = module.root().getAbsolutePath();
        app.packagingType = module.packagingType();
        return app;
    }

    private void dispatchExtractors(
            Collection<CtType<?>> types, ArchitectureModel model, String appId, BuildModule module, String tech) {
        switch (tech) {
            case "spring-boot", "spring" ->
                    new SpringExtractor(new SpringConfigResolver().resolve(module.root())).extract(types, model, appId);
            case "javaee" -> javaEEExtractor.extract(types, model, appId);
            case "quarkus" -> quarkusExtractor.extract(types, model, appId);
            default -> {
                quarkusExtractor.extract(types, model, appId);
                javaEEExtractor.extract(types, model, appId);
                new SpringExtractor(new SpringConfigResolver().resolve(module.root())).extract(types, model, appId);
                genericJavaExtractor.extract(types, model, appId);
            }
        }
        eventBusExtractor.extract(types, model, appId);
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

    private String detectTechnology(Collection<CtType<?>> types, BuildModule module) {
        String pluginText = String.join(" ", module.plugins()).toLowerCase();
        if (pluginText.contains("org.springframework.boot")) return "spring-boot";

        File pom = new File(module.root(), "pom.xml");
        if (pom.exists()) {
            try {
                String content = Files.readString(pom.toPath()).toLowerCase();
                if (content.contains("spring-boot")) return "spring-boot";
                if (content.contains("springframework")) return "spring";
                if (content.contains("quarkus")) return "quarkus";
                if (content.contains("wildfly")
                        || content.contains("jboss")
                        || content.contains("javaee")
                        || content.contains("java-ee")) return "javaee";
            } catch (Exception ignored) {
            }
        }

        if (hasAnnotationPrefix(types, "org.springframework.boot")) return "spring-boot";
        if (hasAnnotationPrefix(types, "org.springframework")) return "spring";
        if (hasAnnotationPrefix(types, "io.quarkus")) return "quarkus";
        if (hasAnnotationPrefix(types, "javax.ejb") || hasAnnotationPrefix(types, "jakarta.ejb")) return "javaee";
        return "unknown";
    }

    private boolean hasAnnotationPrefix(Collection<CtType<?>> types, String prefix) {
        return types.stream()
                .flatMap(type -> type.getAnnotations().stream())
                .anyMatch(annotation -> annotation.getAnnotationType().getQualifiedName().startsWith(prefix));
    }

    private String detectMavenPackagingType(String path) {
        File root = new File(path);
        if (!new File(root, "pom.xml").exists()) return "unknown";
        return scanner.readPackagingType(root);
    }
}
