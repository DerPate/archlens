package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.build.BuildMetadataService;
import dev.dominikbreu.archlens.build.BuildModule;
import dev.dominikbreu.archlens.build.BuildProject;
import dev.dominikbreu.archlens.extractor.objectflow.ObjectFlowIndex;
import dev.dominikbreu.archlens.extractor.objectflow.ObjectFlowIndexBuilder;
import dev.dominikbreu.archlens.extractor.sourcefacts.SourceFactIndex;
import dev.dominikbreu.archlens.extractor.sourcefacts.SourceFactIndexBuilder;
import dev.dominikbreu.archlens.model.*;
import dev.dominikbreu.archlens.model.ids.AppId;
import dev.dominikbreu.archlens.scanner.SpoonScanner;
import dev.dominikbreu.archlens.tracing.Spans;
import io.opentelemetry.api.trace.Span;
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

    private static final String SPRING = "spring";
    private static final String SPRING_BOOT = "spring-boot";
    private static final String JAVAEE = "javaee";
    private static final String QUARKUS = "quarkus";
    private static final String JAVA = "java";

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
    private final SourceFactIndexBuilder sourceFactIndexBuilder = new SourceFactIndexBuilder();
    private final PersistenceTopologyExtractor persistenceTopologyExtractor = new PersistenceTopologyExtractor();
    private final ConfigPropertyResolver configPropertyResolver = new ConfigPropertyResolver();
    private final TransactionPolicyExtractor transactionPolicyExtractor = new TransactionPolicyExtractor();

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
        Spans.traced("extract", () -> {
            Span.current().setAttribute("workspace-path", model.workspacePath);
            List<ModuleWork> modules = collectAllModules(projectPaths, model);
            Spans.traced("pass1-scan", () -> pass1Scan(modules, model));
            Spans.traced("pass2-enrichment", () -> pass2Enrichment(modules, model));
            ModelIndex modelIndex = ModelIndex.build(model);
            Spans.traced("pass2c-dataflow", () -> pass2cDataflow(model, modelIndex));
            Spans.traced("pass3-4-runtime", () -> pass34Runtime(model, modelIndex));
        });
        return model;
    }

    /** Phase 1: lightweight scan — components + entrypoints only, one CtModel at a time. */
    private void pass1Scan(List<ModuleWork> modules, ArchitectureModel model) {
        for (ModuleWork work : modules) {
            CtModel ctModel = buildCtModel(work.module(), "pass1-scan");
            work.ctModel = ctModel;
            Collection<CtType<?>> types = ctModel.getAllTypes();
            String tech = detectTechnology(types, work.module());
            work.app().technology = tech;

            if ("internal_module".equals(work.app().role)) {
                work.app().role = moduleClassifier.classify(types, work.app());
            }

            int sitesBefore = model.outboundSinkSites.size();
            dispatchExtractors(types, model, work.app().id, work.module(), tech);
            persistenceTopologyExtractor.extract(work.module(), types, model, work.app().id);
            configPropertyResolver.resolve(work.module().root(), work.app().id, model);

            Map<String, MessagingConfigResolver.ChannelConfig> resolved =
                    messagingConfigResolver.resolve(work.module().root());
            applyMessagingBrokers(model, work.app().id, resolved);
            new MessagingTopicResolver(15).resolve(model, ctModel, sitesBefore);
        }
        eventBusExtractor.linkCrossModuleEvents(model);
        Span.current().setAttribute("modules", modules.size());
    }

    /** Phase 2: enrich the component registry using the pass-1 Spoon models. */
    private void pass2Enrichment(List<ModuleWork> modules, ArchitectureModel model) {
        for (ModuleWork work : modules) {
            CtModel ctModel = work.ctModel;
            if (ctModel == null) {
                ctModel = buildCtModel(work.module(), "pass2-enrichment");
            }
            extractDependencies(ctModel, model, work.module());
            SourceFactIndex sourceFacts = sourceFactIndexBuilder.build(
                    ctModel, work.module().name(), work.module().sourceRoots().size());
            Span.current().setAttribute("sourceFactTypes." + work.module().name(), sourceFacts.typeCount());
            transactionPolicyExtractor.extract(sourceFacts, model, work.app().id, work.module());
            ObjectFlowIndex objectFlowIndex = new ObjectFlowIndexBuilder().build(ctModel, model, sourceFacts);
            new CallGraphExtractor(objectFlowIndex, sourceFacts).extract(ctModel, model);
            new TransactionPolicyPostProcessor().apply(model);
            work.ctModel = null;
        }
        Span.current().setAttribute("modules", modules.size());
    }

    /** Pass 2c: data-flow tracing — parameter propagation to sinks. */
    private void pass2cDataflow(ArchitectureModel model, ModelIndex modelIndex) {
        List<DataFlowPath> paths = dataFlowTracer.trace(model, modelIndex);
        model.dataFlowPaths.addAll(paths);
        Span.current().setAttribute("paths-found", paths.size());
    }

    /** Pass 3-4: container inference + runtime flows. */
    private void pass34Runtime(ArchitectureModel model, ModelIndex modelIndex) {
        model.containers.addAll(containerInferrer.infer(model.components));
        externalSystemInferrer.infer(model);
        for (Entrypoint entrypoint : model.entrypoints) {
            RuntimeFlow flow = runtimeFlowInferrer.infer(entrypoint.id.serialize(), 5, model, modelIndex);
            if (flow != null) {
                model.runtimeFlows.add(flow);
            }
        }
        new TransactionScopeInferrer().infer(model);
    }

    private CtModel buildCtModel(BuildModule module, String phase) {
        return Spans.traced("ctmodel.build", () -> {
            Span.current().setAttribute("phase", phase);
            Span.current().setAttribute("module", module.name());
            Span.current().setAttribute("source-roots", module.sourceRoots().size());
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setComplianceLevel(21);
            launcher.getEnvironment().setShouldCompile(false);
            scanner.addSourceRoots(launcher, module);
            launcher.buildModel();
            return launcher.getModel();
        });
    }

    private void extractDependencies(CtModel ctModel, ArchitectureModel model, BuildModule module) {
        Spans.traced("dependency.extract", () -> {
            Span.current().setAttribute("module", module.name());
            dependencyExtractor.extract(ctModel, model);
        });
    }

    private static final class ModuleWork {
        private final AppEntry app;
        private final BuildModule module;
        private CtModel ctModel;

        private ModuleWork(AppEntry app, BuildModule module) {
            this.app = app;
            this.module = module;
        }

        private AppEntry app() {
            return app;
        }

        private BuildModule module() {
            return module;
        }
    }

    private List<ModuleWork> collectAllModules(List<String> projectPaths, ArchitectureModel model) {
        List<ModuleWork> result = new ArrayList<>();
        for (String path : projectPaths) {
            BuildProject project = buildMetadataService.detect(new File(path));
            collectProjectModules(project, model, result);
        }
        return result;
    }

    private void collectProjectModules(BuildProject project, ArchitectureModel model, List<ModuleWork> result) {
        List<BuildModule> modules = project.modules();
        registerWarParent(project, modules, model);
        for (BuildModule module : modules) {
            registerModule(module, model, result);
        }
    }

    /**
     * Pre-registers a WAR parent when all modules share the same parent name and the project root
     * has WAR packaging — mirrors the old Maven parent-pom handling.
     */
    private void registerWarParent(BuildProject project, List<BuildModule> modules, ArchitectureModel model) {
        if (modules.isEmpty()) return;
        String sharedParent = modules.getFirst().parentName();
        if (sharedParent == null || !modules.stream().allMatch(m -> sharedParent.equals(m.parentName()))) return;
        String rootPackaging = detectMavenPackagingType(project.root().getAbsolutePath());
        if (!"war".equals(rootPackaging)) return;
        AppId parentId = AppId.of(sharedParent);
        if (model.applications.stream().anyMatch(a -> a.id.equals(parentId))) return;
        AppEntry parent = new AppEntry();
        parent.id = parentId;
        parent.name = sharedParent;
        parent.rootPath = project.root().getAbsolutePath();
        parent.packagingType = rootPackaging;
        parent.role = "deployment_unit";
        model.applications.add(parent);
    }

    private void registerModule(BuildModule module, ArchitectureModel model, List<ModuleWork> result) {
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
        result.add(new ModuleWork(app, module));
    }

    private AppEntry buildAppEntry(BuildModule module) {
        AppEntry app = new AppEntry();
        app.id = AppId.of(module.name());
        app.name = module.name();
        app.rootPath = module.root().getAbsolutePath();
        app.packagingType = module.packagingType();
        return app;
    }

    private void dispatchExtractors(
            Collection<CtType<?>> types, ArchitectureModel model, AppId appId, BuildModule module, String tech) {
        switch (tech) {
            case SPRING_BOOT, SPRING ->
                new SpringExtractor(new SpringConfigResolver().resolve(module.root())).extract(types, model, appId);
            case JAVAEE -> javaEEExtractor.extract(types, model, appId);
            case QUARKUS -> quarkusExtractor.extract(types, model, appId);
            default -> genericJavaExtractor.extract(types, model, appId);
        }
        eventBusExtractor.extract(types, model, appId);
    }

    private void applyMessagingBrokers(
            ArchitectureModel model, AppId appId, Map<String, MessagingConfigResolver.ChannelConfig> resolved) {
        applyResolvedBrokers(model, appId, resolved);
        Set<String> inMemory = detectInMemoryChannels(model, appId, resolved);
        if (inMemory.isEmpty()) return;
        applyInMemoryBroker(model, appId, inMemory);
    }

    /** Pass A: apply config-resolved broker + topic to entrypoints and interfaces. */
    private void applyResolvedBrokers(
            ArchitectureModel model, AppId appId, Map<String, MessagingConfigResolver.ChannelConfig> resolved) {
        for (Entrypoint ep : model.entrypoints) {
            if (!appId.equals(componentModule(model, ep.componentId))) continue;
            applyResolvedToEntrypoint(ep, resolved);
        }
        for (InterfaceEntry iface : model.interfaces) {
            if (!appId.equals(iface.module)) continue;
            applyResolvedToInterface(iface, resolved);
        }
    }

    private void applyResolvedToEntrypoint(Entrypoint ep, Map<String, MessagingConfigResolver.ChannelConfig> resolved) {
        if (ep.channelName == null) return;
        MessagingConfigResolver.ChannelConfig cfg = resolved.get(ep.channelName);
        if (cfg == null) return;
        ep.broker = cfg.broker;
        if (cfg.topic != null) ep.topic = cfg.topic;
    }

    private void applyResolvedToInterface(
            InterfaceEntry iface, Map<String, MessagingConfigResolver.ChannelConfig> resolved) {
        if (iface.path == null) return;
        MessagingConfigResolver.ChannelConfig cfg = resolved.get(iface.path);
        if (cfg == null) return;
        iface.broker = cfg.broker;
        if (cfg.topic != null) iface.topic = cfg.topic;
    }

    /**
     * Pass B: a channel referenced by both an {@code @Incoming} consumer and an {@code @Outgoing}
     * producer in this app, with no resolved connector, is an in-memory channel.
     */
    private Set<String> detectInMemoryChannels(
            ArchitectureModel model, AppId appId, Map<String, MessagingConfigResolver.ChannelConfig> resolved) {
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
        inMemory.removeIf(ch -> {
            MessagingConfigResolver.ChannelConfig cfg = resolved.get(ch);
            return cfg != null && cfg.broker != MessagingBroker.UNKNOWN;
        });
        return inMemory;
    }

    private void applyInMemoryBroker(ArchitectureModel model, AppId appId, Set<String> inMemory) {
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

    private AppId componentModule(ArchitectureModel model, dev.dominikbreu.archlens.model.ids.ComponentId componentId) {
        if (componentId == null) return null;
        for (Component c : model.components) if (componentId.equals(c.id)) return c.module;
        return null;
    }

    private String detectTechnology(Collection<CtType<?>> types, BuildModule module) {
        String pluginText = String.join(" ", module.plugins()).toLowerCase();
        if (pluginText.contains("org.springframework.boot")) return SPRING_BOOT;

        File pom = new File(module.root(), "pom.xml");
        boolean hasMaven = pom.exists();
        if (hasMaven) {
            try {
                String content = Files.readString(pom.toPath()).toLowerCase();
                if (content.contains(SPRING_BOOT)) return SPRING_BOOT;
                if (content.contains("springframework")) return SPRING;
                if (content.contains(QUARKUS)) return QUARKUS;
                if (content.contains("wildfly")
                        || content.contains("jboss")
                        || content.contains(JAVAEE)
                        || content.contains("java-ee")) return JAVAEE;
            } catch (Exception _) {
            }
        }

        String fromAnnotations = detectTechnologyFromAnnotations(types);
        if (fromAnnotations != null && !"unknown".equals(fromAnnotations)) return fromAnnotations;
        return hasMaven || new File(module.root(), "build.gradle").exists() ? JAVA : "unknown";
    }

    private String detectTechnologyFromAnnotations(Collection<CtType<?>> types) {
        for (CtType<?> type : types) {
            String tech = technologyFromAnnotations(type.getAnnotations());
            if (tech != null) return tech;
            for (var method : type.getMethods()) {
                tech = technologyFromAnnotations(method.getAnnotations());
                if (tech != null) return tech;
            }
            for (var field : type.getFields()) {
                tech = technologyFromAnnotations(field.getAnnotations());
                if (tech != null) return tech;
            }
        }
        return null;
    }

    private String technologyFromAnnotations(
            Iterable<? extends spoon.reflect.declaration.CtAnnotation<?>> annotations) {
        for (var annotation : annotations) {
            String technology =
                    technologyFromAnnotationName(annotation.getAnnotationType().getQualifiedName());
            if (technology != null) return technology;
        }
        return null;
    }

    private String technologyFromAnnotationName(String qualifiedName) {
        if (qualifiedName.startsWith("org.springframework.boot")) return SPRING_BOOT;
        if (qualifiedName.startsWith("org.springframework")) return SPRING;
        if (qualifiedName.startsWith("io.quarkus")) return QUARKUS;
        if (qualifiedName.startsWith("javax.ejb") || qualifiedName.startsWith("jakarta.ejb")) return JAVAEE;
        return null;
    }

    private String detectMavenPackagingType(String path) {
        File root = new File(path);
        if (!new File(root, "pom.xml").exists()) return "unknown";
        return scanner.readPackagingType(root);
    }
}
