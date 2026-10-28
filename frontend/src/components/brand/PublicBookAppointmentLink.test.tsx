import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PublicBookAppointmentLink } from "./PublicBookAppointmentLink";

// Chỉ giả lớp phiên đăng nhập (ranh giới với backend); link và effect chạy thật.
const { getSession } = vi.hoisted(() => ({ getSession: vi.fn() }));
vi.mock("@/lib/auth/authSession", () => ({ getSession }));

const LOGIN_HREF = "/login?next=/portal/appointments/new";

function renderLink() {
  render(
    <PublicBookAppointmentLink className="cta">
      Đặt lịch khám
    </PublicBookAppointmentLink>,
  );
  return screen.getByRole("link", { name: "Đặt lịch khám" });
}

afterEach(() => {
  getSession.mockReset();
  vi.restoreAllMocks();
});

describe("PublicBookAppointmentLink", () => {
  it("khách chưa đăng nhập được đưa qua đăng nhập rồi quay lại đặt lịch", async () => {
    getSession.mockResolvedValue(null);
    const link = renderLink();
    await waitFor(() => expect(getSession).toHaveBeenCalled());
    expect(link).toHaveAttribute("href", LOGIN_HREF);
    expect(link).toHaveClass("cta");
  });

  it("người đã có phiên đăng nhập đi thẳng tới màn đặt lịch", async () => {
    getSession.mockResolvedValue({ accessToken: "t" });
    const link = renderLink();
    await waitFor(() =>
      expect(link).toHaveAttribute("href", "/portal/appointments/new"),
    );
  });

  it("không đọc được phiên thì giữ liên kết đăng nhập và ghi log", async () => {
    getSession.mockRejectedValue(new Error("Thiếu cấu hình"));
    const log = vi.spyOn(console, "error").mockImplementation(() => {});
    const link = renderLink();
    await waitFor(() => expect(log).toHaveBeenCalled());
    expect(link).toHaveAttribute("href", LOGIN_HREF);
  });
});
