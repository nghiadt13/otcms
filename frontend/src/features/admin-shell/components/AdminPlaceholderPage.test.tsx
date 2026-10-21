import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AdminPlaceholderPage } from "./AdminPlaceholderPage";

describe("AdminPlaceholderPage", () => {
  it("hiện tiêu đề, mô tả và UC của mục điều hướng", () => {
    render(<AdminPlaceholderPage href="/admin/accounts" />);

    expect(
      screen.getByRole("heading", { level: 1, name: "Tài khoản người dùng" }),
    ).toBeInTheDocument();
    expect(screen.getByText("UC07–UC09")).toBeInTheDocument();
    expect(screen.getByText("Màn hình đang được xây dựng")).toBeInTheDocument();
  });

  it("báo lỗi khi href chưa khai báo trong adminNavigation", () => {
    // React in lỗi render ra console; tắt để kết quả test gọn.
    vi.spyOn(console, "error").mockImplementation(() => {});

    expect(() =>
      render(<AdminPlaceholderPage href="/admin/chua-khai-bao" />),
    ).toThrow("Chưa khai báo mục điều hướng");
  });
});
