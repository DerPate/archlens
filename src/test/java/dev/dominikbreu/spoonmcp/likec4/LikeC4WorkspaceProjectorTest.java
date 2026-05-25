package dev.dominikbreu.spoonmcp.likec4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class LikeC4WorkspaceProjectorTest {

    @Test
    void projectsWorkspaceDocumentWithSystemComponentsAndStandardViews() throws Exception {
        ModelCache cache = indexFixtureProject("state-handoff");
        ArchitectureModel model = cache.load();
        AppEntry app = model.applications.getFirst();

        LikeC4Document document = new LikeC4WorkspaceProjector().projectWorkspace(cache.graph(), model, app, 12);

        assertTrue(
                document.elementKinds().contains("system"),
                document.elementKinds().toString());
        assertTrue(
                document.elementKinds().contains("component"),
                document.elementKinds().toString());

        LikeC4Element system = document.elements().stream()
                .filter(element -> "system".equals(element.kind()))
                .findFirst()
                .orElseThrow();
        assertEquals(app.id, system.id());
        assertEquals(app.name, system.title());
        assertEquals(app.id, system.sourceId());

        List<LikeC4Element> components = document.elements().stream()
                .filter(element -> "component".equals(element.kind()))
                .toList();
        assertFalse(components.isEmpty(), "expected projected component elements");
        assertTrue(
                components.stream().allMatch(element -> element.id().startsWith("comp:")),
                "expected graph component ids as document ids: " + components);

        Set<String> viewIds = document.views().stream().map(LikeC4View::id).collect(Collectors.toSet());
        assertEquals(Set.of("context", "container", "component"), viewIds);

        LikeC4View context = view(document, "context");
        LikeC4View container = view(document, "container");
        LikeC4View component = view(document, "component");

        assertEquals(List.of(app.id), context.includes());
        assertTrue(container.includes().contains(app.id), container.includes().toString());
        assertTrue(
                container.includes().containsAll(component.includes()),
                container.includes().toString());
        assertEquals(
                components.stream().map(LikeC4Element::id).collect(Collectors.toSet()),
                Set.copyOf(component.includes()));
    }

    @Test
    void relationshipEndpointsReferToDocumentElementsWhenRelationshipsExist() throws Exception {
        ModelCache cache = indexFixtureProject("state-handoff");
        ArchitectureModel model = cache.load();
        AppEntry app = model.applications.getFirst();

        LikeC4Document document = new LikeC4WorkspaceProjector().projectWorkspace(cache.graph(), model, app, 12);

        assertFalse(document.relationships().isEmpty(), "state-handoff fixture should project relationships");
        Set<String> elementIds =
                document.elements().stream().map(LikeC4Element::id).collect(Collectors.toSet());

        for (LikeC4Relationship relationship : document.relationships()) {
            assertTrue(elementIds.contains(relationship.sourceId()), "missing source " + relationship);
            assertTrue(elementIds.contains(relationship.targetId()), "missing target " + relationship);
        }
    }

    private static LikeC4View view(LikeC4Document document, String id) {
        LikeC4View view = document.views().stream()
                .filter(candidate -> id.equals(candidate.id()))
                .findFirst()
                .orElse(null);
        assertNotNull(view, "missing view " + id);
        return view;
    }

    private static ModelCache indexFixtureProject(String name) throws Exception {
        Class<?> fixtures = Class.forName("dev.dominikbreu.spoonmcp.mcp.tools.ToolTestFixtures");
        Method method = fixtures.getDeclaredMethod("indexFixtureProject", String.class);
        method.setAccessible(true);
        return (ModelCache) method.invoke(null, name);
    }
}
