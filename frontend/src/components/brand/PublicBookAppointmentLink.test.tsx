import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PublicBookAppointmentLink } from "./PublicBookAppointmentLink";

describe("PublicBookAppointmentLink", () => {
  it("luôn dẫn tới màn đặt lịch; khách chưa đăng nhập được hỏi đăng nhập ngay tại đó", () => {
    render(
      <PublicBookAppointmentLink className="cta">
        Đặt lịch khám
      </PublicBookAppointmentLink>,
    );
    const link = screen.getByRole("link", { name: "Đặt lịch khám" });
    expect(link).toHaveAttribute("href", "/portal/appointments/new");
    expect(link).toHaveClass("cta");
  });
});
