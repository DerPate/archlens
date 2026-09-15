package dev.dominikbreu.archlens.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.ComponentType;
import org.junit.jupiter.api.Test;

class MermaidStyleTest {

    @Test
    void headerIsSingleInitDirectiveLine() {
        String h = MermaidStyle.header();
        assertThat(h).startsWith("%%{init:").endsWith("%%\n");
        assertThat(h.lines()).hasSize(1);
        assertThat(h).contains("\"theme\": \"base\"");
    }

    @Test
    void roleForMapsComponentTypes() {
        assertThat(MermaidStyle.roleFor(ComponentType.REST_RESOURCE)).isEqualTo(MermaidStyle.Role.ENTRYPOINT);
        assertThat(MermaidStyle.roleFor(ComponentType.EJB_STATELESS)).isEqualTo(MermaidStyle.Role.SERVICE);
        assertThat(MermaidStyle.roleFor(ComponentType.MESSAGE_DRIVEN_BEAN)).isEqualTo(MermaidStyle.Role.MESSAGING);
        assertThat(MermaidStyle.roleFor(ComponentType.CDI_EVENT_CONSUMER)).isEqualTo(MermaidStyle.Role.EVENT);
        assertThat(MermaidStyle.roleFor(ComponentType.UTILITY)).isEqualTo(MermaidStyle.Role.COMPONENT);
        assertThat(MermaidStyle.roleFor(null)).isEqualTo(MermaidStyle.Role.COMPONENT);
    }

    @Test
    void roleForExternalKindMapsBrokerAndDatabase() {
        assertThat(MermaidStyle.roleForExternalKind("MESSAGE_BROKER")).isEqualTo(MermaidStyle.Role.MESSAGING);
        assertThat(MermaidStyle.roleForExternalKind("message_broker")).isEqualTo(MermaidStyle.Role.MESSAGING);
        assertThat(MermaidStyle.roleForExternalKind("DATABASE")).isEqualTo(MermaidStyle.Role.STORE);
        assertThat(MermaidStyle.roleForExternalKind("REST_API")).isEqualTo(MermaidStyle.Role.EXTERNAL);
        assertThat(MermaidStyle.roleForExternalKind(null)).isEqualTo(MermaidStyle.Role.EXTERNAL);
    }

    @Test
    void isAsyncKindDetectsMessagingKinds() {
        assertThat(MermaidStyle.isAsyncKind("messaging")).isTrue();
        assertThat(MermaidStyle.isAsyncKind("jms")).isTrue();
        assertThat(MermaidStyle.isAsyncKind("kafka")).isTrue();
        assertThat(MermaidStyle.isAsyncKind("cdi-event")).isTrue();
        assertThat(MermaidStyle.isAsyncKind("injection")).isFalse();
        assertThat(MermaidStyle.isAsyncKind(null)).isFalse();
    }

    @Test
    void nidSanitizesNonAlphanumerics() {
        assertThat(MermaidStyle.nid("app:orders/x")).isEqualTo("app_orders_x");
        assertThat(MermaidStyle.nid(null)).isEqualTo("_");
    }

    @Test
    void nidSplitsMermaidC4KeywordsButLeavesOtherDigitCapitalNamesAlone() {
        assertThat(MermaidStyle.nid("dev.example.likec4.LikeC4DynamicStep"))
                .isEqualTo("dev_example_likec4_LikeC4_DynamicStep");
        assertThat(MermaidStyle.nid("dev.example.likec4.LikeC4ContainerView"))
                .isEqualTo("dev_example_likec4_LikeC4_ContainerView");
        assertThat(MermaidStyle.nid("dev.example.Base64Encoder")).isEqualTo("dev_example_Base64Encoder");
        assertThat(MermaidStyle.nid("dev.example.OAuth2Client")).isEqualTo("dev_example_OAuth2Client");
    }
}
