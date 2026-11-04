import { existsSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  doctorNavItems,
  findActiveBreadcrumb,
  findActiveNavItem,
} from "./doctorNavigation";

describe("findActiveNavItem", () => {
  it("chỉ khớp /doctor với đúng đường dẫn /doctor", () => {
    expect(findActiveNavItem("/doctor")?.href).toBe("/doctor");
    expect(findActiveNavItem("/doctor/khong-ton-tai")).toBeUndefined();
  });

  it("khớp cả đường dẫn con của một mục", () => {
    expect(findActiveNavItem("/doctor/today")?.href).toBe("/doctor/today");
    expect(findActiveNavItem("/doctor/imaging/study-1")?.href).toBe(
      "/doctor/imaging",
    );
  });

  it("không khớp nhầm mục có tiền tố giống nhau", () => {
    expect(findActiveNavItem("/doctor/todayX")).toBeUndefined();
  });
});

describe("findActiveBreadcrumb", () => {
  it("trả về nhóm cha và tên trang", () => {
    expect(findActiveBreadcrumb("/doctor")).toEqual({
      groupLabel: "Tổng quan",
      itemTitle: "Bảng điều khiển",
    });
    expect(findActiveBreadcrumb("/doctor/rooms")).toEqual({
      groupLabel: "Lịch làm việc",
      itemTitle: "Danh sách phòng",
    });
  });

  it("trả về undefined cho đường dẫn không có trong menu", () => {
    expect(findActiveBreadcrumb("/doctor/khong-ton-tai")).toBeUndefined();
  });
});

describe("doctorNavItems", () => {
  it("không có href trùng", () => {
    const hrefs = doctorNavItems.map((item) => item.href);
    expect(new Set(hrefs).size).toBe(hrefs.length);
  });

  it.each(doctorNavItems.map((item) => [item.href] as const))(
    "%s có page.tsx tương ứng trong app/",
    (href) => {
      const segments = href.split("/").filter(Boolean);
      const pageFile = join(
        process.cwd(),
        "src/app/(doctor)",
        ...segments,
        "page.tsx",
      );
      expect(existsSync(pageFile)).toBe(true);
    },
  );
});
