import { describe, expect, it } from "vitest";
import { isPasswordValid } from "@/lib/domain/passwordPolicy";
import {
  generateInitialPassword,
  validateInitialPassword,
  validateUsername,
} from "./accountFieldRules";

describe("validateUsername", () => {
  it.each(["letan.moi", "kttd_cuong", "abcd", "a".repeat(30)])(
    "chấp nhận %s",
    (value) => expect(validateUsername(value)).toBeNull(),
  );

  it.each(["Le An", "abc", "a".repeat(31), "Letan.Moi", "tên.việt", ""])(
    "từ chối %s",
    (value) => expect(validateUsername(value)).not.toBeNull(),
  );
});

describe("validateInitialPassword", () => {
  it("chấp nhận mật khẩu đủ 8 ký tự, có chữ và số", () => {
    expect(validateInitialPassword("Xk7mQ2pL")).toBeNull();
  });

  it.each(["Ab1", "abcdefgh", "12345678"])("từ chối %s", (value) =>
    expect(validateInitialPassword(value)).not.toBeNull(),
  );
});

describe("generateInitialPassword", () => {
  it("luôn đạt BR-04, đủ 12 ký tự và khác nhau giữa các lần", () => {
    const seen = new Set<string>();
    for (let i = 0; i < 200; i++) {
      const password = generateInitialPassword();
      expect(password).toHaveLength(12);
      expect(isPasswordValid(password)).toBe(true);
      seen.add(password);
    }
    expect(seen.size).toBeGreaterThan(190);
  });
});
