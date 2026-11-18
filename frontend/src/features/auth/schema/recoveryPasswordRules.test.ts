import { expect, it } from "vitest";
import { validateRecoveryPassword } from "./recoveryPasswordRules";
it("applies BR-04", () => {
  expect(validateRecoveryPassword("Abcd1234")).toBeUndefined();
  expect(validateRecoveryPassword("abcd123")).toBeDefined();
  expect(validateRecoveryPassword("abcdefgh")).toBeDefined();
  expect(validateRecoveryPassword("12345678")).toBeDefined();
});
