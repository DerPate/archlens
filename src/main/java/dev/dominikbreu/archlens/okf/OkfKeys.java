package dev.dominikbreu.archlens.okf;

import java.util.Map;

/**
 * ArchLens's frontmatter extension keys. OKF v0.3 reserves the {@code x-} prefix for producers;
 * concepts written before it use {@code archlens_} names, which are still read and are renamed on
 * refresh.
 */
final class OkfKeys {
    /** Prefix of every key ArchLens writes. */
    static final String PREFIX = "x-archlens-";

    /** Question family of the investigation. */
    static final String FAMILY = PREFIX + "family";

    /** Answer status of the compiled result. */
    static final String STATUS = PREFIX + "status";

    /** Semantic identity key of the investigation. */
    static final String SEMANTIC_KEY = PREFIX + "semantic-key";

    /** Indexed project root the evidence came from. */
    static final String PROJECT_PATH = PREFIX + "project-path";

    /** Marks a concept as refreshable by ArchLens. */
    static final String GENERATED = PREFIX + "generated";

    private OkfKeys() {}

    /**
     * Reads an ArchLens key, falling back to its pre-v0.3 name.
     *
     * @param frontmatter parsed frontmatter
     * @param key one of this class's keys
     * @return value, or {@code null} when neither name is present
     */
    static Object read(Map<?, ?> frontmatter, String key) {
        Object value = frontmatter.get(key);
        return value != null ? value : frontmatter.get(legacyName(key));
    }

    /**
     * Maps a key to its pre-v0.3 name.
     *
     * @param key one of this class's keys
     * @return legacy key name
     */
    static String legacyName(String key) {
        return "archlens_" + key.substring(PREFIX.length()).replace('-', '_');
    }
}
