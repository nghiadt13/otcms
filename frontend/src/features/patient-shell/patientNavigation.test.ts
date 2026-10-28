import { Activity, CalendarDays, Users } from "lucide-react";
import { describe, expect, it } from "vitest";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import {
  PATIENT_NAV_ITEMS,
  groupPatientNavItems,
  type PatientNavItem,
} from "./patientNavigation";

const fixtures: PatientNavItem[] = [
  {
    label: "Hồ sơ bệnh nhân",
    href: PATIENT_ROUTES.profiles,
    icon: Users,
    group: "Hồ sơ",
  },
  {
    label: "Lịch hẹn",
    href: PATIENT_ROUTES.appointments,
    icon: CalendarDays,
    group: "Khám bệnh",
  },
  {
    label: "Lượt khám hôm nay",
    href: PATIENT_ROUTES.visits,
    icon: Activity,
    group: "Khám bệnh",
  },
];

describe("groupPatientNavItems", () => {
  it("ẩn mục chưa có: nhóm không có mục nào không xuất hiện", () => {
    const groups = groupPatientNavItems(fixtures);
    expect(groups.map((view) => view.group)).toEqual(["Khám bệnh", "Hồ sơ"]);
    expect(groups.find((view) => view.group === "Thanh toán")).toBeUndefined();
  });

  it("giữ thứ tự khai báo trong nhóm", () => {
    const [khamBenh] = groupPatientNavItems(fixtures);
    expect(khamBenh.items.map((item) => item.label)).toEqual([
      "Lịch hẹn",
      "Lượt khám hôm nay",
    ]);
  });

  it("danh sách rỗng thì không có nhóm nào", () => {
    expect(groupPatientNavItems([])).toEqual([]);
  });
});

describe("PATIENT_NAV_ITEMS (mục đã khai báo)", () => {
  // Kiểm tra bất biến, không cố định số lượng: phase thêm mục không phải sửa test.
  it("mọi nhóm đang hiện đều có ít nhất một mục", () => {
    for (const view of groupPatientNavItems(PATIENT_NAV_ITEMS)) {
      expect(view.items.length).toBeGreaterThan(0);
    }
  });

  it("mọi mục đã khai báo nằm trong khu /portal và có icon", () => {
    for (const item of PATIENT_NAV_ITEMS) {
      expect(item.href.startsWith("/portal/")).toBe(true);
      expect(item.icon).toBeTruthy();
    }
  });

  it("Lượt khám hôm nay mở danh sách lượt khám trong nhóm Khám bệnh", () => {
    const item = PATIENT_NAV_ITEMS.find(
      (entry) => entry.href === PATIENT_ROUTES.visits,
    );
    expect(item?.label).toBe("Lượt khám hôm nay");
    expect(item?.group).toBe("Khám bệnh");
  });
});
