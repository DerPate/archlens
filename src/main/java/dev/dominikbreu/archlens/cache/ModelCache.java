package dev.dominikbreu.archlens.cache;

import dev.dominikbreu.archlens.model.ArchitectureModel;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stores and loads the current workspace as a GraphSON file.
 *
 * <p>The JSON model file is no longer written. The graph is persisted as
 * {@code architecture-graph.v1.graphson} and loaded back on demand. All tool
 * calls go through {@link #graph()} (GraphQuery) only.
 */
public class ModelCache {

    private static final String DEFAULT_CACHE_DIR = ".archlens-cache";
    private static final String GRAPH_FILE = "architecture-graph.v1.graphson";
    private static final String WORKSPACES_DIR = "workspaces";
    private static final String ACTIVE_WORKSPACE_FILE = "active-workspace.txt";

    private final String cacheDir;
    private final GraphStore store = new GraphStore();
    private ArchitectureModel current;
    private boolean inMemoryOnly = false;

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
        this.cacheDir = externalCachePath != null ? externalCachePath : DEFAULT_CACHE_DIR;
    }

    /**
     * Projects the model into the in-memory graph and writes the GraphSON file.
     *
     * @param model architecture model to store
     * @throws IOException if the graph cannot be written
     */
    public void store(ArchitectureModel model) throws IOException {
        this.current = model;
        this.inMemoryOnly = false;
        store.clear();
        new GraphProjector().project(model, store);
        File dir = workspaceDir(model);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create cache directory: " + dir.getAbsolutePath());
        }
        try {
            Files.writeString(
                    new File(dir, GRAPH_FILE).toPath(),
                    store.serializeGraphSON(),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IOException("Failed to serialize graph: " + e.getMessage(), e);
        }
        writeActiveWorkspace(workspaceKey(model));
    }

    /**
     * Projects the model into the in-memory graph without writing to disk.
     *
     * <p>Use this in tests to provide a workspace without touching the filesystem.</p>
     *
     * @param model architecture model to index
     */
    public void indexInMemory(ArchitectureModel model) {
        this.current = model;
        this.inMemoryOnly = true;
        store.clear();
        if (model != null) new GraphProjector().project(model, store);
    }

    /**
     * Clears the active in-memory and on-disk workspace pointer.
     *
     * @throws IOException if the active pointer cannot be removed
     */
    public void clearActive() throws IOException {
        this.current = null;
        this.inMemoryOnly = false;
        store.clear();
        Files.deleteIfExists(activeWorkspaceFile().toPath());
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
     * Returns a read-only graph query handle.
     *
     * <p>If the in-memory store is empty, loads the graph from the active workspace's GraphSON
     * file. Tools should always access the workspace through this method.</p>
     *
     * @return graph query over the current architecture model
     * @throws IOException if the GraphSON file cannot be read
     */
    public GraphQuery graph() throws IOException {
        if (store.isEmpty() && !inMemoryOnly) {
            String key = readActiveWorkspace();
            if (key != null) {
                File f = new File(new File(new File(cacheDir, WORKSPACES_DIR), key), GRAPH_FILE);
                if (f.exists()) {
                    try {
                        store.loadFrom(Files.readString(f.toPath(), StandardCharsets.UTF_8));
                    } catch (Exception e) {
                        throw new IOException("Failed to load graph: " + e.getMessage(), e);
                    }
                }
            }
        }
        return new GraphQuery(store);
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
}
