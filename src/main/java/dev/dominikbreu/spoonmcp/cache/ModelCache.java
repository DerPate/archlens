package dev.dominikbreu.spoonmcp.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;

import java.io.File;
import java.io.IOException;

/**
 * Stores and loads the current {@link ArchitectureModel} for MCP tool calls.
 */
public class ModelCache {

    private static final String DEFAULT_CACHE_DIR = ".spoon-mcp-cache";
    private static final String MODEL_FILE = "architecture-model.json";

    private final ObjectMapper mapper;
    private final String cacheDir;
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
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.cacheDir = externalCachePath != null ? externalCachePath : DEFAULT_CACHE_DIR;
    }

    /**
     * Persists the supplied model and marks it as the in-memory current model.
     *
     * @param model architecture model to store
     * @throws IOException if the model cannot be written
     */
    public void store(ArchitectureModel model) throws IOException {
        this.current = model;
        File dir = new File(cacheDir);
        dir.mkdirs();
        mapper.writeValue(new File(dir, MODEL_FILE), model);
    }

    /**
     * Loads the current model from memory or disk.
     *
     * @return cached model, or null when no model has been indexed
     * @throws IOException if the model file cannot be read
     */
    public ArchitectureModel load() throws IOException {
        if (current != null) return current;
        File f = new File(cacheDir, MODEL_FILE);
        if (f.exists()) {
            current = mapper.readValue(f, ArchitectureModel.class);
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
        return current != null ? mapper.writeValueAsString(current) : "{}";
    }

    /**
     * Returns the cache directory path.
     *
     * @return cache directory path
     */
    public String getCacheDir() {
        return cacheDir;
    }
}
