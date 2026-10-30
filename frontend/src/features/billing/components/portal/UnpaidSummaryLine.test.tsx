import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { resetPatientBillingMock } from "../../api/patientBillingApi";
import { UnpaidSummaryLine } from "./UnpaidSummaryLine";
import { formatAppMessage } from "@/lib/messages/appMessages";
beforeEach(() => {
  resetPatientBillingMock();
});

describe("UnpaidSummaryLine", () => {
  it("cộng số khoản và tổng tiền chờ của mọi hồ sơ, dẫn tới trang Thanh toán", async () => {
    render(<UnpaidSummaryLine />);
    const link = await screen.findByRole("link", {
      name: /Bạn có 4 khoản cần thanh toán/,
    });
    expect(link).toHaveAttribute("href", "/portal/payments");
    expect(screen.getByText("Tổng 2.650.000 ₫")).toBeInTheDocument();
  });

  it("lỗi tải: hiện câu lỗi và nút Thử lại", async () => {
    resetPatientBillingMock({ failSources: true });
    render(<UnpaidSummaryLine />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-04"),
    );
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
  });
});
