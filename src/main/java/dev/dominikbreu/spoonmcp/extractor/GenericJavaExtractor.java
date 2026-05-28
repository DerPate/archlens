package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.SourceInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;

/**
 * Conservative fallback extractor for plain Java projects without framework annotations.
 */
public class GenericJavaExtractor {

    /** Creates a fallback extractor using conservative Java naming heuristics. */
    public GenericJavaExtractor() {}

    /**
     * Extracts conservative components from plain Java classes when no framework-specific extractor applies.
     *
     * @param types Spoon types to classify
     * @param model architecture model to update
     * @param appId owning application identifier
     */
    public void extract(Collection<CtType<?>> types, ArchitectureModel model, String appId) {
        Set<dev.dominikbreu.spoonmcp.model.ids.ComponentId> existingIds = new HashSet<>();
        for (Component component : model.components) {
            existingIds.add(component.id);
        }

        for (CtType<?> type : types) {
            if (!isApplicationType(type)) continue;

            Component component = toComponent(type, appId);
            if (!existingIds.add(component.id)) continue;

            model.components.add(component);
            model.applications.stream()
                    .filter(app -> app.id.equals(appId))
                    .findFirst()
                    .ifPresent(app -> app.componentIds.add(component.id));

            extractMainEntrypoint(type, component, model);
        }
    }

    private void extractMainEntrypoint(CtType<?> type, Component component, ArchitectureModel model) {
        for (CtMethod<?> method : type.getMethods()) {
            if (isMainMethod(method)) {
                Entrypoint ep = new Entrypoint();
                ep.id = new dev.dominikbreu.spoonmcp.model.ids.EntrypointId(
                        dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(type.getQualifiedName()), "main", "");
                ep.type = EntrypointType.MAIN_METHOD;
                ep.name = "main";
                ep.componentId = component.id;
                ep.source = new SourceInfo(getFile(method), getLine(method), "signature", 1.0);
                model.entrypoints.add(ep);
            }
        }
    }

    private boolean isMainMethod(CtMethod<?> method) {
        return "main".equals(method.getSimpleName())
                && method.isStatic()
                && method.getModifiers().contains(ModifierKind.PUBLIC)
                && "void".equals(method.getType().getSimpleName())
                && method.getParameters().size() == 1
                && method.getParameters().get(0).getType().toString().contains("String");
    }

    private boolean isApplicationType(CtType<?> type) {
        if (type.isAnonymous() || type.isLocalType() || type.isShadow()) return false;
        String qualifiedName = type.getQualifiedName();
        return qualifiedName != null
                && !qualifiedName.isBlank()
                && !qualifiedName.contains("$")
                && !"package-info".equals(type.getSimpleName());
    }

    private Component toComponent(CtType<?> type, String appId) {
        Component component = new Component();
        component.id = dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(type.getQualifiedName());
        component.type = classify(type);
        component.name = type.getSimpleName();
        component.qualifiedName = type.getQualifiedName();
        component.module = appId;
        component.technology = "java";
        component.stereotypes = stereotypes(type);
        component.source = new SourceInfo(getFile(type), getLine(type), "type-relation", 0.55);
        return component;
    }

    private ComponentType classify(CtType<?> type) {
        String qualifiedName = type.getQualifiedName().toLowerCase();
        String simpleName = type.getSimpleName().toLowerCase();

        if (qualifiedName.contains(".model.") || qualifiedName.contains(".dto.")) {
            return ComponentType.ENTITY;
        }
        if (simpleName.endsWith("repository") || simpleName.endsWith("dao")) {
            return ComponentType.REPOSITORY;
        }
        if (simpleName.endsWith("client") || simpleName.endsWith("proxy")) {
            return ComponentType.HTTP_CLIENT;
        }
        if (simpleName.endsWith("renderer")) {
            return ComponentType.SERVICE;
        }
        if (simpleName.endsWith("tool")
                || simpleName.endsWith("extractor")
                || simpleName.endsWith("merger")
                || simpleName.endsWith("scanner")
                || simpleName.endsWith("cache")
                || simpleName.endsWith("server")) {
            return ComponentType.SERVICE;
        }
        if (qualifiedName.contains(".util.") || simpleName.endsWith("utils")) {
            return ComponentType.UTILITY;
        }
        return ComponentType.UNKNOWN;
    }

    private List<String> stereotypes(CtType<?> type) {
        List<String> stereotypes = new ArrayList<>();
        stereotypes.add("plain-java");

        String qualifiedName = type.getQualifiedName();
        int packageEnd = qualifiedName.lastIndexOf('.');
        if (packageEnd > 0) {
            String packageName = qualifiedName.substring(0, packageEnd);
            int segmentStart = packageName.lastIndexOf('.');
            stereotypes.add(segmentStart >= 0 ? packageName.substring(segmentStart + 1) : packageName);
        }
        return stereotypes;
    }

    private String getFile(CtElement element) {
        var position = element.getPosition();
        return position.isValidPosition() ? position.getFile().getAbsolutePath() : "unknown";
    }

    private int getLine(CtElement element) {
        var position = element.getPosition();
        return position.isValidPosition() ? position.getLine() : 0;
    }
}
