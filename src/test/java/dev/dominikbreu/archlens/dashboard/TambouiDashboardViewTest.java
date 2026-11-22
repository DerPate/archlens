package dev.dominikbreu.archlens.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TambouiDashboardViewTest {
    @Test
    void rendersStatusAndHandlesInput() {
        TambouiDashboardView view =
                new TambouiDashboardView(new DashboardState(), java.util.List.of("index_workspace"));
        view.handle(KeyEvent.ofChar('h'));
        view.handle(KeyEvent.ofChar('i'));
        assertThat(view.command()).isEqualTo("hi");
        Buffer buffer = Buffer.empty(new Rect(0, 0, 80, 20));
        view.render(Frame.forTesting(buffer));
        assertThat(buffer.toAnsiStringTrimmed()).contains("ARCHLENS");
    }

    @ParameterizedTest
    @ValueSource(ints = {40, 80, 89, 90, 140})
    void showsTheResultAtEveryWidth(int width) {
        assertThat(render(width, List.of())).contains("RESULT");
    }

    @ParameterizedTest
    @ValueSource(ints = {40, 80, 140})
    void showsTheCapturedTraversals(int width) {
        assertThat(render(width, List.of("[GraphStep]"))).contains("Traversals").contains("[GraphStep]");
    }

    @ParameterizedTest
    @ValueSource(ints = {80, 140})
    void aLongTraversalDoesNotPushTheOthersOffScreen(int width) {
        String longTrace = "[GraphStep(vertex,[" + "x,".repeat(5000) + "])]";

        assertThat(render(width, List.of(longTrace, "[SECOND]"))).contains("[SECOND]");
    }

    private static String render(int width, List<String> traces) {
        DashboardState state = new DashboardState();
        state.logSystemMessage("ready");
        state.recordEvent(new DashboardEvent("list_apps", "list_apps", traces, 3, "RESULT", null));
        Buffer buffer = Buffer.empty(new Rect(0, 0, width, 24));
        new TambouiDashboardView(state, List.of()).render(Frame.forTesting(buffer));
        return buffer.toAnsiStringTrimmed();
    }
}
