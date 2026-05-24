package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.extractor.ExtractorTestBase;
import org.junit.jupiter.api.Test;

class SourceFactIndexBuilderTest extends ExtractorTestBase {

    @Test
    void sourceFactTypesHaveStableIdsAndLocations() {
        SourceLocation location = new SourceLocation("Example.java", 7);
        SourceType type = new SourceType(
                "type:com.example.Example",
                "com.example.Example",
                "Example",
                "com.example",
                false,
                false,
                location);

        assertThat(type.id()).isEqualTo("type:com.example.Example");
        assertThat(type.qualifiedName()).isEqualTo("com.example.Example");
        assertThat(type.location()).isSameAs(location);
    }
}
