import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PortalEmptyState, PortalNoProfileState } from "./PortalEmptyState";
import { PortalPageHeader } from "./PortalPageHeader";

describe("PortalNoProfileState", () => {
  it("nói rõ chưa có hồ sơ và dẫn tới trang thêm hồ sơ", () => {
    render(<PortalNoProfileState />);
    expect(
      screen.getByText(
        "Tài khoản của bạn chưa có hồ sơ bệnh nhân. Thêm hồ sơ để đặt lịch và xem kết quả.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Thêm hồ sơ bệnh nhân" }),
    ).toHaveAttribute("href", "/portal/profiles/new");
  });
});

describe("PortalEmptyState", () => {
  it("không có action thì không hiện nút", () => {
    render(<PortalEmptyState>Chưa có lịch hẹn nào.</PortalEmptyState>);
    expect(screen.getByText("Chưa có lịch hẹn nào.")).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});

describe("PortalPageHeader", () => {
  it("đúng một h1 kèm mô tả và slot nút", () => {
    render(
      <PortalPageHeader
        actions={<button type="button">Làm việc khác</button>}
        description="Mô tả ngắn."
        title="Lịch hẹn"
      />,
    );
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "Lịch hẹn",
    );
    expect(screen.getByText("Mô tả ngắn.")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Làm việc khác" }),
    ).toBeInTheDocument();
  });
});
