import { CalendarDays, CalendarPlus, UserCheck } from "lucide-react";
import { describe, expect, it } from "vitest";
import {
  findActiveStaffNavItem,
  groupStaffNavItems,
  filterStaffNavItemsByRole,
  STAFF_NAV_ITEMS,
  type StaffNavItem,
} from "./staffNavigation";

// Mục giả để kiểm tra logic; menu thật hiện rỗng (phase 00 chưa có trang nào).
const checkIn: StaffNavItem = {
  title: "Tiếp nhận bệnh nhân",
  href: "/staff/check-in",
  icon: UserCheck,
  group: "Tiếp nhận",
  roles: ["RECEPTIONIST"],
};
const appointments: StaffNavItem = {
  title: "Lịch hẹn",
  href: "/staff/appointments",
  icon: CalendarDays,
  group: "Lịch hẹn",
  roles: ["RECEPTIONIST"],
};
const newAppointment: StaffNavItem = {
  title: "Đặt lịch",
  href: "/staff/appointments/new",
  icon: CalendarPlus,
  group: "Lịch hẹn",
  roles: ["RECEPTIONIST"],
};
const doctorOnly: StaffNavItem = {
  title: "Chỉ bác sĩ",
  href: "/staff/doctor-only",
  icon: UserCheck,
  group: "Tiếp nhận",
  roles: ["ORTHOPEDIC_DOCTOR"],
};

describe("STAFF_NAV_ITEMS", () => {
  it("chỉ khai báo mục đã có trang: Tiếp nhận, Hàng đợi, Lịch hẹn và Đặt lịch cho Lễ tân", () => {
    expect(STAFF_NAV_ITEMS.map((item) => item.href)).toEqual([
      "/staff/check-in",
      "/staff/reception-queue",
      "/staff/appointments",
      "/staff/appointments/new",
    ]);
    expect(STAFF_NAV_ITEMS.every((item) => item.roles.includes("RECEPTIONIST"))).toBe(true);
    expect(STAFF_NAV_ITEMS.map((item) => item.group)).toEqual([
      "Tiếp nhận",
      "Tiếp nhận",
      "Lịch hẹn",
      "Lịch hẹn",
    ]);
  });
});

describe("filterStaffNavItemsByRole", () => {
  it("chỉ giữ mục có vai trò hiện tại, giữ thứ tự", () => {
    const items = [checkIn, doctorOnly, appointments];
    expect(
      filterStaffNavItemsByRole(items, "RECEPTIONIST").map((i) => i.href),
    ).toEqual(["/staff/check-in", "/staff/appointments"]);
  });

  it("vai trò không có mục nào trả mảng rỗng", () => {
    expect(filterStaffNavItemsByRole([checkIn], "PATIENT")).toEqual([]);
  });
});

describe("groupStaffNavItems", () => {
  it("nhóm theo thứ tự cố định và bỏ nhóm rỗng", () => {
    const groups = groupStaffNavItems([appointments, checkIn, newAppointment]);
    expect(groups.map((g) => g.group)).toEqual(["Tiếp nhận", "Lịch hẹn"]);
    expect(groups[1].items.map((i) => i.href)).toEqual([
      "/staff/appointments",
      "/staff/appointments/new",
    ]);
  });

  it("không có mục thì không có nhóm nào; menu thật có nhóm Tiếp nhận và Lịch hẹn", () => {
    expect(groupStaffNavItems([])).toEqual([]);
    expect(groupStaffNavItems().map((view) => view.group)).toEqual([
      "Tiếp nhận",
      "Lịch hẹn",
    ]);
  });
});

describe("findActiveStaffNavItem", () => {
  const items = [checkIn, appointments, newAppointment];

  it("khớp đúng đường dẫn", () => {
    expect(findActiveStaffNavItem("/staff/check-in", items)?.href).toBe(
      "/staff/check-in",
    );
  });

  it("khớp đường dẫn con nhưng không khớp tiền tố trùng một phần", () => {
    expect(
      findActiveStaffNavItem("/staff/check-in/abc", items)?.href,
    ).toBe("/staff/check-in");
    expect(findActiveStaffNavItem("/staff/check-inX", items)).toBeUndefined();
  });

  it("chọn mục dài nhất khi nhiều mục cùng khớp", () => {
    expect(
      findActiveStaffNavItem("/staff/appointments/new", items)?.href,
    ).toBe("/staff/appointments/new");
    expect(
      findActiveStaffNavItem("/staff/appointments/abc", items)?.href,
    ).toBe("/staff/appointments");
  });

  it("pathname rỗng hoặc không khớp trả undefined", () => {
    expect(findActiveStaffNavItem(null, items)).toBeUndefined();
    expect(findActiveStaffNavItem("/staff", items)).toBeUndefined();
    expect(findActiveStaffNavItem("/admin/rooms", items)).toBeUndefined();
  });
});
