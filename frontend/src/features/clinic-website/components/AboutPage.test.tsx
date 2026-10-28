import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { ClinicContact } from "../types/websiteContent";
import { AboutPage } from "./AboutPage";

const contact: ClinicContact = {
  clinicName: "Phòng khám OTCMS",
  address: "12 Nguyễn Thị Minh Khai, Quận 1, TP. Hồ Chí Minh",
  hotline: "1900 0000",
  email: "lienhe@otcms.example",
  openingHours: ["Thứ 2 – Thứ 6: 7:30 – 17:00", "Thứ 7: 7:30 – 11:30"],
};

describe("AboutPage", () => {
  it("chưa có trang giới thiệu vẫn hiện giờ làm việc và liên hệ, không lỗi", () => {
    render(<AboutPage contact={contact} page={null} />);
    expect(
      screen.getByRole("heading", { level: 1, name: "Giới thiệu phòng khám" }),
    ).toBeVisible();
    expect(screen.getByText("Thứ 7: 7:30 – 11:30")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Hotline 1900 0000" }),
    ).toHaveAttribute("href", "tel:19000000");
  });

  it("hiện tiêu đề và nội dung trang đã đăng", () => {
    render(
      <AboutPage
        contact={contact}
        page={{
          title: "Về phòng khám",
          body: "Chuyên chấn thương chỉnh hình.",
        }}
      />,
    );
    expect(
      screen.getByRole("heading", { level: 1, name: "Về phòng khám" }),
    ).toBeVisible();
    expect(
      screen.getByText("Chuyên chấn thương chỉnh hình."),
    ).toBeInTheDocument();
  });
});
