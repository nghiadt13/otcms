import { describe, expect, it } from "vitest";
import { agePriority, receptionPriority } from "./receptionRules";
describe("reception priority based on birthdate", () => {
  it.each([
    ["2020-10-29", "2026-10-28", "child"],
    ["2020-10-28", "2026-10-28", "normal"],
    ["1951-10-29", "2026-10-28", "normal"],
    ["1951-10-28", "2026-10-28", "elderly"],
    ["1950-01-01", "2026-10-28", "elderly"],
    ["2024-02-29", "2030-02-28", "child"],
    ["2024-02-29", "2030-03-01", "normal"],
    ["bad", "2026-10-28", "normal"],
    ["2027-01-01", "2026-10-28", "normal"],
  ])("evaluates %s on %s as %s", (dob, day, expected) => {
    expect(agePriority(dob, day)).toBe(expected);
  });
  it("re-evaluates age priorities but preserves a separate verified category", () => {
    expect(receptionPriority("1951-10-28", "2026-10-28", "normal")).toBe(
      "elderly",
    );
    expect(receptionPriority("2020-10-28", "2026-10-28", "child")).toBe(
      "normal",
    );
    expect(receptionPriority("2022-01-01", "2026-10-28", "emergency")).toBe(
      "emergency",
    );
    expect(receptionPriority("1990-01-01", "2026-10-28", "merit")).toBe(
      "merit",
    );
  });
});
