import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { isActiveNavItem } from "../siteNavigation";
import { SiteHeader } from "./SiteHeader";

// Chỉ giả đường dẫn hiện tại của Next; header và Sheet chạy như thật.
vi.mock("next/navigation", () => ({ usePathname: () => "/doctors" }));

describe("SiteHeader", () => {
  it("đánh dấu mục đang mở và dẫn CTA Đặt lịch khám tới đăng nhập rồi đặt lịch", () => {
    render(<SiteHeader hotline="1900 0000" />);
    expect(screen.getByRole("link", { name: "Bác sĩ" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("link", { name: "Đặt lịch khám" })).toHaveAttribute(
      "href",
      "/login?next=/portal/appointments/new",
    );
  });

  it("mở menu màn hẹp trong Sheet, đóng bằng Esc và trả focus về nút menu", async () => {
    const user = userEvent.setup();
    render(<SiteHeader hotline="1900 0000" />);
    const toggle = screen.getByRole("button", { name: "Mở menu" });

    await user.click(toggle);
    const sheet = await screen.findByRole("dialog");
    expect(sheet).toHaveTextContent("Đăng ký");

    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(toggle).toHaveFocus();
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
