import { describe, expect, it } from "vitest";
import {
  formatPhoneForDisplay,
  STAFF_NO_CANDIDATE_LINES,
} from "./staffAppointmentText";

describe("formatPhoneForDisplay", () => {
  it("số 10 chữ số ghi theo nhóm 4-3-3; số khác giữ nguyên; không có số thì ghi rõ", () => {
    expect(formatPhoneForDisplay("0912345678")).toBe("0912 345 678");
    expect(formatPhoneForDisplay("0912 345")).toBe("0912 345");
    expect(formatPhoneForDisplay(null)).toBe("Chưa có SĐT");
  });
});

describe("STAFF_NO_CANDIDATE_LINES", () => {
  it("không nhắc đến admin: Lễ tân không hủy lịch (BR-22)", () => {
    expect(STAFF_NO_CANDIDATE_LINES.join(" ")).not.toMatch(/Admin|admin/);
    expect(STAFF_NO_CANDIDATE_LINES.join(" ")).toMatch(/Lễ tân không hủy/);
  });
});
