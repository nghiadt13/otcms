import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { resetPatientBillingMock } from "../../api/patientBillingApi";
import { ReceiptView } from "./ReceiptView";

beforeEach(() => {
  resetPatientBillingMock();
});

describe("ReceiptView", () => {
  it("phiếu thu của thanh toán đã hoàn tất: số phiếu, khoản, tổng và phương thức", async () => {
    render(<ReceiptView paymentId="pm-1029" />);

    expect(
      await screen.findByRole("heading", { level: 1, name: "Phiếu thu" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Số phiếu PT-1029")).toBeInTheDocument();
    expect(screen.getByText("Tiền mặt")).toBeInTheDocument();
    expect(screen.getByText("Tổng cộng").nextElementSibling).toHaveTextContent(
      "200.000 ₫",
    );
    expect(screen.getByText("X-quang khớp gối")).toBeInTheDocument();
  });

  it("mã không thuộc tài khoản hoặc không có: hiện không tìm thấy", async () => {
    render(<ReceiptView paymentId="pm-khong-co" />);
    expect(
      await screen.findByRole("heading", { level: 1, name: "Không tìm thấy" }),
    ).toBeInTheDocument();
  });
});
