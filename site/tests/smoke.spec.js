import { expect, test } from "@playwright/test";

test("desktop page shows core ArchLens sections", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto("/");

  await expect(page.getByRole("heading", { name: "Architecture understanding for real Java systems." })).toBeVisible();
  await expect(page.getByAltText("ArchLens")).toHaveAttribute("src", "/wordmark.svg");
  await expect(page.getByText("MCP architecture analysis built on Spoon.")).toBeVisible();
  await expect(page.getByText("Entrypoint discovery")).toBeVisible();
  await expect(page.getByText("From Java source to a queryable architecture graph.")).toBeVisible();
  await expect(page.getByText("Build the server, then let your MCP client ask better questions.")).toBeVisible();
  await expect(page.locator("link[rel=\"icon\"][media=\"(prefers-color-scheme: light)\"]")).toHaveAttribute("href", "/favicon-light.svg");
  await expect(page.locator("link[rel=\"icon\"][media=\"(prefers-color-scheme: dark)\"]")).toHaveAttribute("href", "/favicon-dark.svg");
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
