import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { PublicService } from "../types/clinicService";
import { PublicServiceDetail } from "./PublicServiceDetail";

vi.mock("@/lib/supabase/browser", () => ({
  getSupabaseBrowserClient: () => ({
    auth: { getSession: async () => ({ data: { session: null } }) },
  }),
}));

const service: PublicService = {
  id: "service-1",
  name: "Khám chấn thương chỉnh hình",
  description: "Khám với bác sĩ chuyên khoa.",
  price: 250000,
  durationMinutes: 30,
  bufferMinutes: 10,
  onlineBookable: true,
  active: true,
};

describe("PublicServiceDetail", () => {
  it("hiển thị giá và thời lượng từ backend, cho phép đặt dịch vụ online", () => {
    render(<PublicServiceDetail service={service} />);
    expect(
      screen.getByRole("heading", {
        level: 1,
        name: "Khám chấn thương chỉnh hình",
      }),
    ).toBeVisible();
    expect(screen.getByText("250.000 ₫")).toBeInTheDocument();
    expect(screen.getByText("Thời lượng dự kiến: 30 phút.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Đặt lịch khám" })).toHaveAttribute(
      "href",
      "/portal/appointments/new",
    );
  });

  it("ẩn nút đặt lịch nếu dịch vụ không bật onlineBookable", () => {
    render(<PublicServiceDetail service={{ ...service, onlineBookable: false }} />);
    expect(screen.queryByRole("link", { name: "Đặt lịch khám" })).toBeNull();
  });
});
