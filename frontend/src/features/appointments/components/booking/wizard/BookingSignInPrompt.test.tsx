import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BookingSignInPrompt } from "./BookingSignInPrompt";

describe("BookingSignInPrompt", () => {
  it("đăng nhập và đăng ký quay lại đúng khung giờ đã chọn sẵn", () => {
    const nextPath =
      "/portal/appointments/new?serviceId=svc-kham-tq&doctorId=doc-1&date=2026-11-19&time=08%3A15";
    render(<BookingSignInPrompt nextPath={nextPath} />);

    const encoded = encodeURIComponent(nextPath);
    expect(screen.getByRole("link", { name: /Đăng nhập/ })).toHaveAttribute(
      "href",
      `/login?next=${encoded}`,
    );
    expect(
      screen.getByRole("link", { name: /Đăng ký tài khoản/ }),
    ).toHaveAttribute("href", `/register?next=${encoded}`);
  });

  it("không truyền đường dẫn thì quay lại màn đặt lịch thường", () => {
    render(<BookingSignInPrompt />);
    expect(screen.getByRole("link", { name: /Đăng nhập/ })).toHaveAttribute(
      "href",
      "/login?next=%2Fportal%2Fappointments%2Fnew",
    );
  });
});
