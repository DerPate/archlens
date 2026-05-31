package dev.dominikbreu.spoonmcp.likec4;

import java.util.List;
import java.util.Objects;

public record LikeC4DynamicView(String id, String title, List<LikeC4DynamicStep> steps) {

    public LikeC4DynamicView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
    }
}
