import { defineConfig } from "astro/config";

export default defineConfig({
  site: "https://archlens.dominikbreu.dev",
  compressHTML: true,
  build: {
    format: "directory",
  },
});
