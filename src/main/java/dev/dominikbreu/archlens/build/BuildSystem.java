package dev.dominikbreu.archlens.build;

/** Detected build system for a Java project. */
public enum BuildSystem {
    /** Apache Maven. */
    MAVEN,
    /** Gradle with Groovy DSL ({@code build.gradle}). */
    GRADLE_GROOVY,
    /** Gradle with Kotlin DSL ({@code build.gradle.kts}). */
    GRADLE_KOTLIN,
    /** Both Maven and Gradle descriptors present in the same project. */
    MIXED,
    /** No recognised build system detected. */
    UNKNOWN
}
