package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import dev.dominikbreu.spoonmcp.model.ids.SourceFactId;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeMember;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class SourceFactIndexBuilder {

    private static Tracer tracer() {
        return GlobalOpenTelemetry.getTracer("dev.dominikbreu.spoonmcp");
    }

    public SourceFactIndex build(CtModel ctModel, String moduleName, int sourceRootCount) {
        Span span = tracer().spanBuilder("sourcefacts.build")
                .setAttribute("module", moduleName)
                .setAttribute("source-root-count", sourceRootCount)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            List<SourceType> types = new ArrayList<>();
            List<SourceMethod> methods = new ArrayList<>();
            List<SourceField> fields = new ArrayList<>();
            List<SourceAnnotation> annotations = new ArrayList<>();
            List<SourceInjectionPoint> injectionPoints = new ArrayList<>();
            List<SourceInvocation> invocations = new ArrayList<>();
            List<SourceAssignment> assignments = new ArrayList<>();
            List<SourceReturn> returns = new ArrayList<>();

            buildMembers(ctModel, types, methods, fields, annotations);
            buildInjectionFacts(ctModel, annotations, injectionPoints);
            buildCodeFlowFacts(ctModel, invocations, assignments, returns);

            Map<String, List<SourceType>> implementations = buildImplementations(ctModel, types);

            SourceFactIndex index = new SourceFactIndex(
                    types,
                    methods,
                    fields,
                    annotations,
                    injectionPoints,
                    invocations,
                    assignments,
                    returns,
                    implementations);
            setBuildCounts(span, index);
            return index;
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    private Map<String, List<SourceType>> buildImplementations(CtModel ctModel, List<SourceType> sourceTypes) {
        Span span = tracer().spanBuilder("sourcefacts.inheritance").startSpan();
        try (Scope scope = span.makeCurrent()) {
            Map<String, SourceType> factsByQualifiedName = new LinkedHashMap<>();
            Map<String, CtType<?>> spoonTypesByQualifiedName = new LinkedHashMap<>();
            for (SourceType sourceType : sourceTypes) {
                factsByQualifiedName.put(sourceType.qualifiedName(), sourceType);
            }
            for (CtType<?> type : ctModel.getAllTypes()) {
                spoonTypesByQualifiedName.put(type.getQualifiedName(), type);
            }

            Map<String, List<SourceType>> implementations = new LinkedHashMap<>();
            for (CtType<?> type : ctModel.getAllTypes()) {
                SourceType concrete = factsByQualifiedName.get(type.getQualifiedName());
                if (concrete == null || concrete.interfaceType() || concrete.abstractType()) continue;

                for (String supertype : supertypeClosure(type, spoonTypesByQualifiedName)) {
                    SourceType superFact = factsByQualifiedName.get(supertype);
                    if (superFact != null) {
                        implementations
                                .computeIfAbsent(superFact.qualifiedName(), ignored -> new ArrayList<>())
                                .add(concrete);
                    }
                }
            }
            span.setAttribute("implementation-groups", (long) implementations.size());
            span.setAttribute(
                    "implementation-links",
                    implementations.values().stream().mapToLong(List::size).sum());
            return implementations;
        } finally {
            span.end();
        }
    }

    private Set<String> supertypeClosure(CtType<?> type, Map<String, CtType<?>> spoonTypesByQualifiedName) {
        Set<String> result = new LinkedHashSet<>();
        collectSupertypes(type, spoonTypesByQualifiedName, result);
        return result;
    }

    private void collectSupertypes(
            CtType<?> type, Map<String, CtType<?>> spoonTypesByQualifiedName, Set<String> result) {
        for (CtTypeReference<?> iface : type.getSuperInterfaces()) {
            collectTypeReference(iface, spoonTypesByQualifiedName, result);
        }
        collectTypeReference(type.getSuperclass(), spoonTypesByQualifiedName, result);
    }

    private void collectTypeReference(
            CtTypeReference<?> reference, Map<String, CtType<?>> spoonTypesByQualifiedName, Set<String> result) {
        if (reference == null || reference.getQualifiedName() == null) return;
        String qualifiedName = reference.getQualifiedName();
        if (!result.add(qualifiedName)) return;
        CtType<?> declaration = spoonTypesByQualifiedName.get(qualifiedName);
        if (declaration != null) {
            collectSupertypes(declaration, spoonTypesByQualifiedName, result);
        }
    }

    private void buildMembers(
            CtModel ctModel,
            List<SourceType> types,
            List<SourceMethod> methods,
            List<SourceField> fields,
            List<SourceAnnotation> annotations) {
        Span span = tracer().spanBuilder("sourcefacts.members").startSpan();
        try (Scope scope = span.makeCurrent()) {
            for (CtType<?> type : ctModel.getAllTypes()) {
                SourceFactId typeId = typeId(type.getQualifiedName());
                SourceType sourceType = new SourceType(
                        typeId,
                        type.getQualifiedName(),
                        type.getSimpleName(),
                        packageName(type.getQualifiedName()),
                        type.isInterface(),
                        type.hasModifier(spoon.reflect.declaration.ModifierKind.ABSTRACT),
                        location(type));
                types.add(sourceType);
                annotations.addAll(annotations(typeId, type));

                for (CtField<?> field : type.getFields()) {
                    String fieldType;
                    if (field.getType() == null) {
                        fieldType = null;
                    } else {
                        fieldType = field.getType().getQualifiedName();
                    }
                    SourceFactId fieldId = fieldId(type.getQualifiedName(), field.getSimpleName());
                    fields.add(new SourceField(fieldId, typeId, field.getSimpleName(), fieldType, location(field)));
                    annotations.addAll(annotations(fieldId, field));
                }

                for (CtTypeMember member : type.getTypeMembers()) {
                    if (member instanceof CtConstructor<?> constructor) {
                        String signature = constructor.getSignature();
                        methods.add(
                                methodFact(type, "<init>", signature, true, constructor.getParameters(), constructor));
                        annotations.addAll(annotations(methodId(type.getQualifiedName(), signature), constructor));
                    }
                }

                for (CtMethod<?> method : type.getMethods()) {
                    methods.add(methodFact(
                            type,
                            method.getSimpleName(),
                            method.getSignature(),
                            false,
                            method.getParameters(),
                            method));
                    annotations.addAll(annotations(methodId(type.getQualifiedName(), method.getSignature()), method));
                }
            }
            span.setAttribute("types", (long) types.size());
            span.setAttribute("methods", (long) methods.size());
            span.setAttribute("fields", (long) fields.size());
            span.setAttribute("annotations", (long) annotations.size());
        } finally {
            span.end();
        }
    }

    private void buildInjectionFacts(
            CtModel ctModel, List<SourceAnnotation> annotations, List<SourceInjectionPoint> injectionPoints) {
        Span span = tracer().spanBuilder("sourcefacts.injection").startSpan();
        try (Scope scope = span.makeCurrent()) {
            Map<SourceFactId, List<SourceAnnotation>> annotationsByOwner = indexAnnotationsByOwner(annotations);
            for (CtType<?> type : ctModel.getAllTypes()) {
                collectFieldInjectionPoints(type, annotationsByOwner, injectionPoints);
                for (CtTypeMember member : type.getTypeMembers()) {
                    if (member instanceof CtConstructor<?> constructor) {
                        addConstructorInjectionFacts(type, constructor, injectionPoints);
                    }
                }
            }
            span.setAttribute("injection-point-count", (long) injectionPoints.size());
        } finally {
            span.end();
        }
    }

    private Map<SourceFactId, List<SourceAnnotation>> indexAnnotationsByOwner(List<SourceAnnotation> annotations) {
        Map<SourceFactId, List<SourceAnnotation>> annotationsByOwner = new LinkedHashMap<>();
        for (SourceAnnotation annotation : annotations) {
            annotationsByOwner
                    .computeIfAbsent(annotation.ownerId(), ignored -> new ArrayList<>())
                    .add(annotation);
        }
        return annotationsByOwner;
    }

    private void collectFieldInjectionPoints(
            CtType<?> type,
            Map<SourceFactId, List<SourceAnnotation>> annotationsByOwner,
            List<SourceInjectionPoint> injectionPoints) {
        SourceFactId typeId = typeId(type.getQualifiedName());
        for (CtField<?> field : type.getFields()) {
            SourceFactId fieldId = fieldId(type.getQualifiedName(), field.getSimpleName());
            if (annotationsByOwner.getOrDefault(fieldId, List.of()).stream()
                    .anyMatch(this::isInjectionAnnotation)) {
                String fieldType = field.getType() == null ? null : field.getType().getQualifiedName();
                injectionPoints.add(new SourceInjectionPoint(
                        typeId,
                        fieldType,
                        field.getSimpleName(),
                        null,
                        SourceEvidence.FIELD_INJECTION,
                        FactConfidence.KNOWN,
                        location(field)));
            }
        }
    }

    private void addConstructorInjectionFacts(
            CtType<?> type, CtConstructor<?> constructor, List<SourceInjectionPoint> injectionPoints) {
        Map<String, CtParameter<?>> parametersByName = new LinkedHashMap<>();
        for (CtParameter<?> parameter : constructor.getParameters()) {
            parametersByName.put(parameter.getSimpleName(), parameter);
        }
        if (parametersByName.isEmpty()) {
            return;
        }

        SourceFactId typeId = typeId(type.getQualifiedName());
        for (CtAssignment<?, ?> assignment : constructor.getElements(new TypeFilter<>(CtAssignment.class))) {
            String fieldName = assignedFieldName(assignment);
            String parameterName = assignedParameterName(assignment);
            if (fieldName == null || parameterName == null) continue;

            CtParameter<?> parameter = parametersByName.get(parameterName);
            if (parameter == null) continue;
            String targetType;

            if (parameter.getType() == null) {
                targetType = null;
            } else {
                targetType = parameter.getType().getQualifiedName();
            }
            injectionPoints.add(new SourceInjectionPoint(
                    typeId,
                    targetType,
                    fieldName,
                    parameterName,
                    SourceEvidence.CONSTRUCTOR_INJECTION,
                    FactConfidence.KNOWN,
                    location(assignment)));
        }
    }

    private void buildCodeFlowFacts(
            CtModel ctModel,
            List<SourceInvocation> invocations,
            List<SourceAssignment> assignments,
            List<SourceReturn> returns) {
        Span invocationSpan = tracer().spanBuilder("sourcefacts.invocations").startSpan();
        Span assignmentSpan = tracer().spanBuilder("sourcefacts.assignments").startSpan();
        Span returnSpan = tracer().spanBuilder("sourcefacts.returns").startSpan();
        try (Scope ignored = invocationSpan.makeCurrent()) {
            int invocationIndex = 0;
            int assignmentIndex = 0;
            int returnIndex = 0;
            for (CtType<?> type : ctModel.getAllTypes()) {
                for (CtExecutable<?> executable : executables(type)) {
                    SourceFactId methodId = methodId(type.getQualifiedName(), executable.getSignature());
                    for (CtElement element : executable.getElements(new TypeFilter<>(CtElement.class))) {
                        if (element instanceof CtInvocation<?> invocation) {
                            invocations.add(invocationFact(methodId, invocation, invocationIndex++));
                        } else if (element instanceof CtAssignment<?, ?> assignment) {
                            assignments.add(assignmentFact(methodId, assignment, assignmentIndex++));
                        } else if (element instanceof CtLocalVariable<?> localVariable
                                && localVariable.getDefaultExpression() != null) {
                            assignments.add(localAssignmentFact(methodId, localVariable, assignmentIndex++));
                        } else if (element instanceof CtReturn<?> ctReturn) {
                            returns.add(returnFact(methodId, ctReturn, returnIndex++));
                        }
                    }
                }
            }
            invocationSpan.setAttribute("invocation-count", (long) invocations.size());
            assignmentSpan.setAttribute("assignment-count", (long) assignments.size());
            returnSpan.setAttribute("return-fact-count", (long) returns.size());
        } finally {
            returnSpan.end();
            assignmentSpan.end();
            invocationSpan.end();
        }
    }

    private List<CtExecutable<?>> executables(CtType<?> type) {
        List<CtExecutable<?>> result = new ArrayList<>();
        for (CtTypeMember member : type.getTypeMembers()) {
            if (member instanceof CtExecutable<?> executable) {
                result.add(executable);
            }
        }
        return result;
    }

    private SourceInvocation invocationFact(SourceFactId methodId, CtInvocation<?> invocation, int index) {
        CtElement parent = invocation.getParent();
        String assignedTo = null;
        if (parent instanceof CtAssignment<?, ?> assignment) {
            assignedTo = expressionText(assignment.getAssigned());
        } else if (parent instanceof CtLocalVariable<?> localVariable) {
            assignedTo = localVariable.getSimpleName();
        }
        return new SourceInvocation(
                SourceFactId.of(methodId.serialize() + "@invocation:" + index),
                methodId,
                expressionText(invocation.getTarget()),
                invocation.getExecutable() == null
                        ? null
                        : invocation.getExecutable().getSimpleName(),
                invocation.getArguments().stream().map(this::expressionText).toList(),
                assignedTo,
                SourceEvidence.DIRECT_TYPE_REFERENCE,
                FactConfidence.KNOWN,
                location(invocation));
    }

    private SourceAssignment assignmentFact(SourceFactId methodId, CtAssignment<?, ?> assignment, int index) {
        SourceEvidence evidence;
        if (assignment.getAssigned() instanceof CtFieldWrite<?>) {
            evidence = SourceEvidence.FIELD_ASSIGNMENT;
        } else {
            evidence = assignment.getAssignment() instanceof CtConstructorCall<?>
                    ? SourceEvidence.CONSTRUCTOR_CALL
                    : SourceEvidence.LOCAL_ASSIGNMENT;
        }
        return new SourceAssignment(
                SourceFactId.of(methodId.serialize() + "@assignment:" + index),
                methodId,
                expressionText(assignment.getAssigned()),
                expressionText(assignment.getAssignment()),
                expressionType(assignment.getAssignment()),
                evidence,
                FactConfidence.KNOWN,
                location(assignment));
    }

    private SourceAssignment localAssignmentFact(SourceFactId methodId, CtLocalVariable<?> localVariable, int index) {
        SourceEvidence evidence;
        if (localVariable.getDefaultExpression() instanceof CtConstructorCall<?>) {
            evidence = SourceEvidence.CONSTRUCTOR_CALL;
        } else {
            evidence = SourceEvidence.LOCAL_ASSIGNMENT;
        }
        return new SourceAssignment(
                SourceFactId.of(methodId.serialize() + "@assignment:" + index),
                methodId,
                localVariable.getSimpleName(),
                expressionText(localVariable.getDefaultExpression()),
                expressionType(localVariable.getDefaultExpression()),
                evidence,
                FactConfidence.KNOWN,
                location(localVariable));
    }

    private SourceReturn returnFact(SourceFactId methodId, CtReturn<?> ctReturn, int index) {
        CtExpression<?> returnedExpression = ctReturn.getReturnedExpression();
        String referencedField = referencedField(returnedExpression);
        String referencedParameter = referencedParameter(returnedExpression);
        SourceEvidence evidence;
        if (referencedField != null) {
            evidence = SourceEvidence.METHOD_RETURNS_FIELD;
        } else {
            evidence = referencedParameter != null
                    ? SourceEvidence.METHOD_RETURNS_PARAMETER
                    : returnedExpression instanceof CtInvocation<?>
                            ? SourceEvidence.METHOD_RETURNS_INVOCATION
                            : SourceEvidence.METHOD_RETURNS_LOCAL;
        }
        return new SourceReturn(
                SourceFactId.of(methodId.serialize() + "@return:" + index),
                methodId,
                expressionText(returnedExpression),
                referencedField,
                referencedParameter,
                evidence,
                FactConfidence.KNOWN,
                location(ctReturn));
    }

    private SourceMethod methodFact(
            CtType<?> owner,
            String name,
            String signature,
            boolean constructor,
            List<CtParameter<?>> parameters,
            CtElement element) {
        List<String> parameterNames =
                parameters.stream().map(CtParameter::getSimpleName).toList();
        List<String> parameterTypes = parameters.stream()
                .map(CtParameter::getType)
                .map(type -> type == null ? null : type.getQualifiedName())
                .toList();
        return new SourceMethod(
                methodId(owner.getQualifiedName(), signature),
                typeId(owner.getQualifiedName()),
                name,
                signature,
                constructor,
                parameterNames,
                parameterTypes,
                location(element));
    }

    private List<SourceAnnotation> annotations(SourceFactId ownerId, CtElement element) {
        List<SourceAnnotation> result = new ArrayList<>();
        for (CtAnnotation<?> annotation : element.getAnnotations()) {
            String qualifiedName = annotationQualifiedName(annotation);
            result.add(new SourceAnnotation(
                    ownerId,
                    qualifiedName,
                    annotationValues(annotation),
                    SourceEvidence.ANNOTATION_VALUE,
                    location(annotation)));
        }
        return result;
    }

    private String annotationQualifiedName(CtAnnotation<?> annotation) {
        if (annotation.getAnnotationType() != null) {
            return annotation.getAnnotationType().getQualifiedName();
        }
        try {
            return annotation.getActualAnnotation().annotationType().getName();
        } catch (RuntimeException e) {
            return "(unknown)";
        }
    }

    private boolean isInjectionAnnotation(SourceAnnotation annotation) {
        String qn = annotation.qualifiedName();
        return qn.endsWith(".Inject")
                || "Inject".equals(qn)
                || qn.endsWith(".Autowired")
                || qn.endsWith(".Resource")
                || "Resource".equals(qn);
    }

    private String expressionText(Object expression) {
        if (expression == null) {
            return null;
        } else {
            return expression.toString();
        }
    }

    private String expressionType(CtExpression<?> expression) {
        if (expression == null || expression.getType() == null) {
            return null;
        } else {
            return expression.getType().getQualifiedName();
        }
    }

    private String referencedField(CtExpression<?> expression) {
        if (expression instanceof CtFieldRead<?> fieldRead) {
            if (fieldRead.getVariable() == null) {
                return null;
            } else {
                return fieldRead.getVariable().getSimpleName();
            }
        }
        return null;
    }

    private String referencedParameter(CtExpression<?> expression) {
        if (expression instanceof CtVariableRead<?> variableRead
                && variableRead.getVariable() != null
                && variableRead.getVariable().getDeclaration() instanceof CtParameter<?>) {
            return variableRead.getVariable().getSimpleName();
        }
        return null;
    }

    private String assignedFieldName(CtAssignment<?, ?> assignment) {
        if (assignment.getAssigned() instanceof CtFieldWrite<?> fieldWrite && fieldWrite.getVariable() != null) {
            return fieldWrite.getVariable().getSimpleName();
        }
        return null;
    }

    private String assignedParameterName(CtAssignment<?, ?> assignment) {
        if (assignment.getAssignment() instanceof CtVariableRead<?> variableRead
                && variableRead.getVariable() != null
                && variableRead.getVariable().getDeclaration() instanceof CtParameter<?>) {
            return variableRead.getVariable().getSimpleName();
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    private Map<String, String> annotationValues(CtAnnotation<?> annotation) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, CtExpression> entry : annotation.getValues().entrySet()) {
            values.put(
                    entry.getKey(),
                    entry.getValue() == null ? null : entry.getValue().toString());
        }
        return values;
    }

    private void setBuildCounts(Span span, SourceFactIndex index) {
        span.setAttribute("type-count", (long) index.typeCount());
        span.setAttribute("method-count", (long) index.methodCount());
        span.setAttribute("field-count", (long) index.fieldCount());
        span.setAttribute("invocation-count", (long) index.invocationCount());
        span.setAttribute("assignment-count", (long) index.assignmentCount());
        span.setAttribute("return-fact-count", (long) index.returnCount());
        span.setAttribute("injection-point-count", (long) index.injectionPointCount());
        span.setAttribute("unresolved-receiver-count", 0L);
        span.setAttribute("ambiguous-receiver-count", 0L);
    }

    public static SourceFactId typeId(String qualifiedName) {
        return SourceFactId.of("type:" + qualifiedName);
    }

    public static SourceFactId methodId(String qualifiedTypeName, String signature) {
        return SourceFactId.of("method:" + qualifiedTypeName + "#" + signature);
    }

    public static SourceFactId fieldId(String qualifiedTypeName, String fieldName) {
        return SourceFactId.of("field:" + qualifiedTypeName + "#" + fieldName);
    }

    private static String packageName(String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        if (dot < 0) {
            return "";
        } else {
            return qualifiedName.substring(0, dot);
        }
    }

    public static SourceLocation location(CtElement element) {
        if (element == null
                || element.getPosition() == null
                || !element.getPosition().isValidPosition()) {
            return SourceLocation.unknown();
        }
        return new SourceLocation(
                element.getPosition().getFile().getPath(), element.getPosition().getLine());
    }
}
