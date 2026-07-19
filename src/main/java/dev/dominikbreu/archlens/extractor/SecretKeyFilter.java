package dev.dominikbreu.archlens.extractor;

import java.util.List;
import java.util.Locale;

/**
 * Shared secret-key heuristic: a property whose key contains any of these markers is dropped
 * entirely (never projected as key or value) by both {@link ConfigPropertyResolver} and
 * {@link PersistenceTopologyExtractor}.
 */
final class SecretKeyFilter {

    private static final List<String> SECRET_MARKERS =
            List.of("password", "username", "credential", "secret", "token", "apikey", "api-key", "private-key");

    private SecretKeyFilter() {}

    /**
     * Checks whether a dotted config key looks secret-like.
     *
     * @param key the property key
     * @return {@code true} if the key contains any known secret marker
     */
    static boolean isSecretKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return SECRET_MARKERS.stream().anyMatch(lower::contains);
    }
}
