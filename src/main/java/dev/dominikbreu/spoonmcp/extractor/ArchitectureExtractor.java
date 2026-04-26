package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.scanner.SpoonScanner;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

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

        // Pass 3: container inference
        model.containers.addAll(containerInferrer.infer(model.components));
        for (Entrypoint entrypoint : model.entrypoints) {
            RuntimeFlow flow = runtimeFlowInferrer.infer(entrypoint.id, 5, model);
            if (flow != null) {
                model.runtimeFlows.add(flow);
            }
        }

        return model;
    }

    /**
     * Recursively walks the Maven module tree, registers each leaf as an AppEntry,
     * and assigns roles based on packaging hierarchy.
     *
     * WAR parent  → role=deployment_unit
     * JAR/WAR child of a WAR parent → role=internal_module, parentAppId set
     * Everything else → role=deployment_unit (standalone)
     */
    private void resolveAndRegisterModules(File root, String parentWarAppId,
                                           ArchitectureModel model,
                                           Map<String, CtModel> ctModels) {
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
                if (content.contains("wildfly") || content.contains("jboss")
                        || content.contains("javaee") || content.contains("java-ee")) return "javaee";
            } catch (Exception ignored) {}
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
