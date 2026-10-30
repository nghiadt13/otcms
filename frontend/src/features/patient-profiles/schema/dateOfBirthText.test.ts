import { describe, expect, it } from "vitest";
import {
  dateOfBirthTextToIso,
  formatDateOfBirthText,
  isoToDateOfBirthText,
  validateDateOfBirthText,
} from "./dateOfBirthText";

describe("formatDateOfBirthText", () => {
  it("tự chèn dấu / khi gõ liền và bỏ ký tự không phải số", () => {
    expect(formatDateOfBirthText("1")).toBe("1");
    expect(formatDateOfBirthText("120")).toBe("12/0");
    expect(formatDateOfBirthText("12031958")).toBe("12/03/1958");
    expect(formatDateOfBirthText("12a03-1958999")).toBe("12/03/1958");
  });
});

describe("dateOfBirthTextToIso", () => {
  it("chỉ nhận ngày có thật và đổi sang yyyy-mm-dd", () => {
    expect(dateOfBirthTextToIso("29/02/2024")).toBe("2024-02-29");
    expect(dateOfBirthTextToIso("29/02/2023")).toBeUndefined();
    expect(dateOfBirthTextToIso("31/04/1990")).toBeUndefined();
    expect(dateOfBirthTextToIso("5/5/1990")).toBeUndefined();
  });
});

describe("isoToDateOfBirthText", () => {
  it("đổi yyyy-mm-dd sang dd/mm/yyyy, chuỗi sai dạng thành rỗng", () => {
    expect(isoToDateOfBirthText("1992-07-21")).toBe("21/07/1992");
    expect(isoToDateOfBirthText("21/07/1992")).toBe("");
  });
});

describe("validateDateOfBirthText", () => {
  const today = new Date(2026, 9, 24, 12);

  it("báo lỗi ô trống, sai dạng, ngày tương lai và năm quá xa", () => {
    expect(validateDateOfBirthText("", today)).toBe("Nhập ngày sinh.");
    expect(validateDateOfBirthText("2026-10-24", today)).toBe(
      "Nhập đúng dạng ngày/tháng/năm, ví dụ 20/05/1990.",
    );
    expect(validateDateOfBirthText("25/10/2026", today)).toBe(
      "Ngày sinh không được ở tương lai.",
    );
    expect(validateDateOfBirthText("01/01/1899", today)).toBe(
      "Năm sinh chưa hợp lệ.",
    );
  });

  it("ngày hôm nay và ngày quá khứ hợp lệ", () => {
    expect(validateDateOfBirthText("24/10/2026", today)).toBeUndefined();
    expect(validateDateOfBirthText("17/10/1990", today)).toBeUndefined();
  });
});
