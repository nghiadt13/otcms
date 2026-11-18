import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { formatAppMessage } from "@/lib/messages/appMessages";
const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
beforeEach(() => request.mockReset());
import { UnpaidItemsManagement } from "./UnpaidItemsManagement";
it("hiển thị khoản và trạng thái từ API", async () => {
  request.mockResolvedValue({
    items: [
      {
        itemId: "x",
        invoiceId: "i",
        invoiceNumber: "INV-1",
        patientProfileId: "p",
        patientName: "Nguyễn An",
        sourceType: "APPOINTMENT",
        visitId: null,
        appointmentId: "a",
        serviceId: null,
        description: "Phí khám",
        quantity: 1,
        unitPrice: 50000,
        lineTotal: 50000,
        status: "PAYMENT_PENDING",
        orderedAt: "2026-11-17T08:00:00Z",
      },
    ],
    page: 0,
    size: 20,
    totalItems: 1,
    totalPages: 1,
  });
  render(<UnpaidItemsManagement />);
  expect(await screen.findByText("Nguyễn An")).toBeInTheDocument();
  expect(screen.getByText("Đang thanh toán")).toBeInTheDocument();
  expect(request.mock.calls[0][0]).toContain("/api/v1/invoice-items/unpaid");
});
it("chặn khoảng ngày sai", async () => {
  request.mockResolvedValue({
    items: [],
    page: 0,
    size: 20,
    totalItems: 0,
    totalPages: 0,
  });
  render(<UnpaidItemsManagement />);
  await screen.findByText(
    "Không có khoản nào còn phải thu trong khoảng ngày này.",
  );
  const count = request.mock.calls.length;
  fireEvent.change(screen.getByLabelText("Từ ngày"), {
    target: { value: "2099-01-01" },
  });
  expect(screen.getByRole("alert")).toHaveTextContent(
    formatAppMessage("MSG-54"),
  );
  await waitFor(() => expect(request).toHaveBeenCalledTimes(count));
});
