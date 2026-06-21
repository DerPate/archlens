package dev.dominikbreu.archlens.cache;

import dev.dominikbreu.archlens.model.ids.GraphNodeId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONIo;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONVersion;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;

/**
 * Owns the TinkerPop in-memory graph.
 *
 * <p>Package-private fields {@code graph}, {@code g}, and {@code verticesById} are accessed
 * directly by {@link GraphProjector} (write path) and {@link GraphQuery} (read path).
 * Nothing outside the {@code cache} package touches TinkerGraph directly.</p>
 */
class GraphStore {

    Graph graph = TinkerGraph.open();
    GraphTraversalSource g = graph.traversal();
    final Map<GraphNodeId, Vertex> verticesById = new LinkedHashMap<>();
    boolean projected = false;

    boolean isEmpty() {
        return verticesById.isEmpty();
    }

    boolean isIndexed() {
        return projected;
    }

    long vertexCount() {
        return verticesById.size();
    }

    long edgeCount() {
        return g.E().count().next();
    }

    void clear() {
        try {
            g.close();
            graph.close();
        } catch (Exception ignored) {
        }
        graph = TinkerGraph.open();
        g = graph.traversal();
        verticesById.clear();
        projected = false;
    }

    String serializeGraphSON() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        graph.io(GraphSONIo.build(GraphSONVersion.V3_0)).writer().create().writeGraph(out, graph);
        return out.toString(StandardCharsets.UTF_8);
    }

    void loadFrom(String graphSON) throws Exception {
        clear();
        ByteArrayInputStream in = new ByteArrayInputStream(graphSON.getBytes(StandardCharsets.UTF_8));
        graph.io(GraphSONIo.build(GraphSONVersion.V3_0)).reader().create().readGraph(in, graph);
        g = graph.traversal();
        graph.vertices().forEachRemaining(v -> verticesById.put(GraphNodeId.of(v.id().toString()), v));
        projected = true;
    }

    static GraphStore deserializeGraphSON(String json) throws Exception {
        GraphStore store = new GraphStore();
        ByteArrayInputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        store.graph.io(GraphSONIo.build(GraphSONVersion.V3_0)).reader().create().readGraph(in, store.graph);
        store.g = store.graph.traversal();
        store.graph.vertices().forEachRemaining(v -> store.verticesById.put(GraphNodeId.of(v.id().toString()), v));
        store.projected = true;
        return store;
    }
}
