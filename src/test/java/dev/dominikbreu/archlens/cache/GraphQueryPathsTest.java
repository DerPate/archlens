package dev.dominikbreu.archlens.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.Dependency;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.DependencyId;
import dev.dominikbreu.archlens.model.ids.GraphNodeId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GraphQueryPathsTest {

    /** Wide and deep enough that walking it takes far longer than the timeout: 8^8 walks. */
    private static final int LAYERS = 8;

    private static final int WIDTH = 8;

    @Test
    void anUnreachableTargetAnswersWithoutWalkingTheGraph() throws Exception {
        ArchitectureModel model = layeredDeadEnd();
        component(model, "Target");
        GraphQuery graph = graphOf(model);

        assertThat(pathsWithin(graph, "Start", "Target")).isEmpty();
    }

    @Test
    void aTargetBehindTheStartIsNotSearchedForThroughTheStart() throws Exception {
        ArchitectureModel model = layeredDeadEnd();
        component(model, "Target");
        // every dead end leads back to the start, and only a revisit of it could go on to the target
        component(model, "Gate");
        for (int i = 0; i < WIDTH; i++) dependency(model, "L" + (LAYERS - 1) + "N" + i, "Start");
        dependency(model, "Start", "Gate");
        dependency(model, "Gate", "Target");
        GraphQuery graph = graphOf(model);

        assertThat(pathsWithin(graph, "Start", "Target")).containsExactly("Start>Gate>Target");
    }

    @Test
    void aShortPathIsFoundBesideAHugeDeadEnd() throws Exception {
        ArchitectureModel model = layeredDeadEnd();
        component(model, "Side");
        component(model, "Target");
        dependency(model, "Start", "Side");
        dependency(model, "Side", "Target");
        GraphQuery graph = graphOf(model);

        assertThat(pathsWithin(graph, "Start", "Target")).containsExactly("Start>Side>Target");
    }

    @Test
    void returnsExactlyTheSimplePathsWithinTheDepth() throws Exception {
        Random random = new Random(42);
        for (int round = 0; round < 120; round++) {
            int size = 3 + random.nextInt(5);
            boolean[][] edge = new boolean[size][size];
            ArchitectureModel model = new ArchitectureModel("random" + round);
            for (int i = 0; i < size; i++) component(model, "N" + i);
            for (int from = 0; from < size; from++) {
                for (int to = 0; to < size; to++) {
                    if (from != to && random.nextDouble() < 0.4) {
                        edge[from][to] = true;
                        dependency(model, "N" + from, "N" + to);
                    }
                }
            }
            GraphQuery graph = graphOf(model);
            for (int from = 0; from < size; from++) {
                for (int to = 0; to < size; to++) {
                    int depth = 1 + random.nextInt(8);
                    List<String> expected = new ArrayList<>();
                    if (from != to) enumerate(edge, new ArrayList<>(List.of(from)), to, depth, expected);

                    assertThat(ids(graph.paths(GraphNodeId.of("N" + from), GraphNodeId.of("N" + to), depth, 50_000)))
                            .as("round %d: N%d to N%d within %d", round, from, to, depth)
                            .containsExactlyInAnyOrderElementsOf(expected);
                }
            }
        }
    }

    /** The oracle: every simple path, found by plain depth-first search. */
    private static void enumerate(boolean[][] edge, List<Integer> path, int to, int depth, List<String> found) {
        int last = path.getLast();
        if (last == to) {
            found.add(path.stream().map(node -> "N" + node).collect(Collectors.joining(">")));
            return;
        }
        if (path.size() > depth) return;
        IntStream.range(0, edge.length)
                .filter(next -> edge[last][next] && !path.contains(next))
                .forEach(next -> {
                    path.add(next);
                    enumerate(edge, path, to, depth, found);
                    path.removeLast();
                });
    }

    private static List<String> pathsWithin(GraphQuery graph, String from, String to) {
        return assertTimeoutPreemptively(
                Duration.ofSeconds(3), () -> ids(graph.paths(GraphNodeId.of(from), GraphNodeId.of(to), 8, 256)));
    }

    private static List<String> ids(List<GraphQuery.GraphPath> paths) {
        return paths.stream()
                .map(path ->
                        path.nodes().stream().map(node -> node.id().serialize()).collect(Collectors.joining(">")))
                .toList();
    }

    private static ArchitectureModel layeredDeadEnd() {
        ArchitectureModel model = new ArchitectureModel("layers");
        component(model, "Start");
        for (int layer = 0; layer < LAYERS; layer++) {
            for (int i = 0; i < WIDTH; i++) {
                component(model, "L" + layer + "N" + i);
                if (layer == 0) {
                    dependency(model, "Start", "L0N" + i);
                    continue;
                }
                for (int j = 0; j < WIDTH; j++) dependency(model, "L" + (layer - 1) + "N" + j, "L" + layer + "N" + i);
            }
        }
        return model;
    }

    private static void component(ArchitectureModel model, String name) {
        Component component = new Component();
        component.id = ComponentId.of(name);
        component.name = name;
        component.qualifiedName = name;
        component.type = ComponentType.SERVICE;
        model.components.add(component);
    }

    private static void dependency(ArchitectureModel model, String from, String to) {
        Dependency dependency = new Dependency();
        dependency.fromId = ComponentId.of(from);
        dependency.toId = ComponentId.of(to);
        dependency.id = DependencyId.of(dependency.fromId, dependency.toId);
        dependency.kind = "injection";
        dependency.confidence = 1;
        model.dependencies.add(dependency);
    }

    private static GraphQuery graphOf(ArchitectureModel model) throws Exception {
        ModelCache cache = new ModelCache();
        cache.indexInMemory(model);
        return cache.graph();
    }
}
