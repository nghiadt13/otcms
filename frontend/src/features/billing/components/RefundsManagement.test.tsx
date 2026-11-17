import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { RefundsManagement } from "./RefundsManagement";
const { request, success } = vi.hoisted(() => ({ request: vi.fn(), success: vi.fn() }));
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
vi.mock("@/lib/messages/notify", () => ({ notifySuccess: success, notifyError: vi.fn() }));
it("xác nhận khoản đã hoàn ngoài hệ thống", async () => {
  const item = { itemId: "x1", invoiceId: "i1", invoiceNumber: "INV-1", patientId: "p1", patientName: "Nguyễn An", description: "Phí khám", amount: 50000, status: "REFUND_PENDING", reason: "Bác sĩ nghỉ", refundedAt: null, refundedByAccountId: null };
  request.mockImplementation(async (path: string) => path.includes("refund-confirmation") ? item : { items: [item], page: 0, size: 20, totalItems: 1, totalPages: 1 });
  render(<RefundsManagement />); await screen.findByText("Nguyễn An");
  await userEvent.click(within(screen.getByRole("row", { name: /Nguyễn An/ })).getByRole("button", { name: "Đã hoàn tiền" })); const dialog = screen.getByRole("dialog");
  await userEvent.type(within(dialog).getByLabelText("Ghi chú (tùy chọn)"), "Tiền mặt"); await userEvent.click(within(dialog).getByRole("button", { name: "Đã hoàn tiền" }));
  await waitFor(() => expect(request).toHaveBeenCalledWith("/api/v1/invoice-items/x1/refund-confirmation", expect.objectContaining({ method: "POST", body: JSON.stringify({ note: "Tiền mặt" }) })));
  expect(success).toHaveBeenCalledWith("MSG-96", { amount: formatCurrencyVnd(50000), patientName: "Nguyễn An" });
});
