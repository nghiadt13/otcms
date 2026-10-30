import { describe, expect, it } from "vitest";
import { validateRecoveryPassword } from "./recoveryPasswordRules";
describe("backend-compatible recovery password", () => {
  it("enforces length and UTF-8 limits without adding composition rules", () => {
    expect(validateRecoveryPassword("a".repeat(12))).toBeUndefined();
    expect(validateRecoveryPassword("a".repeat(64))).toBeUndefined();
    expect(validateRecoveryPassword("a".repeat(11))).toBeDefined();
    expect(validateRecoveryPassword("a".repeat(65))).toBeDefined();
    expect(validateRecoveryPassword("ả".repeat(24))).toBeUndefined();
    expect(validateRecoveryPassword("ả".repeat(25))).toBeDefined();
  });
});
