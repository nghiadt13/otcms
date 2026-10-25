import { describe, expect, it } from "vitest";
import { STAFF_ROUTES } from "./staffRoutes";

describe("STAFF_ROUTES", () => {
  it("lối vào khu Lễ tân là /reception", () => {
    expect(STAFF_ROUTES.home).toBe("/reception");
  });
});
