import { describe, expect, it } from "vitest";
import { formatClinicServicePrice } from "./formatClinicServicePrice";

describe("formatClinicServicePrice", () => {
  it("giữ phần thập phân của giá backend trả về", () => {
    expect(formatClinicServicePrice(125000.5)).toBe("125.000,5 ₫");
  });

  it("định dạng giá VND nguyên theo locale Việt Nam", () => {
    expect(formatClinicServicePrice(250000)).toBe("250.000 ₫");
  });

  it("không hiển thị giá mặc định 0 đồng như một dịch vụ miễn phí", () => {
    expect(formatClinicServicePrice(0)).toBe("Đang cập nhật giá");
  });
});
