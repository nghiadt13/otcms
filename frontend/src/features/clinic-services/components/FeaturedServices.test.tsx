import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { PublicService } from "../types/clinicService";
import { FeaturedServices } from "./FeaturedServices";

const services: PublicService[] = [
  {
    id: "service-1",
    name: "Khám chấn thương chỉnh hình",
    description: null,
    price: 250000,
    durationMinutes: 30,
    bufferMinutes: 10,
    onlineBookable: true,
    active: true,
  },
  {
    id: "service-2",
    name: "Chụp X-quang khớp gối",
    description: "Chụp X-quang kỹ thuật số.",
    price: 180000,
    durationMinutes: 15,
    bufferMinutes: 5,
    onlineBookable: true,
    active: true,
  },
];

describe("FeaturedServices", () => {
  it("hiện giá công khai theo định dạng Việt Nam và liên kết tới bảng giá đầy đủ", async () => {
    render(<FeaturedServices services={services} />);
    expect(
      screen.getByRole("heading", { name: "Dịch vụ chính" }),
    ).toBeVisible();
    expect(screen.getByText("250.000 ₫")).toBeInTheDocument();
    expect(screen.getByText("180.000 ₫")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /Xem bảng giá đầy đủ/ }),
    ).toHaveAttribute("href", "/services");
  });

  it("ẩn cả khối khi chưa có dịch vụ nào đang cung cấp", () => {
    const { container } = render(<FeaturedServices services={[]} />);
    expect(container).toBeEmptyDOMElement();
  });
});
