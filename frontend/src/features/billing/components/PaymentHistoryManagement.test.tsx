import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { formatAppMessage } from "@/lib/messages/appMessages";
const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
beforeEach(() => request.mockReset());
import { PaymentHistoryManagement } from "./PaymentHistoryManagement";
const payment = {
  id: "p1",
  invoiceId: "i1",
  patientId: "patient1",
  invoiceNumber: "INV-1",
  amount: 50000,
  currency: "VND",
  channel: "COUNTER",
  confirmedAt: "2026-11-17T08:00:00Z",
};
it("hiển thị giao dịch từ API", async () => {
  request.mockResolvedValue({
    items: [payment],
    page: 0,
    size: 20,
    totalItems: 1,
    totalPages: 1,
  });
  render(<PaymentHistoryManagement />);
  expect(await screen.findByText("INV-1")).toBeInTheDocument();
  expect(screen.getByText("Tiền mặt tại quầy")).toBeInTheDocument();
  expect(request.mock.calls[0][0]).toContain("/api/v1/payments");
});
it("chặn khoảng ngày sai", async () => {
  request.mockResolvedValue({
    items: [],
    page: 0,
    size: 20,
    totalItems: 0,
    totalPages: 0,
  });
  render(<PaymentHistoryManagement />);
  await screen.findByText("Chưa có giao dịch nào trong khoảng ngày này.");
  const count = request.mock.calls.length;
  fireEvent.change(screen.getByLabelText("Từ ngày"), {
    target: { value: "2099-01-01" },
  });
  expect(screen.getByRole("alert")).toHaveTextContent(
    formatAppMessage("MSG-54"),
  );
  await waitFor(() => expect(request).toHaveBeenCalledTimes(count));
});
