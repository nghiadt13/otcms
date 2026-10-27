import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { listFeaturedServices } from "../api/clinicServiceApi";
import { FeaturedServices } from "./FeaturedServices";

describe("FeaturedServices", () => {
  it("hiện giá công khai theo định dạng Việt Nam, kèm đơn vị tính", async () => {
    render(<FeaturedServices services={await listFeaturedServices()} />);
    expect(
      screen.getByRole("heading", { name: "Dịch vụ chính" }),
    ).toBeVisible();
    expect(screen.getByText("250.000 đ")).toBeInTheDocument();
    expect(screen.getByText("320.000 đ / buổi")).toBeInTheDocument();
    expect(screen.getByText("Báo giá sau khi khám")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /Xem bảng giá đầy đủ/ }),
    ).toHaveAttribute("href", "/services");
  });

  it("ẩn cả khối khi chưa có dịch vụ nào đang hoạt động", () => {
    const { container } = render(<FeaturedServices services={[]} />);
    expect(container).toBeEmptyDOMElement();
  });
});
