import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PublicBookAppointmentLink } from "./PublicBookAppointmentLink";

const { getSession } = vi.hoisted(() => ({ getSession: vi.fn() }));
vi.mock("@/lib/auth/authSession", () => ({ getSession }));

afterEach(() => {
  getSession.mockReset();
  vi.restoreAllMocks();
});

describe("PublicBookAppointmentLink", () => {
  it("khách chưa đăng nhập đi qua đăng nhập rồi quay lại đặt lịch", async () => {
    getSession.mockResolvedValue(null);
    render(
      <PublicBookAppointmentLink className="cta">
        Đặt lịch khám
      </PublicBookAppointmentLink>,
    );
    const link = screen.getByRole("link", { name: "Đặt lịch khám" });
    await waitFor(() => expect(getSession).toHaveBeenCalled());
    expect(link).toHaveAttribute(
      "href",
      "/login?next=/portal/appointments/new",
    );
    expect(link).toHaveClass("cta");
  });

  it("người đã đăng nhập đi thẳng tới màn đặt lịch", async () => {
    getSession.mockResolvedValue({ accessToken: "test-token" });
    render(
      <PublicBookAppointmentLink>Đặt lịch khám</PublicBookAppointmentLink>,
    );
    await waitFor(() =>
      expect(
        screen.getByRole("link", { name: "Đặt lịch khám" }),
      ).toHaveAttribute("href", "/portal/appointments/new"),
    );
  });
});
