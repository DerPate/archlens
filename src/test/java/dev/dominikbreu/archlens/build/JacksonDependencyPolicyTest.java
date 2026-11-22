package dev.dominikbreu.archlens.build;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Keeps Jackson 2 from returning to the classpath through a transitive dependency. */
class JacksonDependencyPolicyTest {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "com/fasterxml/jackson/databind/ObjectMapper.class",
                "com/fasterxml/jackson/core/JsonFactory.class"
            })
    void jackson2RuntimeIsNotOnTheClasspath(String classFile) {
        assertThat(getClass().getClassLoader().getResource(classFile))
                .as("Jackson 2 is unused; exclude it from whichever dependency brought it in")
                .isNull();
    }
}
