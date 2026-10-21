import { describe, expect, it } from "vitest";
import { validateShiftForm, type ShiftFormValues } from "./shiftFieldRules";

const TODAY = "2026-10-20";
const valid: ShiftFormValues = {
  staffId: "st-bs1",
  date: "2026-10-21",
  startTime: "08:00",
  endTime: "12:00",
  roomId: "rm-k2",
};

describe("validateShiftForm", () => {
  it("hợp lệ thì không có lỗi", () => {
    expect(validateShiftForm(valid, TODAY, { requireStaff: true })).toEqual({});
  });

  it("thiếu trường -> MSG-12 đúng ô", () => {
    const errors = validateShiftForm(
      { staffId: "", date: "", startTime: "", endTime: "", roomId: "" },
      TODAY,
      { requireStaff: true },
    );
    expect(errors).toEqual({
      staffId: "MSG-12",
      date: "MSG-12",
      startTime: "MSG-12",
      endTime: "MSG-12",
      roomId: "MSG-12",
    });
  });

  it("khi sửa không bắt buộc nhân viên", () => {
    expect(
      validateShiftForm({ ...valid, staffId: "" }, TODAY, { requireStaff: false }),
    ).toEqual({});
  });

  it("kết thúc không sau bắt đầu -> MSG-34 ở ô kết thúc", () => {
    for (const endTime of ["08:00", "07:59"]) {
      expect(
        validateShiftForm({ ...valid, endTime }, TODAY, { requireStaff: true }),
      ).toEqual({ endTime: "MSG-34" });
    }
  });

  it("ngày quá khứ bị chặn khi thêm và khi sửa; hôm nay hợp lệ", () => {
    for (const requireStaff of [true, false]) {
      expect(
        validateShiftForm({ ...valid, date: "2026-10-19" }, TODAY, { requireStaff }),
      ).toEqual({ date: "MSG-34" });
    }
    expect(
      validateShiftForm({ ...valid, date: TODAY }, TODAY, { requireStaff: true }),
    ).toEqual({});
  });
});
