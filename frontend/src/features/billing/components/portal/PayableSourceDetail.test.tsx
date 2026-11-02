import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { resetPatientBillingMock } from "../../api/patientBillingApi";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { PayableSourceDetail } from "./PayableSourceDetail";

// Testing Library chuẩn hóa khoảng trắng; số tiền dùng khoảng trắng không ngắt nên chuẩn hóa thêm.
const money = (amount: number) => formatCurrencyVnd(amount).replace(/\s/g, " ");

beforeEach(() => {
  resetPatientBillingMock();
});

describe("PayableSourceDetail", () => {
  it("chỉ tick được khoản chờ thanh toán; tạm tính cộng đúng các khoản đã chọn", async () => {
    const user = userEvent.setup();
    render(<PayableSourceDetail id="vis-an-today" kind="visit" />);

    const mri = await screen.findByRole("checkbox", {
      name: "MRI cột sống thắt lưng",
    });
    // Checkbox của base-ui dùng aria-disabled (không phải thuộc tính disabled của nút).
    expect(
      screen.getByRole("checkbox", { name: "X-quang khớp gối" }),
    ).toHaveAttribute("aria-disabled", "true");
    expect(
      screen.getByRole("checkbox", { name: "Siêu âm khớp vai" }),
    ).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "Lượt khám 23/10/2026 · Nguyễn Văn An (Bản thân)",
    );
    expect(
      screen.queryByRole("link", { name: "Thanh toán qua VNPay" }),
    ).not.toBeInTheDocument();

    await user.click(mri);
    await user.click(screen.getByRole("checkbox", { name: "Công thức máu" }));
    expect(screen.getByText("Tạm tính").nextElementSibling).toHaveTextContent(
      money(1450000),
    );
    expect(
      screen.getByRole("link", { name: "Thanh toán qua VNPay" }),
    ).toHaveAttribute(
      "href",
      "/portal/payments/pay?source=visit&id=vis-an-today&items=svc-pending%2Csvc-cbc",
    );

    await user.click(mri);
    expect(screen.getByText("Tạm tính").nextElementSibling).toHaveTextContent(
      money(250000),
    );
  });

  it("khoản đã trả có liên kết tới phiếu thu, khoản đã hủy ghi lý do", async () => {
    render(<PayableSourceDetail id="vis-an-today" kind="visit" />);
    const receipt = await screen.findByRole("link", {
      name: "Phiếu thu PT-1029",
    });
    expect(receipt).toHaveAttribute("href", "/portal/payments/history/pm-1029");
    expect(screen.getByText("Bác sĩ thay bằng MRI")).toBeInTheDocument();
  });

  it("nguồn không còn khả dụng hiện MSG-41 và nút thử lại", async () => {
    render(<PayableSourceDetail id="vis-khong-co" kind="visit" />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Không tìm thấy thông tin cần xử lý hoặc thông tin không còn khả dụng.",
    );
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
  });

  it("nguồn ngoài phạm vi tài khoản hiện MSG-40", async () => {
    render(<PayableSourceDetail id="vis-forbidden" kind="visit" />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Bạn không có quyền truy cập hoặc thực hiện thao tác này.",
    );
  });
});
