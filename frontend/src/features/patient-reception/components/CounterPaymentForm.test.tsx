import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import type { BillingInvoice } from "@/lib/api/billingApi";
import { CounterPaymentForm } from "./CounterPaymentForm";
import { ReceptionProvider } from "./ReceptionProvider";
const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
const invoice: BillingInvoice = {
  id: "i1",
  invoiceNumber: "INV-1",
  appointmentId: "a1",
  visitId: null,
  status: "OPEN",
  totalAmount: 100,
  paidAmount: 0,
  outstandingBalance: 100,
  issuedAt: "2026-11-17",
  items: [
    {
      id: "x1",
      lineNumber: 1,
      serviceId: null,
      description: "Phí khám",
      quantity: 1,
      unitPrice: 100,
      lineTotal: 100,
      status: "PENDING",
    },
  ],
};
beforeEach(() => {
  request.mockReset();
  sessionStorage.clear();
});
it("thu tiền mặt theo khoản đã chọn", async () => {
  request.mockResolvedValue({ id: "pay1" });
  const paid = vi.fn();
  render(
    <ReceptionProvider>
      <CounterPaymentForm
        invoice={invoice}
        title="Thu tiền"
        description="Thu tại quầy"
        onClose={vi.fn()}
        onPaid={paid}
      />
    </ReceptionProvider>,
  );
  await userEvent.click(screen.getByRole("checkbox", { name: /Đã nhận tiền/ }));
  await userEvent.click(
    screen.getByRole("button", { name: "Xác nhận thu tiền" }),
  );
  await waitFor(() => expect(paid).toHaveBeenCalledWith("pay1"));
  expect(request).toHaveBeenCalledWith(
    "/api/v1/invoices/i1/counter-payments",
    expect.objectContaining({
      method: "POST",
      body: JSON.stringify({ itemIds: ["x1"], method: "CASH" }),
      headers: expect.objectContaining({
        "Idempotency-Key": expect.any(String),
      }),
    }),
  );
});
it("tạo mã QR theo khoản đã chọn", async () => {
  request.mockResolvedValue({
    id: "pay1",
    status: "PENDING",
    amount: 100,
    qrCode: null,
    expiresAt: null,
  });
  render(
    <ReceptionProvider>
      <CounterPaymentForm
        invoice={invoice}
        title="Thu tiền"
        description="Thu tại quầy"
        onClose={vi.fn()}
        onPaid={vi.fn()}
      />
    </ReceptionProvider>,
  );
  await userEvent.selectOptions(
    screen.getByLabelText("Phương thức thanh toán"),
    "COUNTER_QR",
  );
  await userEvent.click(screen.getByRole("button", { name: "Tạo mã QR" }));
  await waitFor(() =>
    expect(request).toHaveBeenCalledWith(
      "/api/v1/invoices/i1/payment-attempts",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ itemIds: ["x1"], channel: "COUNTER_QR" }),
        headers: expect.objectContaining({
          "Idempotency-Key": expect.any(String),
        }),
      }),
    ),
  );
});
