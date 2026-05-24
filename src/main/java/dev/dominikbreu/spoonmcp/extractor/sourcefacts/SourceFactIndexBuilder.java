package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeMember;

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

            buildMembers(ctModel, types, methods, fields, annotations);

            SourceFactIndex index = new SourceFactIndex(
                    types,
                    methods,
                    fields,
                    annotations,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    Map.of());
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

    private void buildMembers(
            CtModel ctModel,
            List<SourceType> types,
            List<SourceMethod> methods,
            List<SourceField> fields,
            List<SourceAnnotation> annotations) {
        Span span = tracer().spanBuilder("sourcefacts.members").startSpan();
        try (Scope scope = span.makeCurrent()) {
            for (CtType<?> type : ctModel.getAllTypes()) {
                String typeId = typeId(type.getQualifiedName());
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
                    String fieldType = field.getType() == null ? null : field.getType().getQualifiedName();
                    String fieldId = fieldId(type.getQualifiedName(), field.getSimpleName());
                    fields.add(new SourceField(fieldId, typeId, field.getSimpleName(), fieldType, location(field)));
                    annotations.addAll(annotations(fieldId, field));
                }

                for (CtTypeMember member : type.getTypeMembers()) {
                    if (member instanceof CtConstructor<?> constructor) {
                        String signature = constructor.getSignature();
                        methods.add(methodFact(type, "<init>", signature, true, constructor.getParameters(), constructor));
                        annotations.addAll(annotations(methodId(type.getQualifiedName(), signature), constructor));
                    }
                }

                for (CtMethod<?> method : type.getMethods()) {
                    methods.add(methodFact(
                            type, method.getSimpleName(), method.getSignature(), false, method.getParameters(), method));
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

    private SourceMethod methodFact(
            CtType<?> owner,
            String name,
            String signature,
            boolean constructor,
            List<CtParameter<?>> parameters,
            CtElement element) {
        List<String> parameterNames = parameters.stream().map(CtParameter::getSimpleName).toList();
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

    private List<SourceAnnotation> annotations(String ownerId, CtElement element) {
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

    @SuppressWarnings("rawtypes")
    private Map<String, String> annotationValues(CtAnnotation<?> annotation) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, CtExpression> entry : annotation.getValues().entrySet()) {
            values.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().toString());
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

    public static String typeId(String qualifiedName) {
        return "type:" + qualifiedName;
    }

    public static String methodId(String qualifiedTypeName, String signature) {
        return "method:" + qualifiedTypeName + "#" + signature;
    }

    public static String fieldId(String qualifiedTypeName, String fieldName) {
        return "field:" + qualifiedTypeName + "#" + fieldName;
    }

    private static String packageName(String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        return dot < 0 ? "" : qualifiedName.substring(0, dot);
    }

    public static SourceLocation location(CtElement element) {
        if (element == null || element.getPosition() == null || !element.getPosition().isValidPosition()) {
            return SourceLocation.unknown();
        }
        return new SourceLocation(element.getPosition().getFile().getPath(), element.getPosition().getLine());
    }
}
