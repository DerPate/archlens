package dev.dominikbreu.archlens.renderer.template;

import io.jstach.jstache.JStache;
import io.jstach.jstache.JStacheConfig;
import io.jstach.jstache.JStacheType;

/**
 * Typed presentation model for one shaped Mermaid node line.
 *
 * @param indent leading whitespace
 * @param id Mermaid-safe node id
 * @param open opening shape token
 * @param label escaped node label
 * @param close closing shape token
 */
@JStache(path = "templates/mermaid/node.mustache")
@JStacheConfig(type = JStacheType.STACHE)
public record MermaidNodeTemplate(String indent, String id, String open, String label, String close) {}
