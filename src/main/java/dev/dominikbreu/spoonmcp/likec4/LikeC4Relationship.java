package dev.dominikbreu.spoonmcp.likec4;

import java.util.Map;
import java.util.Objects;

public record LikeC4Relationship(
        String sourceId, String targetId, String title, String sourceLabel, Map<String, Object> metadata) {

    public LikeC4Relationship {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(sourceLabel, "sourceLabel");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }
}
