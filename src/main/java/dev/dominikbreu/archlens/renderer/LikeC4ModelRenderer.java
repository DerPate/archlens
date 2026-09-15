package dev.dominikbreu.archlens.renderer;

import dev.dominikbreu.archlens.likec4.LikeC4Document;
import dev.dominikbreu.archlens.view.ArchitectureViewProjection;

/** Public façade for rendering LikeC4 documents and architecture projections. */
public final class LikeC4ModelRenderer {

    private final LikeC4TemplateAdapter adapter = new LikeC4TemplateAdapter();

    /** Creates a renderer with default settings. */
    public LikeC4ModelRenderer() {}

    /**
     * Renders the given LikeC4 document as a DSL string.
     *
     * @param document the document to render
     * @return the LikeC4 DSL text
     */
    public String render(LikeC4Document document) {
        return adapter.render(document);
    }

    /**
     * Renders the given architecture projection as a LikeC4 DSL string.
     *
     * @param projection the projection to render
     * @return the LikeC4 DSL text
     */
    public String render(ArchitectureViewProjection projection) {
        return adapter.render(projection);
    }
}
