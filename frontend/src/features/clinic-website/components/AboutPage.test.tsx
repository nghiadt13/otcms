import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { clinicContact, hotlineTelHref } from "../siteNavigation";
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
  it("hiện giới thiệu mặc định cùng thông tin liên hệ đã công khai", () => {
    render(<AboutPage contact={contact} page={null} />);
    expect(
      screen.getByRole("heading", { level: 1, name: "Giới thiệu phòng khám" }),
    ).toBeVisible();
    expect(
      screen.getByText(/^Phòng khám chấn thương chỉnh hình OTCMS tiếp nhận/),
    ).toBeVisible();
    expect(screen.getByText("Thứ 7: 7:30 – 11:30")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Hotline 1900 0000" }),
    ).toHaveAttribute("href", "tel:19000000");
    expect(screen.getByRole("link", { name: contact.email })).toHaveAttribute(
      "href",
      `mailto:${contact.email}`,
    );
    expect(screen.getByRole("link", { name: "Đặt lịch khám" })).toHaveAttribute(
      "href",
      "/portal/appointments/new",
    );
    for (const title of [
      "Khám chuyên khoa",
      "Hỗ trợ chẩn đoán",
      "Theo dõi kết quả",
    ]) {
      expect(screen.getByRole("heading", { name: title })).toBeVisible();
    }
  });

  it("hiện tiêu đề và nội dung trang đã đăng", () => {
    render(
      <AboutPage
        contact={contact}
        page={{
          title: "Về phòng khám",
          body: "Chuyên **chấn thương chỉnh hình**.",
        }}
      />,
    );
    expect(
      screen.getByRole("heading", { level: 1, name: "Về phòng khám" }),
    ).toBeVisible();
    expect(
      screen.getByText("chấn thương chỉnh hình", { selector: "strong" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/^Phòng khám chấn thương chỉnh hình OTCMS tiếp nhận/),
    ).not.toBeInTheDocument();
  });

  it("lấy dữ liệu dự phòng theo từng trường rỗng và giữ số hotline đã công khai", () => {
    render(
      <AboutPage
        contact={{ address: " ", hotline: " 1900 0000 ", openingHours: [" "] }}
        page={{ title: " ", body: "\n  " }}
      />,
    );

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "Giới thiệu phòng khám",
    );
    expect(screen.getByText(clinicContact.clinicName)).toBeVisible();
    expect(screen.getByText(clinicContact.address)).toBeVisible();
    expect(screen.getByText(clinicContact.openingHours[0])).toBeVisible();
    expect(
      screen.getByRole("link", { name: clinicContact.email }),
    ).toHaveAttribute("href", `mailto:${clinicContact.email}`);
    expect(
      screen.getByRole("link", { name: "Hotline 1900 0000" }),
    ).toHaveAttribute("href", hotlineTelHref("1900 0000"));
  });
});
