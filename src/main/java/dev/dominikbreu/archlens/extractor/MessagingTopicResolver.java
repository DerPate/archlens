package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

public class MessagingTopicResolver {

    private final int maxDepth;

    public MessagingTopicResolver(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    /**
     * Resolves unresolved OutboundSinkSites from {@code fromIndex} onward.
     * Sites with LITERAL kind or non-null topic are left unchanged.
     * Non-literal sites are expanded to one site per resolved topic literal;
     * if unresolvable, the site is kept with topic=null (suppresses spurious channel name).
     */
    public void resolve(ArchitectureModel model, CtModel spoonModel, int fromIndex) {
        if (fromIndex >= model.outboundSinkSites.size()) return;

        List<OutboundSinkSite> toProcess = new ArrayList<>(
                model.outboundSinkSites.subList(fromIndex, model.outboundSinkSites.size()));
        model.outboundSinkSites.subList(fromIndex, model.outboundSinkSites.size()).clear();

        for (OutboundSinkSite site : toProcess) {
            if (site.topicArgKind == TopicArgKind.LITERAL || site.topic != null) {
                model.outboundSinkSites.add(site);
                continue;
            }

            CtType<?> containingType = findType(spoonModel, site.componentId.qualifiedName());
            if (containingType == null) {
                model.outboundSinkSites.add(site);
                continue;
            }

            CtMethod<?> containingMethod = findMethod(containingType, site.method);
            if (containingMethod == null) {
                model.outboundSinkSites.add(site);
                continue;
            }

            CtInvocation<?> sendInv = findInvocationAtLine(containingMethod, site.source.line);
            if (sendInv == null || sendInv.getArguments().isEmpty()) {
                model.outboundSinkSites.add(site);
                continue;
            }

            Set<String> topics = StringExpressionResolver.resolve(
                    sendInv.getArguments().get(0),
                    containingType,
                    containingMethod,
                    spoonModel,
                    maxDepth,
                    new HashSet<>());

            if (topics.isEmpty()) {
                model.outboundSinkSites.add(site); // keep with null topic
            } else {
                for (String topic : topics) {
                    model.outboundSinkSites.add(site.withTopic(topic));
                }
            }
        }
    }

    private CtType<?> findType(CtModel model, String qualifiedName) {
        return model.getAllTypes().stream()
                .filter(t -> qualifiedName.equals(t.getQualifiedName()))
                .findFirst()
                .orElse(null);
    }

    private CtMethod<?> findMethod(CtType<?> type, String methodName) {
        return type.getMethods().stream()
                .filter(m -> methodName.equals(m.getSimpleName()))
                .findFirst()
                .orElse(null);
    }

    private CtInvocation<?> findInvocationAtLine(CtMethod<?> method, int line) {
        return method.getElements(new TypeFilter<>(CtInvocation.class)).stream()
                .filter(inv -> {
                    var pos = inv.getPosition();
                    return pos != null && pos.isValidPosition() && pos.getLine() == line;
                })
                .findFirst()
                .orElse(null);
    }
}
