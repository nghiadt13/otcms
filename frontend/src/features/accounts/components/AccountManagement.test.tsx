import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AccountManagement } from "./AccountManagement";

describe("AccountManagement", () => {
  it("tải danh sách tài khoản mẫu rồi tìm kiếm theo tên (có debounce)", async () => {
    render(<AccountManagement />);

    expect(
      await screen.findByText("BS. Nguyễn Văn Long"),
    ).toBeInTheDocument();
    expect(screen.getByText("Hoàng Đức An")).toBeInTheDocument();

    await userEvent.type(
      screen.getByRole("textbox", { name: "Tìm kiếm người dùng" }),
      "Nguyễn Văn Long",
    );

    // Chờ debounce 250ms hoàn thành
    await waitFor(() => {
      expect(screen.queryByText("Hoàng Đức An")).not.toBeInTheDocument();
    });
    expect(screen.getByText("BS. Nguyễn Văn Long")).toBeInTheDocument();
  });

  it("mở hộp thoại tạo tài khoản nhân viên khi bấm nút chính", async () => {
    render(<AccountManagement />);

    const createButton = await screen.findByRole("button", {
      name: "Tạo tài khoản nhân viên",
    });
    await userEvent.click(createButton);

    expect(
      screen.getByRole("heading", { name: "Tạo tài khoản nhân viên" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Họ và tên")).toBeInTheDocument();
    expect(screen.getByLabelText("Email đăng nhập")).toBeInTheDocument();
    expect(screen.getByLabelText("Số điện thoại")).toBeInTheDocument();
  });

  it("mở side drawer chi tiết khi bấm vào một dòng", async () => {
    render(<AccountManagement />);

    const row = await screen.findByText("BS. Nguyễn Văn Long");
    await userEvent.click(row);

    expect(
      await screen.findByRole("heading", { name: "Thông tin tài khoản" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Khóa tài khoản người dùng" }),
    ).toBeInTheDocument();
    expect(screen.getAllByText("long.nv@otcms.vn").length).toBeGreaterThanOrEqual(1);
  });

  it("lọc danh sách khi bấm vào tab tóm tắt trạng thái", async () => {
    render(<AccountManagement />);

    await screen.findByText("BS. Nguyễn Văn Long");

    // Bấm tab "Tạm khóa"
    const inactiveTab = screen.getByRole("tab", { name: /Tạm khóa/i });
    await userEvent.click(inactiveTab);

    // Bùi Minh Tâm là inactive, BS Long là active
    expect(await screen.findByText("Bùi Minh Tâm")).toBeInTheDocument();
    expect(screen.queryByText("BS. Nguyễn Văn Long")).not.toBeInTheDocument();
  });
});
