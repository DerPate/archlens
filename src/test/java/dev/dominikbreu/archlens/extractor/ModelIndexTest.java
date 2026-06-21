package dev.dominikbreu.archlens.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.CallEdge;
import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.DataFlowSink;
import dev.dominikbreu.archlens.model.Dependency;
import dev.dominikbreu.archlens.model.FieldAccess;
import dev.dominikbreu.archlens.model.OutboundSinkSite;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.DependencyId;
import dev.dominikbreu.archlens.model.ids.FieldAccessId;
import dev.dominikbreu.archlens.model.ids.FieldBinding;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelIndexTest {

    @Test
    void callAdjacency_returnsEdgesForNode() {
        CallEdge e = new CallEdge();
        e.fromComponentId = ComponentId.of("A");
        e.fromMethod = "go";
        e.toComponentId = ComponentId.of("B");
        e.toMethod = "run";
        e.id = "call:A#go->B#run";

        CallAdjacency adj = CallAdjacency.build(List.of(e));

        assertThat(adj.edges(ComponentId.of("A"), "go")).containsExactly(e);
        assertThat(adj.edges(ComponentId.of("B"), "run")).isEmpty();
    }

    @Test
    void fieldAccessIndex_separatesReadsAndWrites() {
        FieldAccess read = new FieldAccess();
        read.kind = FieldAccess.Kind.READ;
        read.componentId = ComponentId.of("A");
        read.method = "get";
        read.fieldBinding = new FieldBinding.Own("cache");
        read.id = FieldAccessId.of("r1");
        FieldAccess write = new FieldAccess();
        write.kind = FieldAccess.Kind.WRITE;
        write.componentId = ComponentId.of("A");
        write.method = "put";
        write.fieldBinding = new FieldBinding.Own("cache");
        write.id = FieldAccessId.of("w1");

        FieldAccessIndex idx = FieldAccessIndex.build(List.of(read, write));

        assertThat(idx.reads(ComponentId.of("A"), "get")).containsExactly(read);
        assertThat(idx.writes(ComponentId.of("A"), "put")).containsExactly(write);
        assertThat(idx.reads(ComponentId.of("A"), "put")).isEmpty();
    }

    @Test
    void outboundSinkIndex_returnsSitesByComponentAndMethod() {
        OutboundSinkSite site = new OutboundSinkSite();
        site.id = "o1";
        site.componentId = ComponentId.of("A");
        site.method = "send";
        site.kind = DataFlowSink.Kind.MESSAGING;

        OutboundSinkIndex idx = OutboundSinkIndex.build(List.of(site));

        assertThat(idx.sites(ComponentId.of("A"), "send")).containsExactly(site);
        assertThat(idx.sites(ComponentId.of("A"), "other")).isEmpty();
    }

    @Test
    void entityIndex_resolvesByBasePackageAndName() {
        Component entity = new Component();
        entity.id = ComponentId.of("com.example.model.Order");
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
        entity.id = ComponentId.of("com.example.model.OrderEntity");
        entity.name = "OrderEntity";
        entity.qualifiedName = "com.example.model.OrderEntity";
        entity.type = ComponentType.ENTITY;

        EntityIndex idx = EntityIndex.build(List.of(entity));

        assertThat(idx.resolve("com.example", "Order")).isEqualTo("com.example.model.OrderEntity");
    }

    @Test
    void dependencyAdjacency_returnsTargetsForComponent() {
        Dependency dep = new Dependency();
        dep.fromId = ComponentId.of("A");
        dep.toId = ComponentId.of("B");
        dep.kind = "injection";
        dep.id = DependencyId.of(dep.fromId, dep.toId);

        DependencyAdjacency adj = DependencyAdjacency.build(List.of(dep));

        assertThat(adj.targets(ComponentId.of("A"))).containsEntry(ComponentId.of("B"), "injection");
        assertThat(adj.targets(ComponentId.of("B"))).isEmpty();
    }

    @Test
    void modelIndex_build_populatesAllSubIndexes() {
        ArchitectureModel model = new ArchitectureModel("test");
        Component c = new Component();
        c.id = ComponentId.of("A");
        c.name = "A";
        c.type = ComponentType.SERVICE;
        model.components.add(c);

        ModelIndex idx = ModelIndex.build(model);

        assertThat(idx.components.get(ComponentId.of("A"))).isSameAs(c);
        assertThat(idx.callAdj.edges(ComponentId.of("A"), "go")).isEmpty();
    }
}
