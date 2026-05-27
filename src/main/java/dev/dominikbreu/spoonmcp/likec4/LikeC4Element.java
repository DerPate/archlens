package dev.dominikbreu.spoonmcp.likec4;

import java.util.Map;
import java.util.Objects;

public record LikeC4Element(String id, String kind, String title, String sourceId, Map<String, Object> metadata) {

    public LikeC4Element {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(sourceId, "sourceId");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }
}
