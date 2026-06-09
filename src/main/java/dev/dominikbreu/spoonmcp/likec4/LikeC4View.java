package dev.dominikbreu.spoonmcp.likec4;

import java.util.List;
import java.util.Objects;

/**
 * A LikeC4 static view definition.
 *
 * @param id the view id
 * @param title the view title
 * @param includes the element or wildcard includes for this view
 * @param notes optional notes rendered as comments in the view block
 */
public record LikeC4View(String id, String title, List<String> includes, List<String> notes) {

    /** Validates non-null fields and defensively copies list fields. */
    public LikeC4View {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        includes = List.copyOf(Objects.requireNonNull(includes, "includes"));
        notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
    }
}
