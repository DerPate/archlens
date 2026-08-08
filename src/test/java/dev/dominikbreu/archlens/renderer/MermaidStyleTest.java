package dev.dominikbreu.archlens.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.ComponentType;
import java.util.EnumSet;
import java.util.List;
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
    void nodeUsesRoleShapeAndEscapesLabel() {
        assertThat(MermaidStyle.node("    ", "a", "My \"Repo\"", MermaidStyle.Role.REPOSITORY))
                .isEqualTo("    a[(\"My 'Repo'\")]\n");
        assertThat(MermaidStyle.node("    ", "b", "Svc", MermaidStyle.Role.SERVICE))
                .isEqualTo("    b(\"Svc\")\n");
        assertThat(MermaidStyle.node("    ", "c", "Api", MermaidStyle.Role.ENTRYPOINT))
                .isEqualTo("    c([\"Api\"])\n");
        assertThat(MermaidStyle.node("    ", "d", "Ext", MermaidStyle.Role.EXTERNAL))
                .isEqualTo("    d[/\"Ext\"\\]\n");
        assertThat(MermaidStyle.node("    ", "e", "Evt", MermaidStyle.Role.EVENT))
                .isEqualTo("    e((\"Evt\"))\n");
    }

    @Test
    void classDefsEmitOnlyUsedRolesInEnumOrder() {
        String out = MermaidStyle.classDefs(List.of(MermaidStyle.Role.SERVICE, MermaidStyle.Role.ENTRYPOINT));
        assertThat(out.lines()).hasSize(2);
        assertThat(out.indexOf("classDef entrypoint")).isLessThan(out.indexOf("classDef service"));
        assertThat(MermaidStyle.classDefs(List.of())).isEmpty();
    }

    @Test
    void legendContainsOneNodePerUsedRole() {
        String out = MermaidStyle.legend(EnumSet.of(MermaidStyle.Role.SERVICE, MermaidStyle.Role.STORE));
        assertThat(out).contains("subgraph legend[\"Legend\"]");
        assertThat(out).contains("legend_service(\"service\")");
        assertThat(out).contains("legend_store[(\"store\")]");
        assertThat(out).contains("class legend_service service");
        assertThat(MermaidStyle.legend(EnumSet.noneOf(MermaidStyle.Role.class))).isEmpty();
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
    void trackerFooterEmitsClassDefsAssignmentsAndLegend() {
        MermaidStyle.Tracker t = new MermaidStyle.Tracker();
        t.tag("n1", MermaidStyle.Role.SERVICE);
        t.tag("n2", MermaidStyle.Role.STORE);
        String footer = t.footer();
        assertThat(footer).contains("classDef service");
        assertThat(footer).contains("classDef store");
        assertThat(footer).contains("class n1 service");
        assertThat(footer).contains("class n2 store");
        assertThat(footer).contains("subgraph legend");
        assertThat(new MermaidStyle.Tracker().footer()).isEmpty();
    }
}
