package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.CallEdge;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.FieldAccess;
import dev.dominikbreu.spoonmcp.model.OutboundSinkSite;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelIndexTest {

    @Test
    void callAdjacency_returnsEdgesForNode() {
        CallEdge e = new CallEdge();
        e.fromComponentId = "comp:A";
        e.fromMethod = "go";
        e.toComponentId = "comp:B";
        e.toMethod = "run";
        e.id = "call:A#go->B#run";

        CallAdjacency adj = CallAdjacency.build(List.of(e));

        assertThat(adj.edges("comp:A", "go")).containsExactly(e);
        assertThat(adj.edges("comp:B", "run")).isEmpty();
    }

    @Test
    void fieldAccessIndex_separatesReadsAndWrites() {
        FieldAccess read = new FieldAccess();
        read.kind = FieldAccess.Kind.READ;
        read.componentId = "comp:A";
        read.method = "get";
        read.fieldName = "cache";
        read.id = "r1";
        FieldAccess write = new FieldAccess();
        write.kind = FieldAccess.Kind.WRITE;
        write.componentId = "comp:A";
        write.method = "put";
        write.fieldName = "cache";
        write.id = "w1";

        FieldAccessIndex idx = FieldAccessIndex.build(List.of(read, write));

        assertThat(idx.reads("comp:A", "get")).containsExactly(read);
        assertThat(idx.writes("comp:A", "put")).containsExactly(write);
        assertThat(idx.reads("comp:A", "put")).isEmpty();
    }

    @Test
    void outboundSinkIndex_returnsSitesByComponentAndMethod() {
        OutboundSinkSite site = new OutboundSinkSite();
        site.id = "o1";
        site.componentId = "comp:A";
        site.method = "send";
        site.kind = DataFlowSink.Kind.MESSAGING;

        OutboundSinkIndex idx = OutboundSinkIndex.build(List.of(site));

        assertThat(idx.sites("comp:A", "send")).containsExactly(site);
        assertThat(idx.sites("comp:A", "other")).isEmpty();
    }

    @Test
    void entityIndex_resolvesByBasePackageAndName() {
        Component entity = new Component();
        entity.id = "comp:com.example.model.Order";
        entity.name = "Order";
        entity.qualifiedName = "com.example.model.Order";
        entity.type = ComponentType.ENTITY;

        EntityIndex idx = EntityIndex.build(List.of(entity));

        assertThat(idx.resolve("com.example", "Order")).isEqualTo("com.example.model.Order");
        assertThat(idx.resolve("com.example", "OrderEntity")).isNull();
        assertThat(idx.resolve("other", "Order")).isNull();
    }

    @Test
    void entityIndex_resolvesByEntitySuffix() {
        Component entity = new Component();
        entity.id = "comp:com.example.model.OrderEntity";
        entity.name = "OrderEntity";
        entity.qualifiedName = "com.example.model.OrderEntity";
        entity.type = ComponentType.ENTITY;

        EntityIndex idx = EntityIndex.build(List.of(entity));

        assertThat(idx.resolve("com.example", "Order")).isEqualTo("com.example.model.OrderEntity");
    }

    @Test
    void dependencyAdjacency_returnsTargetsForComponent() {
        Dependency dep = new Dependency();
        dep.fromId = "comp:A";
        dep.toId = "comp:B";
        dep.kind = "injection";
        dep.id = "d1";

        DependencyAdjacency adj = DependencyAdjacency.build(List.of(dep));

        assertThat(adj.targets("comp:A")).containsEntry("comp:B", "injection");
        assertThat(adj.targets("comp:B")).isEmpty();
    }

    @Test
    void modelIndex_build_populatesAllSubIndexes() {
        ArchitectureModel model = new ArchitectureModel("test");
        Component c = new Component();
        c.id = "comp:A";
        c.name = "A";
        c.type = ComponentType.SERVICE;
        model.components.add(c);

        ModelIndex idx = ModelIndex.build(model);

        assertThat(idx.components.get("comp:A")).isSameAs(c);
        assertThat(idx.callAdj.edges("comp:A", "go")).isEmpty();
    }
}
