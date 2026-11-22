package dev.dominikbreu.archlens.cache;

import dev.dominikbreu.archlens.model.ArchitectureModel;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Stores and loads the current workspace as a GraphSON file.
 *
 * <p>The JSON model file is no longer written. The graph is persisted as
 * {@code architecture-graph.v1.graphson} and loaded back on demand. All tool
 * calls go through {@link #graph()} (GraphQuery) only.
 */
public class ModelCache {

    private static final String GRAPH_FILE = "architecture-graph.v1.graphson";
    private static final String WORKSPACES_DIR = "workspaces";
    private static final String ACTIVE_WORKSPACE_FILE = "active-workspace.txt";

    /** The only shape {@link #workspaceKey} produces: a hash, then one file-name-safe segment. */
    private static final Pattern WORKSPACE_KEY = Pattern.compile("[0-9a-f]{16}-[A-Za-z0-9._-]+");

    private final String cacheDir;
    private final GraphStore store = new GraphStore();

    /** The graph a call selected through {@link #inWorkspace}, visible only inside that call. */
    private final ScopedValue<GraphStore> selected = ScopedValue.newInstance();

    /** Key of the workspace held in {@link #store}, or {@code null}. */
    private String loadedKey;

    /** Key of the workspace held in {@link #otherStore}, or {@code null}. */
    private String otherKey;

    /** The last explicitly selected non-active workspace, kept to avoid parsing it on every call. */
    private GraphStore otherStore;

    private ArchitectureModel current;
    private boolean inMemoryOnly = false;

    /** Creates a cache in the user's cache directory for the current working directory. */
    public ModelCache() {
        this(null);
    }

    /**
     * Creates a cache with an optional custom directory.
     *
     * @param externalCachePath cache directory path, or null to use the default
     */
    public ModelCache(String externalCachePath) {
        this.cacheDir = externalCachePath != null
                ? externalCachePath
                : CacheLocation.current().toString();
    }

    /**
     * Projects the model into the in-memory graph and writes the GraphSON file.
     *
     * @param model architecture model to store
     * @return workspace id, usable with {@link #inWorkspace}
     * @throws IOException if the graph cannot be written
     */
    public String store(ArchitectureModel model) throws IOException {
        this.current = model;
        this.inMemoryOnly = false;
        loadedKey = null;
        otherKey = null;
        otherStore = null;
        store.clear();
        new GraphProjector().project(model, store);
        File dir = workspaceDir(model);
        createPrivateDirectories(dir.toPath());
        try {
            Files.writeString(new File(dir, GRAPH_FILE).toPath(), store.serializeGraphSON(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IOException("Failed to serialize graph: " + e.getMessage(), e);
        }
        String key = workspaceKey(model);
        writeActiveWorkspace(key);
        loadedKey = key;
        return key;
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
        loadedKey = null;
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
        loadedKey = null;
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
     * <p>Inside {@link #inWorkspace} this is the selected workspace. Otherwise it is the active one:
     * if the in-memory store is empty, loads the graph from the active workspace's GraphSON
     * file. Tools should always access the workspace through this method.</p>
     *
     * @return graph query over the current architecture model
     * @throws IOException if the GraphSON file cannot be read
     */
    public GraphQuery graph() throws IOException {
        if (selected.isBound()) {
            return new GraphQuery(selected.get());
        }
        if (store.isEmpty() && !inMemoryOnly) {
            String key = readActiveWorkspace();
            if (key != null && load(key, store)) {
                loadedKey = key;
            }
        }
        return new GraphQuery(store);
    }

    /**
     * Runs an action with {@link #graph()} bound to one indexed workspace. The active workspace is
     * not changed.
     *
     * @param workspaceId id returned by {@link #store}
     * @param action action to run; must not return {@code null}
     * @param <T> action result type
     * @return the action's result, or empty when no such workspace is cached for this directory
     * @throws IOException if the workspace cannot be read
     */
    public <T> Optional<T> inWorkspace(String workspaceId, Supplier<T> action) throws IOException {
        // The id reaches the file system, so only keys this class could have produced are accepted.
        if (!WORKSPACE_KEY.matcher(workspaceId).matches()) {
            return Optional.empty();
        }
        GraphStore target;
        if (workspaceId.equals(loadedKey)) {
            target = store;
        } else if (workspaceId.equals(otherKey)) {
            target = otherStore;
        } else {
            target = new GraphStore();
            if (!load(workspaceId, target)) {
                return Optional.empty();
            }
            otherKey = workspaceId;
            otherStore = target;
        }
        return Optional.of(ScopedValue.where(selected, target).call(action::get));
    }

    /** Loads a workspace's graph file into a store, returning whether the file exists. */
    private boolean load(String key, GraphStore target) throws IOException {
        File file = new File(new File(new File(cacheDir, WORKSPACES_DIR), key), GRAPH_FILE);
        if (!file.exists()) {
            return false;
        }
        try {
            target.loadFrom(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            throw new IOException("Failed to load graph: " + e.getMessage(), e);
        }
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
        createPrivateDirectories(Path.of(cacheDir));
        Files.writeString(activeWorkspaceFile().toPath(), key, StandardCharsets.UTF_8);
    }

    private String readActiveWorkspace() throws IOException {
        File active = activeWorkspaceFile();
        if (!active.exists()) {
            return null;
        }
        String key = Files.readString(active.toPath(), StandardCharsets.UTF_8).trim();
        return WORKSPACE_KEY.matcher(key).matches() ? key : null;
    }

    private File activeWorkspaceFile() {
        return new File(cacheDir, ACTIVE_WORKSPACE_FILE);
    }

    /** Creates missing directories readable only by the owner, as the cache describes private source code. */
    private static void createPrivateDirectories(Path directory) throws IOException {
        if (Files.isDirectory(directory)) return;
        try {
            if (directory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.createDirectories(
                        directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            } else {
                Files.createDirectories(directory);
            }
        } catch (IOException error) {
            throw new IOException(
                    "Failed to create cache directory " + directory
                            + " (set ARCHLENS_CACHE_DIR to a writable absolute path)",
                    error);
        }
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
