package dev.dominikbreu.archlens.renderer;

/** Shared escaping for user text embedded in Mermaid node and edge labels. */
final class Mermaid {

    private Mermaid() {}

    /**
     * Escapes a string for use inside a Mermaid label. Mermaid labels are double-quoted, so a
     * literal {@code "} is swapped to {@code '}; the edge-label delimiter {@code |} is swapped to
     * {@code -}; and newlines become the literal {@code \n} sequence so a label stays on one line.
     *
     * @param s raw label text (may be null)
     * @return escaped label text, or empty string when {@code s} is null
     */
    static String escapeLabel(String s) {
        if (s == null) return "";
        return s.replace("\"", "'").replace("|", "-").replace("\n", "\\n");
    }
}
