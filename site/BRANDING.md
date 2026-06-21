# ArchLens Site Branding

Swap these files to change the site marks without editing Astro components:

- site/public/wordmark.svg: hero wordmark shown above the main headline.
- site/public/nav-logo-light.svg: light-mode navbar logo asset.
- site/public/nav-logo-dark.svg: dark-mode navbar logo asset.
- site/public/nav-logo.svg: fallback navbar logo for clients that ignore media-specific sources.
- site/public/favicon-light.svg: light-stroke favicon asset.
- site/public/favicon-dark.svg: dark-stroke favicon asset.
- site/public/favicon.svg: fallback browser tab icon for clients that ignore media-specific favicons.

The document head maps favicon assets with media queries. The navbar mark uses a picture element in site/src/components/LogoMark.astro with matching media-specific sources. The hero wordmark stays fixed because the page background is always dark.

Use square SVGs for icons when possible. The navbar constrains its mark to 36 by 36 pixels and keeps the image contained, so wider or taller artwork will scale down instead of breaking the header.
