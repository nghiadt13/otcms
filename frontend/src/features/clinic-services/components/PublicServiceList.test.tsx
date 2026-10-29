import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { PublicService } from "../types/clinicService";
import { PublicServiceList } from "./PublicServiceList";

const services: PublicService[] = [
  {
    id: "service-1",
    name: "Khám chấn thương chỉnh hình",
    description: "Khám với bác sĩ chuyên khoa.",
    price: 250000,
    durationMinutes: 30,
    bufferMinutes: 10,
    onlineBookable: true,
    active: true,
  },
];

describe("PublicServiceList", () => {
  it("hiển thị tên, mô tả và giá backend trả về", () => {
    render(<PublicServiceList filtered={false} services={services} />);
    const link = screen.getByRole("link", {
      name: /Khám chấn thương chỉnh hình/,
    });
    expect(link).toHaveAttribute("href", "/services/service-1");
    expect(screen.getByText("250.000 ₫")).toBeInTheDocument();
  });

  it("khi không có dịch vụ thì gợi ý bỏ từ khóa tìm kiếm", () => {
    const { rerender } = render(<PublicServiceList filtered services={[]} />);
    expect(screen.getByText("Chưa có dịch vụ phù hợp.")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Xem tất cả dịch vụ" }),
    ).toHaveAttribute("href", "/services");

    rerender(<PublicServiceList filtered={false} services={[]} />);
    expect(
      screen.queryByRole("link", { name: "Xem tất cả dịch vụ" }),
    ).toBeNull();
  });
});
