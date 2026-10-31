import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AppointmentStatusBadge } from "./AppointmentStatusBadge";

describe("AppointmentStatusBadge", () => {
  it.each([
    ["BOOKED", "Đã đặt"],
    ["CHECKED_IN", "Đã tiếp nhận"],
    ["CANCELLED", "Đã hủy"],
    ["NO_SHOW", "Không đến"],
  ] as const)(
    "%s hiện nhãn chữ %s (nhãn dùng chung với lễ tân)",
    (status, label) => {
      render(<AppointmentStatusBadge status={status} />);
      expect(screen.getByText(label)).toBeInTheDocument();
    },
  );
});
