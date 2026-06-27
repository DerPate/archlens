# Astro v7 Migration Design

## Goal

Upgrade the documentation website in `site/` from Astro v6 to Astro v7 while preserving its current rendering and adding the public website URL to the repository README.

## Changes

- Upgrade the `astro` development dependency to the current Astro v7 release and regenerate `site/package-lock.json` with npm.
- Set `compressHTML: true` in `site/astro.config.mjs` to retain Astro v6's HTML-aware whitespace behavior instead of adopting Astro v7's JSX-style whitespace compression.
- Make no Markdown-pipeline changes because the site contains no Markdown or MDX content and uses no remark or rehype plugins.
- Make no routing changes because the site has no `src/fetch.ts` or `src/fetch.js` file and no experimental routing configuration.
- Add a visible link to `https://archlens.dominikbreu.dev/` near the top of `README.md`.

## Compatibility Review

The site uses plain `.astro` components, static pages, one external browser dependency, and no Astro integrations. The Astro v7 migration guide therefore identifies only two relevant checks:

1. The Rust compiler's stricter HTML parsing must accept every template.
2. The generated site must retain its expected layout and behavior.

The production build will validate template compilation. Existing Playwright tests will validate browser-visible behavior. The explicit `compressHTML: true` setting preserves legacy whitespace semantics.

## Verification

Run from `site/`:

```sh
npm run build
npm test
```

Inspect the dependency tree or lockfile to confirm Astro v7 is installed. If tests reveal a migration-specific regression, address it narrowly and rerun both commands.

## Out of Scope

- Redesigning the website or changing its content.
- Adding deployment automation.
- Refactoring unrelated Java or website code.
