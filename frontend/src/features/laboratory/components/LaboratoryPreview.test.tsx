import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { LaboratoryPreview } from "./LaboratoryPreview";

describe("LaboratoryPreview", () => {
  it("validates, flags abnormal values, and confirms approval is only a preview", () => {
    render(<LaboratoryPreview />);
    fireEvent.click(screen.getByRole("button", { name: /Nguyễn Minh An/ }));
    fireEvent.click(screen.getByRole("button", { name: "Duyệt kết quả mẫu" }));
    expect(screen.getAllByText(/Giá trị chỉ số không hợp lệ/)).toHaveLength(4);
    fireEvent.change(screen.getByLabelText("Kết quả Hồng cầu (RBC)"), {
      target: { value: "6" },
    });
    fireEvent.change(screen.getByLabelText("Kết quả Bạch cầu (WBC)"), {
      target: { value: "7" },
    });
    fireEvent.change(screen.getByLabelText("Kết quả Huyết sắc tố (HGB)"), {
      target: { value: "140" },
    });
    fireEvent.change(screen.getByLabelText("Kết quả Tiểu cầu (PLT)"), {
      target: { value: "200" },
    });
    expect(screen.getByText("Ngoài khoảng")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Duyệt kết quả mẫu" }));
    expect(screen.getByRole("status")).toHaveTextContent(
      "Không có dữ liệu nào được gửi đi",
    );
  });
});
