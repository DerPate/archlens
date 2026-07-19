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
  await expect(groups.nth(1).locator(".tool-card-phase")).toHaveText(Array(10).fill("Query"));
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
