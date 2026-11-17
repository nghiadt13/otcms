import { describe, expect, it } from "vitest";
import { homeRouteForAccount } from "./homeRouteForAccount";

describe("laboratory landing route", () => {
  it("routes laboratory technicians to their workspace", () => {
    expect(homeRouteForAccount("LAB_TECHNICIAN")).toBe("/laboratory");
  });
});
