package dev.dominikbreu.spoonmcp.likec4;

import java.util.Objects;

public record LikeC4DynamicStep(String sourceId, String targetId, String title) {

    public LikeC4DynamicStep {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(title, "title");
    }
}
