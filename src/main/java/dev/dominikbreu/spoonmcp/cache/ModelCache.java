package dev.dominikbreu.spoonmcp.cache;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores and loads the current {@link ArchitectureModel} for MCP tool calls.
 */
public class ModelCache {

    private static final String DEFAULT_CACHE_DIR = ".spoon-mcp-cache";
    private static final String MODEL_FILE = "architecture-model.json";
    private static final String WORKSPACES_DIR = "workspaces";
    private static final String ACTIVE_WORKSPACE_FILE = "active-workspace.txt";
    private static final String BACKEND_PROPERTY = "spoonmcp.cache.backend";
    private static final String BACKEND_ENV = "SPOON_MCP_CACHE_BACKEND";

    private final JsonMapper mapper;
    private final String cacheDir;
    private final CacheBackend backend;
    private final ArchitectureGraph graph;
    private ArchitectureModel current;

    /** Creates a cache using the default local cache directory. */
    public ModelCache() {
        this(null);
    }

    /**
     * Creates a cache with an optional custom directory.
     *
     * @param externalCachePath cache directory path, or null to use the default
     */
    public ModelCache(String externalCachePath) {
        this(externalCachePath, configuredBackend());
    }

    /**
     * Creates a cache with an explicit backend. This constructor is primarily
     * useful for tests and embedded use.
     *
     * @param externalCachePath cache directory path, or null to use the default
     * @param backend cache backend mode
     */
    public ModelCache(String externalCachePath, CacheBackend backend) {
        this.mapper =
                JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        this.cacheDir = externalCachePath != null ? externalCachePath : DEFAULT_CACHE_DIR;
        this.backend = backend != null ? backend : CacheBackend.JSON;
        this.graph = new ArchitectureGraph();
    }

    /**
     * Persists the supplied model and marks it as the in-memory current model.
     *
     * @param model architecture model to store
     * @throws IOException if the model cannot be written
     */
    public void store(ArchitectureModel model) throws IOException {
        this.current = model;
        File dir = workspaceDir(model);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create cache directory: " + dir.getAbsolutePath());
        }
        mapper.writeValue(new File(dir, MODEL_FILE), model);
        writeActiveWorkspace(workspaceKey(model));
        if (backend == CacheBackend.GRAPH) {
            graph.rebuild(model);
        }
    }

    /**
     * Clears the active in-memory and on-disk workspace pointer.
     *
     * <p>Previously stored workspace snapshots remain available on disk, but
     * subsequent tool calls will not accidentally operate on an older project
     * after a new index attempt fails.</p>
     *
     * @throws IOException if the active pointer cannot be removed
     */
    public void clearActive() throws IOException {
        this.current = null;
        graph.rebuild(null);
        Files.deleteIfExists(activeWorkspaceFile().toPath());
    }

    /**
     * Loads the current model from memory or disk.
     *
     * @return cached model, or null when no model has been indexed
     * @throws IOException if the model file cannot be read
     */
    public ArchitectureModel load() throws IOException {
        if (current != null) return current;
        String key = readActiveWorkspace();
        if (key == null) return null;
        File f = new File(new File(new File(cacheDir, WORKSPACES_DIR), key), MODEL_FILE);
        if (f.exists()) {
            current = mapper.readValue(f, ArchitectureModel.class);
            if (backend == CacheBackend.GRAPH) {
                graph.rebuild(current);
            }
        }
        return current;
    }

    /**
     * Returns the in-memory model without reading from disk.
     *
     * @return current model, or null if none has been loaded or stored
     */
    public ArchitectureModel getCurrent() {
        return current;
    }

    /**
     * Serializes the current model as JSON.
     *
     * @return model JSON or an empty JSON object when no model exists
     * @throws IOException if serialization fails
     */
    public String exportJson() throws IOException {
        if (current != null) {
            return mapper.writeValueAsString(current);
        } else {
            return "{}";
        }
    }

    /**
     * Returns a graph projection for traversal-oriented tools.
     *
     * <p>When the graph backend is enabled, the projection is maintained during
     * store/load. With the JSON backend, it is built lazily from the current
     * model so graph tools remain available without changing the durable cache
     * format.</p>
     *
     * @return graph projection of the current architecture model
     * @throws IOException if the current model must be loaded and cannot be read
     */
    public ArchitectureGraph graph() throws IOException {
        ArchitectureModel model = load();
        if (model != null && graph.isEmpty()) {
            graph.rebuild(model);
        }
        return graph;
    }

    /**
     * Returns the configured cache backend.
     *
     * @return cache backend
     */
    public CacheBackend getBackend() {
        return backend;
    }

    /**
     * Returns the cache directory path.
     *
     * @return cache directory path
     */
    public String getCacheDir() {
        return cacheDir;
    }

    private File workspaceDir(ArchitectureModel model) {
        return new File(new File(cacheDir, WORKSPACES_DIR), workspaceKey(model));
    }

    private String workspaceKey(ArchitectureModel model) {
        String workspacePath;
        if (model != null && model.workspacePath != null) {
            workspacePath = model.workspacePath;
        } else {
            workspacePath = "unknown";
        }
        String hash = sha256(workspacePath).substring(0, 16);
        String suffix = workspacePath.replace('\\', '/');
        int slash = suffix.lastIndexOf('/');
        if (slash >= 0 && slash < suffix.length() - 1) {
            suffix = suffix.substring(slash + 1);
        }
        suffix = suffix.replaceAll("[^A-Za-z0-9._-]+", "-");
        if (suffix.isBlank()) {
            suffix = "workspace";
        }
        return hash + "-" + suffix;
    }

    private void writeActiveWorkspace(String key) throws IOException {
        File dir = new File(cacheDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create cache directory: " + dir.getAbsolutePath());
        }
        Files.writeString(activeWorkspaceFile().toPath(), key, StandardCharsets.UTF_8);
    }

    private String readActiveWorkspace() throws IOException {
        File active = activeWorkspaceFile();
        if (!active.exists()) {
            return null;
        }
        String key = Files.readString(active.toPath(), StandardCharsets.UTF_8).trim();
        if (key.isBlank()) {
            return null;
        } else {
            return key;
        }
    }

    private File activeWorkspaceFile() {
        return new File(cacheDir, ACTIVE_WORKSPACE_FILE);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static CacheBackend configuredBackend() {
        String value = System.getProperty(BACKEND_PROPERTY);
        if (value == null || value.isBlank()) {
            value = System.getenv(BACKEND_ENV);
        }
        return CacheBackend.from(value);
    }

    /** Supported cache backend modes. */
    public enum CacheBackend {
        /** Durable JSON snapshot only. Graph tools build a transient projection. */
        JSON,
        /** Durable JSON snapshot with eagerly maintained embedded graph projection. */
        GRAPH;

        static CacheBackend from(String value) {
            if (value == null || value.isBlank()) {
                return JSON;
            }
            if ("graph".equalsIgnoreCase(value.trim())) {
                return GRAPH;
            } else {
                return JSON;
            }
        }
    }
}
