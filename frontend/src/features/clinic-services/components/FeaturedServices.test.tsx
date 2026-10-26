import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { listFeaturedServices } from "../api/publicServiceApi";
import { FeaturedServices } from "./FeaturedServices";

describe("FeaturedServices", () => {
  it("hiện giá công khai theo định dạng Việt Nam và liên kết tới bảng giá đầy đủ", async () => {
    render(<FeaturedServices services={await listFeaturedServices()} />);
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
