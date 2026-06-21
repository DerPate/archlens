# ArchLens Site Branding

Swap these files to change the site marks without editing Astro components:

- site/public/nav-logo.svg: logo shown in the top navigation.
- site/public/favicon-light.svg: light-stroke favicon asset.
- site/public/favicon-dark.svg: dark-stroke favicon asset.
- site/public/favicon.svg: fallback browser tab icon for clients that ignore media-specific favicons.

The document head maps the light and dark favicon assets with media queries, so check site/src/layouts/BaseLayout.astro if a browser shows the wrong one for its chrome mode.

Use square SVGs when possible. The navbar constrains its mark to 36 by 36 pixels and keeps the image contained, so wider or taller artwork will scale down instead of breaking the header.
