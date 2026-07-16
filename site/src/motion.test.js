import { describe, expect, it } from "vitest";

import { preferredScrollBehavior } from "./motion.js";


describe("preferredScrollBehavior", () => {
  it("uses instant scrolling when reduced motion is requested", () => {
    expect(preferredScrollBehavior(true)).toBe("auto");
  });

  it("uses smooth scrolling otherwise", () => {
    expect(preferredScrollBehavior(false)).toBe("smooth");
  });
});
