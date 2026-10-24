import { describe, expect, it } from "vitest";
import {
  buildStaffAppointmentQuery,
  checkInHref,
  parseStaffAppointmentUrl,
} from "./staffAppointmentUrlRules";

const TODAY = "2026-10-24";
const read = (query: string) =>
  parseStaffAppointmentUrl(new URLSearchParams(query), TODAY);

describe("parseStaffAppointmentUrl", () => {
  it("thiếu ngày thì dùng hôm nay; ngày sai dạng hoặc không có thật cũng về hôm nay", () => {
    expect(read("").date).toBe(TODAY);
    expect(read("date=24-10-2026").date).toBe(TODAY);
    expect(read("date=2026-02-30").date).toBe(TODAY);
    expect(read("date=2026-10-27").date).toBe("2026-10-27");
  });

  it("bác sĩ và trạng thái lấy từ URL; trạng thái lạ bị bỏ", () => {
    expect(read("doctorId=doc-ha&status=CHECKED_IN")).toEqual({
      date: TODAY,
      doctorId: "doc-ha",
      status: "CHECKED_IN",
    });
    expect(read("status=CANCEL").status).toBeUndefined();
    expect(read("doctorId=").doctorId).toBeUndefined();
  });

  it("bỏ qua tham số tìm kiếm (không đọc q từ URL)", () => {
    expect(read("date=2026-10-24&q=0912345678")).not.toHaveProperty("q");
  });
});

describe("buildStaffAppointmentQuery", () => {
  it("luôn ghi ngày; bỏ bác sĩ và trạng thái khi trống; không có q", () => {
    expect(buildStaffAppointmentQuery({ date: TODAY })).toBe("?date=2026-10-24");
    expect(
      buildStaffAppointmentQuery({ date: TODAY, doctorId: "doc-ha", status: "BOOKED" }),
    ).toBe("?date=2026-10-24&doctorId=doc-ha&status=BOOKED");
  });
});

describe("checkInHref", () => {
  it("trỏ tới tiếp nhận kèm mã lịch đã mã hóa", () => {
    expect(checkInHref("apt-1007")).toBe("/staff/check-in?appointmentId=apt-1007");
    expect(checkInHref("a&b")).toBe("/staff/check-in?appointmentId=a%26b");
  });
});
