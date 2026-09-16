package dev.dominikbreu.archlens.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.junit.jupiter.api.Test;

class TambouiCompatibilityTest {

    @Test
    void rendersWrappedParagraphIntoBuffer() {
        Buffer buffer = Buffer.empty(new Rect(0, 0, 8, 2));

        Paragraph.builder()
                .text("alpha beta gamma")
                .overflow(Overflow.WRAP_WORD)
                .build()
                .render(new Rect(0, 0, 8, 2), buffer);

        assertThat(buffer.get(0, 0).symbol()).isEqualTo("a");
        assertThat(buffer.get(0, 1).symbol()).isEqualTo("b");
    }
}
