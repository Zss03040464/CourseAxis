import { describe, expect, it } from "vitest";

import { buildSiteConfig, normalizeExternalUrl } from "./siteConfig.js";


describe("normalizeExternalUrl", () => {
  it("accepts an explicit HTTPS URL", () => {
    expect(normalizeExternalUrl("https://github.com/example/courseaxis")).toBe(
      "https://github.com/example/courseaxis",
    );
  });

  it("does not strip a meaningful trailing slash from a query value", () => {
    expect(normalizeExternalUrl("https://example.com/pay?next=/")).toBe(
      "https://example.com/pay?next=/",
    );
  });

  it.each([
    undefined,
    "",
    "http://example.com",
    "javascript:alert(1)",
    "https://user:password@example.com",
    "https://",
  ])("fails closed for %s", (value) => {
    expect(normalizeExternalUrl(value)).toBeNull();
  });
});


describe("buildSiteConfig", () => {
  it("keeps GitHub and sponsor destinations independently optional", () => {
    expect(
      buildSiteConfig({
        VITE_GITHUB_URL: "https://github.com/example/courseaxis",
        VITE_SPONSOR_URL: "",
      }),
    ).toEqual({
      githubUrl: "https://github.com/example/courseaxis",
      sponsorUrl: null,
    });
  });
});
