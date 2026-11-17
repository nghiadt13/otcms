import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { AppointmentPayOsPayment } from "./AppointmentPayOsPayment";

// Mock ở ranh giới `apiRequest` (không mock hook/component), theo quy ước test frontend.
const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));

const invoice = (overrides: Record<string, unknown> = {}) => ({
  id: "inv-1",
  invoiceNumber: "OTC-1",
  appointmentId: "apt-1",
  visitId: null,
  status: "OPEN",
  totalAmount: 500000,
  paidAmount: 0,
  outstandingBalance: 500000,
  issuedAt: "2026-11-09T09:00:00Z",
  items: [
    {
      lineNumber: 1,
      description: "Khám nội",
      quantity: 1,
      unitPrice: 500000,
      lineTotal: 500000,
    },
  ],
  ...overrides,
});

const attempt = (status = "PENDING") => ({
  id: "att-1",
  invoiceId: "inv-1",
  channel: "ONLINE",
  provider: "PAYOS",
  amount: 500000,
  currency: "VND",
  status,
  checkoutUrl: "https://pay.payos.vn/web/x",
  qrCode: "0002010102",
  expiresAt: new Date(Date.now() + 10 * 60_000).toISOString(),
  createdAt: new Date().toISOString(),
  confirmedAt: null,
  reconciliationReason: null,
});

const notPayable = () =>
  new ApiError(409, { errorCode: "INVOICE_NOT_PAYABLE", detail: "x" });

/** Định tuyến `apiRequest` theo đường dẫn: lịch → hóa đơn; hóa đơn → mã thanh toán. */
function route(handlers: {
  invoiceOfAppointment?: () => unknown;
  invoiceById?: () => unknown;
  createAttempt?: () => unknown;
}) {
  request.mockImplementation(async (path: string, init?: RequestInit) => {
    const method = init?.method ?? "GET";
    if (path.includes("/appointments/") && path.endsWith("/invoices"))
      return (handlers.invoiceOfAppointment ?? (() => invoice()))();
    if (path.endsWith("/payment-attempts") && method === "POST")
      return (handlers.createAttempt ?? (() => attempt()))();
    if (path.startsWith("/api/v1/invoices/"))
      return (handlers.invoiceById ?? (() => invoice()))();
    if (path.includes("/payment-attempts/")) return attempt();
    throw new Error(`unexpected request ${method} ${path}`);
  });
}

beforeEach(() => { request.mockReset(); window.sessionStorage.clear(); });
describe("AppointmentPayOsPayment", () => {
  it("hiện QR và chính sách không hoàn phí", async () => {
    route({}); render(<AppointmentPayOsPayment appointmentId="apt-1" onFinish={() => {}} />);
    expect(await screen.findByLabelText("Quét mã QR")).toBeInTheDocument();
    expect(screen.getByText(/Khoản đã thanh toán không được hoàn lại/)).toBeInTheDocument();
  });
  it("hiện đã trả khi hóa đơn hết dư nợ", async () => {
    route({ invoiceOfAppointment: () => invoice({ status: "PAID", outstandingBalance: 0, paidAmount: 500000 }) });
    render(<AppointmentPayOsPayment appointmentId="apt-1" onFinish={() => {}} />);
    expect(await screen.findByRole("button", { name: "Hoàn tất" })).toBeInTheDocument();
  });
  it("hiện hóa đơn không thanh toán được khi VOID", async () => {
    route({ invoiceById: () => invoice({ status: "VOID" }) }); render(<AppointmentPayOsPayment invoiceId="inv-1" />);
    expect(await screen.findByText("Hóa đơn không còn thanh toán được")).toBeInTheDocument();
  });
  it("cho tạo mã mới khi attempt hết hạn", async () => {
    route({ createAttempt: () => attempt("EXPIRED") }); render(<AppointmentPayOsPayment invoiceId="inv-1" />);
    expect(await screen.findByRole("button", { name: "Tạo mã thanh toán mới" })).toBeInTheDocument();
  });
  it("phân biệt hóa đơn vừa trả khi tạo mã bị từ chối", async () => {
    route({ createAttempt: () => { throw notPayable(); }, invoiceById: () => invoice({ status: "PAID", outstandingBalance: 0, paidAmount: 500000 }) });
    render(<AppointmentPayOsPayment appointmentId="apt-1" onFinish={() => {}} />);
    expect(await screen.findByRole("button", { name: "Hoàn tất" })).toBeInTheDocument();
  });
});
