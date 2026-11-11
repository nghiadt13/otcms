import { describe, expect, it } from "vitest";
import { isPatientCancellable } from "./patientCancelWindow";

const START = "2026-10-31T10:00:00+07:00";
const minutesBefore = (minutes: number) =>
  Date.parse(START) - minutes * 60_000;

describe("isPatientCancellable (hạn tự hủy 120 phút)", () => {
  it("còn đúng 120 phút: vẫn hủy được", () => {
    expect(isPatientCancellable(START, minutesBefore(120))).toBe(true);
  });

  it("còn 119 phút: không hủy được", () => {
    expect(isPatientCancellable(START, minutesBefore(119))).toBe(false);
  });

  it("còn nhiều ngày: hủy được", () => {
    expect(isPatientCancellable(START, minutesBefore(3 * 24 * 60))).toBe(true);
  });

  it("đã tới giờ hẹn: không hủy được", () => {
    expect(isPatientCancellable(START, Date.parse(START))).toBe(false);
  });
});
