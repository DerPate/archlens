package dev.dominikbreu.archlens.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import spoon.Launcher;
import spoon.reflect.declaration.CtType;

class PersistenceEntityTypesTest {

    @Test
    void detectsAnnotatedEntityRegardlessOfPackage() {
        assertThat(PersistenceEntityTypes.isEntity(parse("""
                        package com.acme.bean;
                        import jakarta.persistence.Entity;
                        @Entity public class Customer { private String name; }
                        """))).isTrue();
    }

    @Test
    void detectsSubclassOfMappedSuperclass() {
        // The subclass carries no annotation of its own; only its parent does.
        CtType<?> base = parse("""
                package com.acme.domain;
                import jakarta.persistence.MappedSuperclass;
                @MappedSuperclass public class AuditedBase { private String createdBy; }
                """);
        assertThat(PersistenceEntityTypes.isEntity(base)).isTrue();
    }

    @Test
    void detectsPanacheEntityByBaseClass() {
        assertThat(PersistenceEntityTypes.isEntity(parse("""
                        package com.acme.databaseobject;
                        import io.quarkus.hibernate.orm.panache.PanacheEntity;
                        public class Order extends PanacheEntity { public String reference; }
                        """))).isTrue();
    }

    @Test
    void plainClassIsNotAnEntity() {
        assertThat(PersistenceEntityTypes.isEntity(parse("""
                        package com.acme.model;
                        public class OrderDto { private String reference; }
                        """))).isFalse();
    }

    @Test
    void serviceInEntityPackageIsNotAnEntity() {
        // Package layout must not drive the decision in either direction.
        assertThat(PersistenceEntityTypes.isEntity(parse("""
                        package com.acme.entity;
                        public class OrderService { public void handle() {} }
                        """))).isFalse();
    }

    private static CtType<?> parse(String source) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(new spoon.support.compiler.VirtualFile(source));
        launcher.buildModel();
        return launcher.getModel().getAllTypes().iterator().next();
    }
}
