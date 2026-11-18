import { render, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { MyPaymentHistory } from "./MyPaymentHistory";
const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
vi.mock("@/hooks/useLinkedProfiles", () => ({
  useLinkedProfiles: () => ({ profiles: [] }),
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }) }));
beforeEach(() => request.mockReset());
it("liên kết giao dịch tới phiếu thu", async () => {
  request.mockResolvedValue({
    items: [
      {
        id: "p1",
        invoiceId: "i1",
        patientId: "pt",
        invoiceNumber: "INV-1",
        amount: 50000,
        currency: "VND",
        channel: "ONLINE",
        confirmedAt: "2026-11-17T08:00:00Z",
      },
    ],
    page: 0,
    size: 20,
    totalItems: 1,
    totalPages: 1,
  });
  render(<MyPaymentHistory />);
  expect(await screen.findByRole("link", { name: /INV-1/ })).toHaveAttribute(
    "href",
    "/portal/payments/history/p1",
  );
  expect(request.mock.calls[0][0]).toContain("/api/v1/payments");
});
it("chặn khoảng ngày sai", () => {
  render(<MyPaymentHistory from="2026-11-20" to="2026-11-01" />);
  expect(screen.getByRole("alert")).toHaveTextContent(
    formatAppMessage("MSG-54"),
  );
  expect(request).not.toHaveBeenCalled();
});
