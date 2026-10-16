import { existsSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { adminNavItems, findActiveNavItem } from "./adminNavigation";

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
