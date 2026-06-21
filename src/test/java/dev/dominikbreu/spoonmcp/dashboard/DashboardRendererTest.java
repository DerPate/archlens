package dev.dominikbreu.spoonmcp.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardRendererTest {

    @Test
    void render_idleState_showsIdlePlaceholder() {
        DashboardState state = new DashboardState();

        String rendered = DashboardRenderer.render(state, 100);

        assertThat(rendered).contains("idle");
    }

    @Test
    void render_includesSystemLogLines() {
        DashboardState state = new DashboardState();
        state.logSystemMessage("spoon-mcp-server 1.2.0 — standalone dashboard");

        String rendered = DashboardRenderer.render(state, 100);

        assertThat(rendered).contains("spoon-mcp-server 1.2.0");
    }

    @Test
    void render_activeEvent_showsCommandTraversalAndResult() {
        DashboardState state = new DashboardState();
        state.recordEvent(new DashboardEvent(
                "find_entrypoints appId=core",
                "find_entrypoints",
                List.of("[TinkerGraphStep(vertex,[]), HasStep([appId.eq(core)])]"),
                12,
                "GET /core/health",
                null));

        String rendered = DashboardRenderer.render(state, 120);

        assertThat(rendered).contains("find_entrypoints appId=core");
        assertThat(rendered).contains("TinkerGraphStep");
        assertThat(rendered).contains("GET /core/health");
        assertThat(rendered).contains("12ms");
    }

    @Test
    void render_errorEvent_showsErrorText() {
        DashboardState state = new DashboardState();
        state.recordEvent(new DashboardEvent("boom", "boom", List.of(), 1, null, "IllegalStateException: boom"));

        String rendered = DashboardRenderer.render(state, 120);

        assertThat(rendered).contains("ERROR").contains("IllegalStateException: boom");
    }

    @Test
    void render_longResultLine_isTruncatedToPaneWidth() {
        DashboardState state = new DashboardState();
        String longLine = "x".repeat(500);
        state.recordEvent(new DashboardEvent("cmd", "cmd", List.of(), 1, longLine, null));

        String rendered = DashboardRenderer.render(state, 80);

        assertThat(rendered.lines()).noneMatch(line -> line.length() > 80);
    }
}
