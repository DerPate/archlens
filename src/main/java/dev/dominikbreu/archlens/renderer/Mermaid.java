package dev.dominikbreu.archlens.renderer;

import java.util.regex.Pattern;

/** Shared escaping for user text embedded in Mermaid node and edge labels. */
final class Mermaid {

    /**
     * Matches Mermaid's own C4-diagram keywords ({@code C4Context}, {@code C4Container},
     * {@code C4Component}, {@code C4Dynamic}, {@code C4Deployment}) wherever they occur as a
     * substring, e.g. inside a generated type name like {@code LikeC4DynamicStep}. Mermaid's
     * diagram-type detection scans the whole document text for these keywords, so a flowchart
     * label merely containing one is enough to make Mermaid misparse the document as a C4
     * diagram. Splitting the keyword defeats that detection without altering any other text.
     */
    static final Pattern C4_KEYWORD_BOUNDARY =
            Pattern.compile("(?<=C4)(?=Context|Container|Component|Dynamic|Deployment)");

    private Mermaid() {}

    /**
     * Escapes a string for use inside a Mermaid label. Mermaid labels are double-quoted, so a
     * literal {@code "} is swapped to {@code '}; the edge-label delimiter {@code |} is swapped to
     * {@code -}; newlines become the literal {@code \n} sequence so a label stays on one line; and
     * a Mermaid C4-keyword substring (see {@link #C4_KEYWORD_BOUNDARY}) is split with a space so it
     * can no longer be mistaken for a real C4 diagram directive.
     *
     * @param s raw label text (may be null)
     * @return escaped label text, or empty string when {@code s} is null
     */
    static String escapeLabel(String s) {
        if (s == null) return "";
        String escaped = s.replace("\"", "'").replace("|", "-").replace("\n", "\\n");
        return C4_KEYWORD_BOUNDARY.matcher(escaped).replaceAll(" ");
    }
}
