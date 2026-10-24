import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { listPublicServices } from "../api/publicServiceApi";
import { parseServiceListQuery } from "../schema/serviceListQuery";
import { PublicServiceList } from "./PublicServiceList";

describe("PublicServiceList", () => {
  it("lọc theo loại lấy từ URL: chỉ hiện dịch vụ chẩn đoán hình ảnh", async () => {
    const parsed = parseServiceListQuery({ type: "IMAGING" });
    const { items } = await listPublicServices(parsed.query);
    render(<PublicServiceList filtered services={items} />);

    const names = screen.getAllByRole("heading", { level: 2 }).map((h) => h.textContent);
    expect(names).toHaveLength(4);
    expect(names).toEqual(
      expect.arrayContaining([
        "Chụp X-quang kỹ thuật số",
        "Chụp MRI khớp",
        "Siêu âm khớp",
        "Chụp CT cột sống",
      ]),
    );
    expect(screen.queryByText("Khám chấn thương chỉnh hình")).toBeNull();
  });

  it("mỗi dòng là liên kết tới chi tiết và ghi giá bằng số VND", async () => {
    const { items } = await listPublicServices({ type: "EXAMINATION" });
    render(<PublicServiceList filtered={false} services={items} />);
    const link = screen.getByRole("link", { name: /Khám chấn thương chỉnh hình/ });
    expect(link).toHaveAttribute("href", "/services/svc-consultation");
    expect(screen.getByText("250.000 ₫")).toBeInTheDocument();
  });

  it("không có dịch vụ phù hợp thì gợi ý bỏ lọc, nhưng khi không lọc thì không hiện nút đó", () => {
    const { rerender } = render(<PublicServiceList filtered services={[]} />);
    expect(screen.getByText("Chưa có dịch vụ phù hợp.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Xem tất cả dịch vụ" })).toHaveAttribute(
      "href",
      "/services",
    );

    rerender(<PublicServiceList filtered={false} services={[]} />);
    expect(screen.queryByRole("link", { name: "Xem tất cả dịch vụ" })).toBeNull();
  });
});
