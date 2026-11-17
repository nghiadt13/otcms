import { render, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { ReceiptView } from "./ReceiptView";
const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
beforeEach(() => { request.mockReset(); });
it("hiện phiếu thu và chính sách", async () => {
  request.mockResolvedValue({ id: "p1", receiptNumber: "RC-123", invoiceId: "i1", invoiceNumber: "INV-1", patientProfileId: "pt", patientName: "Nguyễn An", amount: 50000, currency: "VND", method: "CASH", channel: "COUNTER", confirmedAt: "2026-11-17T08:00:00Z", receivedByName: null, providerTransactionReference: null, items: [{ itemId: "x", lineNumber: 1, description: "Phí khám", quantity: 1, unitPrice: 50000, lineTotal: 50000, status: "PAID", refundReason: null }] });
  render(<ReceiptView paymentId="p1" />);
  expect(await screen.findByText("Số phiếu RC-123")).toBeInTheDocument(); expect(screen.getByText("Nguyễn An")).toBeInTheDocument(); expect(screen.getByText("Phí khám")).toBeInTheDocument(); expect(screen.getByText(/Khoản đã thanh toán không được hoàn lại/)).toBeInTheDocument();
  expect(request.mock.calls[0][0]).toBe("/api/v1/payments/p1");
});
it("hiện không tìm thấy cho 404", async () => {
  request.mockRejectedValue(new ApiError(404)); render(<ReceiptView paymentId="missing" />);
  expect(await screen.findByRole("heading", { name: "Không tìm thấy" })).toBeInTheDocument();
});
