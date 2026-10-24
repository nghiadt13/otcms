import { describe, expect, it } from "vitest";
import { foldSearchText, matchesAppointmentQuery } from "./appointmentRowFilter";

const row = { patientName: "Đỗ Thanh Hà", phone: "0912345678" };

describe("lọc lịch hẹn hôm nay", () => {
  it("bỏ dấu và đổi đ thành d, không phân biệt hoa thường", () => {
    expect(foldSearchText("Đỗ Thanh Hà")).toBe("do thanh ha");
    expect(matchesAppointmentQuery(row, "do thanh")).toBe(true);
    expect(matchesAppointmentQuery(row, "ĐỖ THANH")).toBe(true);
    expect(matchesAppointmentQuery(row, "Thánh")).toBe(true);
    expect(matchesAppointmentQuery(row, "Thương")).toBe(false);
  });

  it("số điện thoại khớp khi bỏ khoảng trắng trong ô tìm", () => {
    expect(matchesAppointmentQuery(row, "0912 345")).toBe(true);
    expect(matchesAppointmentQuery(row, "0999")).toBe(false);
  });

  it("ô trống khớp mọi dòng", () => {
    expect(matchesAppointmentQuery(row, "   ")).toBe(true);
  });
});
