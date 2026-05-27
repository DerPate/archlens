package dev.dominikbreu.spoonmcp.likec4;

import java.util.List;
import java.util.Objects;

public record LikeC4View(String id, String title, List<String> includes, List<String> notes) {

    public LikeC4View {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        includes = List.copyOf(Objects.requireNonNull(includes, "includes"));
        notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
    }
}
