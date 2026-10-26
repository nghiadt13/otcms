import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { getPublicService } from "../api/publicServiceApi";
import { PublicServiceDetail } from "./PublicServiceDetail";

// Phiên đăng nhập chưa có trong test: nút rơi về liên kết đăng nhập (đường an toàn).
vi.mock("@/lib/supabase/browser", () => ({
  getSupabaseBrowserClient: () => ({
    auth: { getSession: async () => ({ data: { session: null } }) },
  }),
}));

describe("PublicServiceDetail", () => {
  it("chỉ dịch vụ Khám có nút Đặt lịch khám, dẫn qua đăng nhập", async () => {
    render(
      <PublicServiceDetail
        service={await getPublicService("svc-consultation")}
      />,
    );
    expect(
      screen.getByRole("heading", {
        level: 1,
        name: "Khám chấn thương chỉnh hình",
      }),
    ).toBeVisible();
    expect(
      screen.getByText("Mỗi lượt khám khoảng 30 phút."),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Đặt lịch khám" })).toHaveAttribute(
      "href",
      "/login?next=/portal/appointments/new",
    );
  });

  it("chẩn đoán hình ảnh không có nút đặt lịch và ghi do bác sĩ chỉ định", async () => {
    render(
      <PublicServiceDetail service={await getPublicService("svc-xray")} />,
    );
    expect(
      screen.getByText("Do bác sĩ chỉ định khi khám."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Đặt lịch khám" })).toBeNull();
    expect(screen.getByText("180.000 ₫")).toBeInTheDocument();
  });
});
