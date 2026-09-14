package dev.dominikbreu.archlens.renderer.template;

import io.jstach.jstache.JStache;
import io.jstach.jstache.JStacheConfig;
import io.jstach.jstache.JStacheType;

/** Empty model for the shared Mermaid theme directive template. */
@JStache(path = "templates/mermaid/header.mustache")
@JStacheConfig(type = JStacheType.STACHE)
public record MermaidHeaderTemplate() {}
