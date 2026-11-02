import { existsSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  adminNavItems,
  findActiveBreadcrumb,
  findActiveNavItem,
} from "./adminNavigation";

describe("findActiveNavItem", () => {
  it("chỉ khớp /admin với đúng đường dẫn /admin", () => {
    expect(findActiveNavItem("/admin")?.href).toBe("/admin");
    expect(findActiveNavItem("/admin/khong-ton-tai")).toBeUndefined();
  });

  it("khớp cả đường dẫn con của một mục", () => {
    expect(findActiveNavItem("/admin/rooms")?.href).toBe("/admin/rooms");
    expect(findActiveNavItem("/admin/rooms/room-001")?.href).toBe(
      "/admin/rooms",
    );
  });

  it("không khớp nhầm mục có tiền tố giống nhau", () => {
    expect(findActiveNavItem("/admin/roomsX")).toBeUndefined();
  });
});

describe("findActiveBreadcrumb", () => {
  it("trả về đúng nhóm cha và tên trang cho /admin/accounts", () => {
    const breadcrumb = findActiveBreadcrumb("/admin/accounts");
    expect(breadcrumb?.groupLabel).toBe("Tài khoản và nhân sự");
    expect(breadcrumb?.itemTitle).toBe("Tài khoản người dùng");
  });

  it("trả về đúng nhóm cha cho /admin (Tổng quan > Bảng điều khiển)", () => {
    const breadcrumb = findActiveBreadcrumb("/admin");
    expect(breadcrumb?.groupLabel).toBe("Tổng quan");
    expect(breadcrumb?.itemTitle).toBe("Bảng điều khiển");
  });

  it("trả về đúng nhóm cha cho các trang khác", () => {
    const rooms = findActiveBreadcrumb("/admin/rooms");
    expect(rooms?.groupLabel).toBe("Vận hành phòng khám");
    expect(rooms?.itemTitle).toBe("Phòng khám");

    const audit = findActiveBreadcrumb("/admin/activity-logs");
    expect(audit?.groupLabel).toBe("Hệ thống");
    expect(audit?.itemTitle).toBe("Nhật ký hoạt động");
  });

  it("trả về undefined cho đường dẫn không tồn tại", () => {
    expect(findActiveBreadcrumb("/admin/khong-ton-tai")).toBeUndefined();
  });
});

describe("adminNavItems", () => {
  it("không có href trùng", () => {
    const hrefs = adminNavItems.map((item) => item.href);
    expect(new Set(hrefs).size).toBe(hrefs.length);
  });

  it.each(adminNavItems.map((item) => [item.href] as const))(
    "%s có page.tsx tương ứng trong app/",
    (href) => {
      const segments = href.split("/").filter(Boolean); // ["admin", "rooms"]
      const pageFile = join(
        process.cwd(),
        "src/app/(admin)",
        ...segments,
        "page.tsx",
      );
      expect(existsSync(pageFile)).toBe(true);
    },
  );
});
