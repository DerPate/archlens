import { expect, test } from "@playwright/test";

test("desktop page shows core ArchLens sections", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto("/");

  await expect(page.getByRole("heading", { name: "Architecture understanding for real Java systems." })).toBeVisible();
  await expect(page.getByAltText("ArchLens")).toHaveAttribute("src", "/wordmark.svg");
  await expect(page.getByText("MCP architecture analysis built on Spoon.")).toBeVisible();
  await expect(page.getByText("Entrypoint discovery")).toBeVisible();
  await expect(page.getByText("From Java source to a queryable architecture graph.")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Explore ArchLens without wiring an MCP client." })).toBeVisible();
  await expect(page.getByText("Build the server, then let your MCP client ask better questions.")).toBeVisible();
  await expect(page.locator("link[rel=\"icon\"][media=\"(prefers-color-scheme: light)\"]")).toHaveAttribute("href", "/favicon-light.svg");
  await expect(page.locator("link[rel=\"icon\"][media=\"(prefers-color-scheme: dark)\"]")).toHaveAttribute("href", "/favicon-dark.svg");
});

test("tools are grouped into ordered phase sections", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto("/");

  const groups = page.locator(".tool-group");
  await expect(groups).toHaveCount(4);
  await expect(groups.locator(".tool-group-heading")).toHaveText(["Index", "Query", "Render", "Export"]);
  await expect(groups.nth(0).locator(".tool-card-phase")).toHaveText(["Index"]);
  await expect(groups.nth(1).locator(".tool-card-phase")).toHaveText(Array(11).fill("Query"));
  await expect(groups.nth(2).locator(".tool-card-phase")).toHaveText(Array(7).fill("Render"));
  await expect(groups.nth(3).locator(".tool-card-phase")).toHaveText(Array(5).fill("Export"));
});

test("mobile page keeps navigation and primary CTA visible", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");

  await expect(page.getByLabel("ArchLens home")).toBeVisible();
  await expect(page.locator(".nav-logo img")).toHaveAttribute("src", "/nav-logo.svg");
  await expect(page.locator(".nav-logo source[media=\"(prefers-color-scheme: light)\"]")).toHaveAttribute("srcset", "/nav-logo-light.svg");
  await expect(page.locator(".nav-logo source[media=\"(prefers-color-scheme: dark)\"]")).toHaveAttribute("srcset", "/nav-logo-dark.svg");
  await expect(page.getByRole("link", { name: "View on GitHub" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Read install guide" })).toBeVisible();
});

test("analytics waits for cookie consent", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByText("We use cookies")).toBeVisible();
  await expect(page.locator("script[src=\"https://www.googletagmanager.com/gtag/js?id=G-N4J5QGY48M\"]")).toHaveCount(0);

  await page.getByRole("button", { name: "Accept all" }).click();

  await expect(page.locator("script[src=\"https://www.googletagmanager.com/gtag/js?id=G-N4J5QGY48M\"]")).toHaveCount(1);
  await expect.poll(async () => page.evaluate(() => {
    return window.dataLayer?.some((entry) => {
      const args = Array.from(entry);
      return args[0] === "config" && args[1] === "G-N4J5QGY48M";
    }) ?? false;
  })).toBe(true);
});

const toolPages = ["index-workspace", "query-architecture-graph", "trace-data-flow", "render-pipeline", "answer-architecture-question"];

// 681 is just above the breakpoint below which argument tables stack.
for (const width of [390, 681, 1440]) {
  test(`tool pages fit ${width === 390 ? "a phone" : `${width}px`}`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 });
    for (const tool of toolPages) {
      await page.goto(`/tools/${tool}`);
      // main clips its overflow, so the document never scrolls: measure the content itself.
      const clipped = await page.evaluate(() => {
        const container = document.querySelector(".tool-page .container").getBoundingClientRect();
        return [...document.querySelectorAll(".arg-table, .arg-table td, .tool-page-intro, .tool-page-intro code, .tool-page-title")]
          .filter((element) => element.getBoundingClientRect().right > container.right + 1 || element.scrollWidth > element.clientWidth + 1 && element.clientWidth > 0)
          .map((element) => `${element.tagName}: ${element.textContent.trim().slice(0, 50)}`);
      });
      expect(clipped, tool).toEqual([]);
    }
  });
}

for (const width of [390, 681, 1440]) {
  test(`navigation covers no page title at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 });
    for (const path of ["/", ...toolPages.map((tool) => `/tools/${tool}`)]) {
      await page.goto(path);
      const nav = await page.locator("nav.nav").boundingBox();
      const first = await page.locator(path === "/" ? ".hero-wordmark" : "main h1").boundingBox();
      expect(first.y, path).toBeGreaterThanOrEqual(nav.y + nav.height);
    }
  });
}

test("landing claims match the product", async ({ page }) => {
  await page.goto("/");

  const main = page.locator("main");
  await expect(main).toContainText("What is the impact of changing OrderRepository?");
  await expect(main).toContainText("eleven question families");
  await expect(main).not.toContainText("Where is order data stored?");
  await expect(main).not.toContainText("self-contained");
});

test("install offers the release and the migration guide", async ({ page }) => {
  await page.goto("/");

  const install = page.locator("#install");
  await expect(install.getByRole("link", { name: "Download the latest release" }))
    .toHaveAttribute("href", "https://github.com/DerPate/archlens/releases/latest");
  await expect(install.getByRole("link", { name: "migration guide" }))
    .toHaveAttribute("href", "https://github.com/DerPate/archlens/blob/main/docs/MIGRATION_3.0.md");
  await expect(page.locator('meta[property="og:image"]'))
    .toHaveAttribute("content", "https://archlens.dominikbreu.dev/og-image.png");
});
