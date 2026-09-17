package dev.dominikbreu.archlens.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyEvent;
import org.junit.jupiter.api.Test;

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
}
