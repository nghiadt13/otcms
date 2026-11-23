import { render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { isActiveNavItem } from "../siteNavigation";
import { SiteHeader } from "./SiteHeader";

// Giữ component thật; chỉ giả đường dẫn đang mở.
vi.mock("next/navigation", () => ({ usePathname: () => "/doctors" }));

describe("SiteHeader", () => {
  it("giữ menu desktop và đánh dấu bác sĩ đang mở", () => {
    render(<SiteHeader hotline="1900 0000" />);
    const nav = within(screen.getByRole("navigation", { name: "Menu chính" }));
    expect(nav.getByRole("link", { name: "Bác sĩ" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(nav.getByRole("link", { name: "Giới thiệu" })).toHaveAttribute(
      "href",
      "/gioi-thieu",
    );
  });

  it("mobile có bốn liên kết trực tiếp, đăng nhập và đặt lịch, không dùng menu thu gọn", () => {
    render(<SiteHeader hotline="1900 0000" />);
    const nav = within(
      screen.getByRole("navigation", { name: "Điều hướng mobile" }),
    );
    expect(nav.getAllByRole("link").map((link) => link.textContent)).toEqual([
      "Trang chủ",
      "Dịch vụ",
      "Bác sĩ",
      "Hướng dẫn",
    ]);
    expect(nav.getByRole("link", { name: "Hướng dẫn" })).toHaveAttribute(
      "href",
      "/#quy-trinh-kham",
    );
    expect(nav.getByRole("link", { name: "Bác sĩ" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(
      screen.getByRole("link", { name: "Đặt lịch" }),
    ).toHaveAttribute("href", "/portal/appointments/new");
    expect(
      screen
        .getAllByRole("link", { name: "Đăng nhập" })
        .every((link) => link.getAttribute("href") === "/login"),
    ).toBe(true);
    expect(screen.queryByRole("button", { name: "Mở menu" })).toBeNull();
  });
});

describe("isActiveNavItem", () => {
  it("trang chủ chỉ khớp đúng '/', mục khác khớp cả trang con, neo không bao giờ khớp", () => {
    expect(isActiveNavItem("/", "/")).toBe(true);
    expect(isActiveNavItem("/", "/doctors")).toBe(false);
    expect(isActiveNavItem("/doctors", "/doctors/abc")).toBe(true);
    expect(isActiveNavItem("/services", "/doctors")).toBe(false);
    expect(isActiveNavItem("#lien-he", "/")).toBe(false);
  });
});
