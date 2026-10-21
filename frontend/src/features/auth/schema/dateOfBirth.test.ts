import { describe, expect, it } from "vitest";
import {
  dateOfBirthToIso,
  formatDateOfBirthInput,
  parseDateOfBirth,
  validateDateOfBirth,
} from "./dateOfBirth";

describe("dateOfBirth", () => {
  it("tự chèn dấu / theo dd/mm/yyyy và bỏ ký tự không phải số", () => {
    expect(formatDateOfBirthInput("2")).toBe("2");
    expect(formatDateOfBirthInput("200")).toBe("20/0");
    expect(formatDateOfBirthInput("20a05-1990")).toBe("20/05/1990");
    expect(formatDateOfBirthInput("2005199012")).toBe("20/05/1990");
  });

  it("chỉ nhận ngày có thật", () => {
    expect(parseDateOfBirth("29/02/2024")).toBeDefined();
    expect(parseDateOfBirth("29/02/2023")).toBeUndefined();
    expect(parseDateOfBirth("31/04/1990")).toBeUndefined();
    expect(parseDateOfBirth("5/5/1990")).toBeUndefined();
  });

  it("báo lỗi ngày tương lai, năm quá xa và ô trống", () => {
    const today = new Date(2026, 9, 17);
    expect(validateDateOfBirth("", today)).toBe("Nhập ngày sinh.");
    expect(validateDateOfBirth("18/10/2026", today)).toBeDefined();
    expect(validateDateOfBirth("01/01/1899", today)).toBeDefined();
    expect(validateDateOfBirth("17/10/2026", today)).toBeUndefined();
  });

  it("đổi sang yyyy-mm-dd để gửi backend", () => {
    expect(dateOfBirthToIso("20/05/1990")).toBe("1990-05-20");
  });
});
