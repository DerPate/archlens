package dev.dominikbreu.spoonmcp.model.ids;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed representation of a shared-state field access on a {@code FieldAccess} record.
 *
 * <ul>
 *   <li>{@link Own} — the accessing component reads/writes its own field. No external owner.
 *   <li>{@link CrossComponent} — the accessing component reads a field owned by another
 *       injected component (detected via getter return analysis). The owner is always
 *       non-null, making it impossible to build an ownerless cross-component reference.
 * </ul>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "bindingKind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FieldBinding.Own.class, name = "own"),
    @JsonSubTypes.Type(value = FieldBinding.CrossComponent.class, name = "cross")
})
public sealed interface FieldBinding permits FieldBinding.Own, FieldBinding.CrossComponent {

    /**
     * Returns the simple name of the accessed field.
     *
     * @return the field name
     */
    String fieldName();

    /**
     * Binding where the accessing component reads or writes its own field.
     *
     * @param fieldName the simple name of the field
     */
    record Own(String fieldName) implements FieldBinding {}

    /**
     * Binding where the accessing component reads a field owned by a different injected component.
     *
     * @param ref reference to the owning component and field name
     */
    record CrossComponent(FieldRef ref) implements FieldBinding {
        @Override
        public String fieldName() {
            return ref.fieldName();
        }
    }
}
