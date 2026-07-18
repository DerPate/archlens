package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.*;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Second-pass resolver that fills in the {@code topic} of outbound messaging sink sites whose
 * destination could not be read directly from the send call (i.e. the topic argument was not a
 * plain string literal).
 *
 * <p>For each unresolved {@link OutboundSinkSite}, it locates the originating send invocation in
 * the Spoon model and resolves the topic argument. When the topic flows in through a parameter of
 * the enclosing wrapper method (either read directly, {@link TopicArgKind#PARAM_REF}, or via an
 * accessor call on it, {@link TopicArgKind#METHOD_CALL}), the site is expanded <em>per caller call
 * site</em>: each expanded copy carries the topic passed at that call site and is restricted (via
 * {@link OutboundSinkSite#restrictedCallerComponentId}) to call chains entering the wrapper from
 * that caller. This prevents the union of all callers' topics from being attributed to every
 * caller. METHOD_CALL expansions are additionally tagged with {@code topic-expansion} evidence.
 *
 * <p>Sites whose topic cannot be tied to a wrapper parameter fall back to a global
 * {@link StringExpressionResolver} walk and are expanded unrestricted, one site per literal; a
 * site that cannot be resolved at all is kept with {@code topic == null} rather than falling back
 * to a misleading channel name.
 *
 * <p>{@code maxDepth} bounds the recursive caller/implementation walk performed by
 * {@link StringExpressionResolver}. Runs over sink sites from a given index onward so it can be
 * invoked incrementally after each extraction pass.
 */
public class MessagingTopicResolver {

    private final int maxDepth;

    /**
     * @param maxDepth bounds the recursive caller/implementation walk used to recover topic literals
     */
    public MessagingTopicResolver(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    /**
     * Resolves unresolved OutboundSinkSites from {@code fromIndex} onward.
     * Sites with LITERAL kind or non-null topic are left unchanged.
     * Parameter-fed sites are expanded per caller call site with a caller restriction;
     * other non-literal sites are expanded unrestricted, one site per resolved literal;
     * if unresolvable, the site is kept with topic=null (suppresses spurious channel name).
     */
    public void resolve(ArchitectureModel model, CtModel spoonModel, int fromIndex) {
        if (fromIndex >= model.outboundSinkSites.size()) return;

        List<OutboundSinkSite> toProcess =
                new ArrayList<>(model.outboundSinkSites.subList(fromIndex, model.outboundSinkSites.size()));
        model.outboundSinkSites
                .subList(fromIndex, model.outboundSinkSites.size())
                .clear();

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

            CtMethod<?> containingMethod = findMethod(containingType, site.method, site.source.line);
            if (containingMethod == null) {
                model.outboundSinkSites.add(site);
                continue;
            }

            CtInvocation<?> sendInv = findInvocationAtLine(containingMethod, site.source.line);
            if (sendInv == null || sendInv.getArguments().isEmpty()) {
                model.outboundSinkSites.add(site);
                continue;
            }

            TopicParam topicParam = topicParameter(site, containingMethod, sendInv);
            if (topicParam != null) {
                // Parameter-fed topic: per-call-site expansion is authoritative. When no call
                // site resolves (e.g. a dead overload with no callers), keep the site
                // unresolved instead of falling back to the global caller walk — that walk
                // matches call sites of ALL overloads by simple name and would re-union every
                // caller's topic onto every chain reaching the wrapper.
                List<OutboundSinkSite> perCaller =
                        expandPerCallSite(site, containingType, containingMethod, topicParam, spoonModel);
                if (perCaller.isEmpty()) {
                    model.outboundSinkSites.add(site); // keep with null topic
                } else {
                    model.outboundSinkSites.addAll(perCaller);
                }
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

    /** How the topic reaches the send call: wrapper parameter index, plus the accessor method
     * name when the topic is {@code <param>.<accessor>()} rather than the parameter itself. */
    private record TopicParam(int paramIndex, String accessor) {}

    /**
     * Determines whether the site's topic is fed through a parameter of the wrapper method —
     * directly ({@link TopicArgKind#PARAM_REF}) or via an accessor call on a parameter
     * ({@link TopicArgKind#METHOD_CALL}). Returns {@code null} when it is not.
     */
    private TopicParam topicParameter(OutboundSinkSite site, CtMethod<?> wrapperMethod, CtInvocation<?> sendInv) {
        CtExpression<?> topicArg = sendInv.getArguments().get(0);

        if (site.topicArgKind == TopicArgKind.PARAM_REF && site.topicArgParamIndex >= 0) {
            return new TopicParam(site.topicArgParamIndex, null);
        }
        if (site.topicArgKind == TopicArgKind.METHOD_CALL
                && topicArg instanceof CtInvocation<?> topicInv
                && topicInv.getTarget() instanceof CtVariableRead<?> receiver
                && topicInv.getExecutable() != null) {
            try {
                if (receiver.getVariable().getDeclaration() instanceof CtParameter<?> param) {
                    List<CtParameter<?>> params = wrapperMethod.getParameters();
                    for (int i = 0; i < params.size(); i++) {
                        if (params.get(i).getSimpleName().equals(param.getSimpleName())) {
                            return new TopicParam(
                                    i, topicInv.getExecutable().getSimpleName());
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Expands a wrapper-method site whose topic flows in through a parameter into one
     * caller-restricted site per (call site, resolved literal). Returns an empty list when no
     * call site of this overload resolves to a literal.
     */
    private List<OutboundSinkSite> expandPerCallSite(
            OutboundSinkSite site,
            CtType<?> wrapperType,
            CtMethod<?> wrapperMethod,
            TopicParam topicParam,
            CtModel spoonModel) {

        int paramIndex = topicParam.paramIndex();
        String topicAccessor = topicParam.accessor();

        List<OutboundSinkSite> expanded = new ArrayList<>();
        for (CtInvocation<?> callSite : callSitesOf(wrapperType, wrapperMethod, spoonModel)) {
            CtMethod<?> callerMethod = callSite.getParent(CtMethod.class);
            CtType<?> callerType = callSite.getParent(CtType.class);
            if (callerMethod == null || callerType == null) continue;

            CtExpression<?> callerArg = callSite.getArguments().get(paramIndex);
            Set<String> topics;
            if (topicAccessor == null) {
                topics = StringExpressionResolver.resolve(
                        callerArg, callerType, callerMethod, spoonModel, maxDepth, new HashSet<>());
            } else {
                topics = resolveAccessorOnArgumentType(callerArg, topicAccessor, spoonModel);
                if (topics.isEmpty()) {
                    // Argument's concrete type is unknown — expand over all implementations of the
                    // declared parameter type, but stay restricted to this caller so the expansion
                    // cannot leak onto callers of other (resolvable) call sites.
                    CtTypeReference<?> declaredType =
                            wrapperMethod.getParameters().get(paramIndex).getType();
                    if (declaredType != null) {
                        topics = StringExpressionResolver.resolveFromImplementations(
                                declaredType, topicAccessor, spoonModel, maxDepth, new HashSet<>());
                    }
                }
            }

            for (String topic : topics) {
                OutboundSinkSite copy = site.withTopic(topic);
                copy.restrictedCallerComponentId = ComponentId.of(callerType.getQualifiedName());
                copy.restrictedCallerMethod = callerMethod.getSimpleName();
                if (topicAccessor != null) {
                    copy.linkEvidence =
                            site.linkEvidence == null ? "topic-expansion" : site.linkEvidence + "+topic-expansion";
                }
                expanded.add(copy);
            }
        }
        return expanded;
    }

    /**
     * Resolves {@code <arg>.<accessor>()} from the argument's concrete static type: finds that
     * type in the model and resolves the string returned by its {@code accessor} method. Returns
     * an empty set when the type is unknown, an interface, or the accessor yields no literal.
     */
    private Set<String> resolveAccessorOnArgumentType(CtExpression<?> arg, String accessor, CtModel spoonModel) {
        CtTypeReference<?> argType = arg.getType();
        if (argType == null) return Set.of();
        CtType<?> concrete = findType(spoonModel, argType.getQualifiedName());
        if (concrete == null || concrete.isInterface()) return Set.of();
        Set<String> results = new LinkedHashSet<>();
        for (CtMethod<?> m : concrete.getMethods()) {
            if (!accessor.equals(m.getSimpleName())) continue;
            for (CtReturn<?> ret : m.getElements(new TypeFilter<>(CtReturn.class))) {
                CtExpression<?> returned = ret.getReturnedExpression();
                if (returned != null) {
                    results.addAll(StringExpressionResolver.resolve(
                            returned, concrete, m, spoonModel, maxDepth, new HashSet<>()));
                }
            }
        }
        return results;
    }

    /**
     * Finds all invocations of exactly this wrapper method overload (same simple name, same
     * declaring type, same arity) across the model.
     */
    private List<CtInvocation<?>> callSitesOf(CtType<?> wrapperType, CtMethod<?> wrapperMethod, CtModel spoonModel) {
        String name = wrapperMethod.getSimpleName();
        int arity = wrapperMethod.getParameters().size();
        List<CtInvocation<?>> callSites = new ArrayList<>();
        for (CtType<?> candidate : spoonModel.getAllTypes()) {
            for (CtInvocation<?> inv : candidate.getElements(new TypeFilter<>(CtInvocation.class))) {
                if (inv.getExecutable() == null
                        || !name.equals(inv.getExecutable().getSimpleName())) continue;
                if (inv.getArguments().size() != arity) continue;
                CtTypeReference<?> declType = inv.getExecutable().getDeclaringType();
                if (declType == null) continue;
                // noClasspath mode often loses qualified name; fall back to simple name
                boolean matches = wrapperType.getQualifiedName().equals(declType.getQualifiedName())
                        || wrapperType.getSimpleName().equals(declType.getSimpleName());
                if (!matches) continue;
                callSites.add(inv);
            }
        }
        return callSites;
    }

    private CtType<?> findType(CtModel model, String qualifiedName) {
        return model.getAllTypes().stream()
                .filter(t -> qualifiedName.equals(t.getQualifiedName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds the method named {@code methodName} whose body spans {@code sendLine} — overload-aware:
     * when a wrapper class overloads the method (e.g. two {@code sendKafkaEvent} variants), the
     * simple-name-only lookup used to return an arbitrary overload, so the send invocation was not
     * found and the site silently stayed unresolved. Falls back to the first name match when no
     * position spans the line.
     */
    private CtMethod<?> findMethod(CtType<?> type, String methodName, int sendLine) {
        CtMethod<?> fallback = null;
        for (CtMethod<?> m : type.getMethods()) {
            if (!methodName.equals(m.getSimpleName())) continue;
            if (fallback == null) fallback = m;
            var pos = m.getPosition();
            if (pos != null && pos.isValidPosition() && pos.getLine() <= sendLine && sendLine <= pos.getEndLine()) {
                return m;
            }
        }
        return fallback;
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
