import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { DoctorAvailabilitySummary } from "./DoctorAvailabilitySummary";

describe("DoctorAvailabilitySummary", () => {
  it("ngày không còn giờ hiện 'Hết giờ' bằng chữ, ngày còn giờ hiện 'Còn giờ'", () => {
    render(
      <DoctorAvailabilitySummary
        availability={[
          { date: "2026-10-24", hasOpenTime: true },
          { date: "2026-10-25", hasOpenTime: false },
        ]}
      />,
    );
    const rows = screen.getAllByRole("listitem");
    expect(rows).toHaveLength(2);

    const saturday = within(rows[0]);
    expect(saturday.getByText("T7 24/10")).toBeInTheDocument();
    expect(saturday.getByText("Còn giờ")).toBeInTheDocument();

    const sunday = within(rows[1]);
    expect(sunday.getByText("CN 25/10")).toBeInTheDocument();
    expect(sunday.getByText("Hết giờ")).toBeInTheDocument();
  });

  it("không liệt kê từng giờ, chỉ ghi cách xem giờ cụ thể", () => {
    render(
      <DoctorAvailabilitySummary
        availability={[{ date: "2026-10-24", hasOpenTime: true }]}
      />,
    );
    expect(
      screen.getByText(
        "Giờ cụ thể hiện khi bạn chọn bác sĩ và giờ trên màn đặt lịch.",
      ),
    ).toBeInTheDocument();
  });
});
