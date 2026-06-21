package dev.dominikbreu.archlens.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.build.BuildModule;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

class ArchitectureExtractorTechDetectionTest extends ExtractorTestBase {

    @Test
    void detectsTechnologyFromAnnotationsInSinglePass() throws Exception {
        AtomicInteger annotationReads = new AtomicInteger();
        CtType<?> type = typeWithSingleUseAnnotations(annotationReads, "org.springframework.stereotype.Service");

        String technology = invokeDetectTechnology(List.of(type));

        assertThat(technology).isEqualTo("spring");
        assertThat(annotationReads).hasValue(1);
    }

    @Test
    void detectsQuarkusFromMethodAnnotationsWhenBuildMetadataIsUnknown() {
        ArchitectureModel model = new ArchitectureExtractor().extract(List.of(projectPath("scheduler-hub")));

        assertThat(model.applications)
                .anyMatch(app -> "scheduler-hub".equals(app.name) && "quarkus".equals(app.technology));
    }

    @SuppressWarnings("unchecked")
    private static CtType<?> typeWithSingleUseAnnotations(AtomicInteger annotationReads, String annotationName) {
        CtAnnotation<Annotation> annotation = (CtAnnotation<Annotation>) Proxy.newProxyInstance(
                CtAnnotation.class.getClassLoader(), new Class<?>[] {CtAnnotation.class}, (proxy, method, args) -> {
                    if ("getAnnotationType".equals(method.getName())) {
                        return annotationType(annotationName);
                    }
                    if ("getValues".equals(method.getName())) {
                        return java.util.Map.<String, CtExpression<?>>of();
                    }
                    return defaultValue(method);
                });
        return (CtType<?>) Proxy.newProxyInstance(
                CtType.class.getClassLoader(), new Class<?>[] {CtType.class}, (proxy, method, args) -> {
                    if ("getAnnotations".equals(method.getName())) {
                        if (annotationReads.incrementAndGet() > 1) {
                            throw new IllegalStateException("annotations were scanned more than once");
                        }
                        return List.of(annotation);
                    }
                    return defaultValue(method);
                });
    }

    @SuppressWarnings("unchecked")
    private static CtTypeReference<Annotation> annotationType(String qualifiedName) {
        return (CtTypeReference<Annotation>) Proxy.newProxyInstance(
                CtTypeReference.class.getClassLoader(),
                new Class<?>[] {CtTypeReference.class},
                (proxy, method, args) -> {
                    if ("getQualifiedName".equals(method.getName())) {
                        return qualifiedName;
                    }
                    return defaultValue(method);
                });
    }

    private static Object defaultValue(Method method) {
        Class<?> type = method.getReturnType();
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        if ("toString".equals(method.getName()))
            return method.getDeclaringClass().getSimpleName() + "Proxy";
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String invokeDetectTechnology(Collection<CtType<?>> types) throws Exception {
        Method method =
                ArchitectureExtractor.class.getDeclaredMethod("detectTechnology", Collection.class, BuildModule.class);
        method.setAccessible(true);
        BuildModule module = new BuildModule(
                "test", new File("does-not-exist"), null, "unknown", List.of(), List.of(), List.of(), "test");
        try {
            return (String) method.invoke(new ArchitectureExtractor(), types, module);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception ex) throw ex;
            throw e;
        }
    }
}
