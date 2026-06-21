package dev.dominikbreu.archlens.extractor.sourcefacts;

import dev.dominikbreu.archlens.model.ids.SourceFactId;

/**
 * A source-level field declaration observed on a type.
 *
 * @param id the fact id for this field
 * @param typeId the fact id of the declaring type
 * @param name the simple field name
 * @param fieldType the declared field type name
 * @param location the source location of the field declaration
 */
public record SourceField(
        SourceFactId id, SourceFactId typeId, String name, String fieldType, SourceLocation location) {}
