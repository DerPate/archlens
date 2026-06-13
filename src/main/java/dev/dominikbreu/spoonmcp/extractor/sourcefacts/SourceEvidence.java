package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

/** Evidence kind describing how a source fact was extracted from the AST. */
public enum SourceEvidence {
    /** Value taken from a direct type reference in the source. */
    DIRECT_TYPE_REFERENCE,
    /** Value inferred from a field injection annotation (e.g. {@code @Autowired}). */
    FIELD_INJECTION,
    /** Value inferred from a constructor injection parameter. */
    CONSTRUCTOR_INJECTION,
    /** Value inferred from a method injection parameter. */
    METHOD_INJECTION,
    /** Value inferred from a local variable assignment. */
    LOCAL_ASSIGNMENT,
    /** Value inferred from a field assignment. */
    FIELD_ASSIGNMENT,
    /** Value inferred from a method that returns a field. */
    METHOD_RETURNS_FIELD,
    /** Value inferred from a method that returns a parameter. */
    METHOD_RETURNS_PARAMETER,
    /** Value inferred from a method that returns a local variable. */
    METHOD_RETURNS_LOCAL,
    /** Value inferred from a method that returns an invocation result. */
    METHOD_RETURNS_INVOCATION,
    /** Value inferred from a constructor call expression. */
    CONSTRUCTOR_CALL,
    /** Value resolved through a polymorphic implementation lookup. */
    POLYMORPHIC_IMPLEMENTATION,
    /** Value taken directly from an annotation attribute. */
    ANNOTATION_VALUE,
    /** Value taken from a configuration property. */
    CONFIG_VALUE,
    /** Extraction attempted but failed due to missing classpath information. */
    UNRESOLVED_NO_CLASSPATH,
    /** Weak signal inferred from a naming convention. */
    NAMING_HINT
}
