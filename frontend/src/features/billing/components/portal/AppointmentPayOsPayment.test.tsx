import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
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

const inTheFuture = (ms: number) => new Date(Date.now() + ms).toISOString();
const inThePast = (ms: number) => new Date(Date.now() - ms).toISOString();

beforeEach(() => {
  request.mockReset();
  window.sessionStorage.clear();
});

afterEach(() => {
  vi.useRealTimers();
});

describe("AppointmentPayOsPayment - chế độ lịch có hạn giữ chỗ", () => {
  it("hiện đếm ngược và QR khi còn hạn", async () => {
    route({});
    render(
      <AppointmentPayOsPayment
        appointmentId="apt-1"
        onFinish={() => {}}
        paymentDeadline={inTheFuture(90_000)}
      />,
    );
    const timer = await screen.findByText(/Giữ lịch còn/);
    expect(timer.textContent).toMatch(/00:01:(29|30)/);
    expect(screen.getByLabelText("Quét mã QR")).toBeInTheDocument();
    expect(
      screen.queryByText("Hết thời gian giữ lịch"),
    ).not.toBeInTheDocument();
  });

  it("hết hạn: thay bằng 'Hết thời gian giữ lịch' + Đặt lịch lại, không còn 'Tạo mã mới'", async () => {
    const onBookAgain = vi.fn();
    route({});
    render(
      <AppointmentPayOsPayment
        appointmentId="apt-1"
        onBookAgain={onBookAgain}
        onFinish={() => {}}
        paymentDeadline={inThePast(1_000)}
      />,
    );
    expect(
      await screen.findByText("Hết thời gian giữ lịch"),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("Quét mã QR")).not.toBeInTheDocument();
    expect(screen.queryByText("Tạo mã thanh toán mới")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Đặt lịch lại" }));
    expect(onBookAgain).toHaveBeenCalledTimes(1);
  });

  it("đếm ngược về 0 thì chuyển sang hết giữ lịch", async () => {
    route({});
    render(
      <AppointmentPayOsPayment
        appointmentId="apt-1"
        onFinish={() => {}}
        paymentDeadline={inTheFuture(1_500)}
      />,
    );
    expect(await screen.findByText(/Giữ lịch còn/)).toBeInTheDocument();
    expect(
      await screen.findByText("Hết thời gian giữ lịch", {}, { timeout: 4_000 }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Giữ lịch còn/)).not.toBeInTheDocument();
  });

  it("INVOICE_NOT_PAYABLE khi tạo mã: cùng trạng thái hết giữ lịch, không báo lỗi thô", async () => {
    route({
      createAttempt: () => {
        throw notPayable();
      },
      invoiceById: () => invoice({ status: "VOID" }),
    });
    render(
      <AppointmentPayOsPayment
        appointmentId="apt-1"
        onFinish={() => {}}
        paymentDeadline={inTheFuture(60_000)}
      />,
    );
    expect(
      await screen.findByText("Hết thời gian giữ lịch"),
    ).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.queryByText("Tạo mã thanh toán mới")).not.toBeInTheDocument();
  });

  it("hóa đơn đã VOID: hết giữ lịch ngay, không tạo mã thanh toán", async () => {
    route({ invoiceOfAppointment: () => invoice({ status: "VOID" }) });
    render(
      <AppointmentPayOsPayment appointmentId="apt-1" onFinish={() => {}} />,
    );
    expect(
      await screen.findByText("Hết thời gian giữ lịch"),
    ).toBeInTheDocument();
    expect(
      request.mock.calls.some(([path]) =>
        String(path).endsWith("/payment-attempts"),
      ),
    ).toBe(false);
  });

  it("INVOICE_NOT_PAYABLE nhưng hóa đơn vừa trả đủ: hiện đã thanh toán, không báo hết giữ lịch", async () => {
    route({
      createAttempt: () => {
        throw notPayable();
      },
      invoiceById: () =>
        invoice({ status: "PAID", paidAmount: 500000, outstandingBalance: 0 }),
    });
    render(
      <AppointmentPayOsPayment appointmentId="apt-1" onFinish={() => {}} />,
    );
    expect(
      await screen.findByRole("button", { name: "Hoàn tất" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("Hết thời gian giữ lịch"),
    ).not.toBeInTheDocument();
  });

  it("xóa khóa idempotency khi lịch hết giữ lịch", async () => {
    route({
      createAttempt: () => {
        throw notPayable();
      },
      invoiceById: () => invoice({ status: "VOID" }),
    });
    render(
      <AppointmentPayOsPayment appointmentId="apt-1" onFinish={() => {}} />,
    );
    await screen.findByText("Hết thời gian giữ lịch");
    expect(
      window.sessionStorage.getItem("otcms:payos:idempotency:inv-1"),
    ).toBeNull();
  });

  it("nút Hủy → hộp thoại xác nhận → gọi onCancelHold", async () => {
    const onCancelHold = vi.fn().mockResolvedValue(undefined);
    route({});
    render(
      <AppointmentPayOsPayment
        appointmentId="apt-1"
        onCancelHold={onCancelHold}
        onFinish={() => {}}
        paymentDeadline={inTheFuture(60_000)}
      />,
    );
    await userEvent.click(
      await screen.findByRole("button", { name: "Hủy thanh toán" }),
    );
    const dialog = await screen.findByRole("alertdialog");
    expect(
      within(dialog).getByText("Bạn có chắc muốn hủy thanh toán?"),
    ).toBeInTheDocument();
    expect(onCancelHold).not.toHaveBeenCalled();
    await userEvent.click(
      within(dialog).getByRole("button", { name: "Hủy lịch" }),
    );
    await waitFor(() => expect(onCancelHold).toHaveBeenCalledTimes(1));
  });

  it("chọn 'Tiếp tục thanh toán' thì không hủy", async () => {
    const onCancelHold = vi.fn();
    route({});
    render(
      <AppointmentPayOsPayment
        appointmentId="apt-1"
        onCancelHold={onCancelHold}
        onFinish={() => {}}
      />,
    );
    await userEvent.click(
      await screen.findByRole("button", { name: "Hủy thanh toán" }),
    );
    await userEvent.click(
      await screen.findByRole("button", { name: "Tiếp tục thanh toán" }),
    );
    expect(onCancelHold).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Quét mã QR")).toBeInTheDocument();
  });

  it("onCancelHold lỗi: giữ hộp thoại và báo thử lại", async () => {
    const onCancelHold = vi.fn().mockRejectedValue(new Error("boom"));
    route({});
    render(
      <AppointmentPayOsPayment
        appointmentId="apt-1"
        onCancelHold={onCancelHold}
        onFinish={() => {}}
      />,
    );
    await userEvent.click(
      await screen.findByRole("button", { name: "Hủy thanh toán" }),
    );
    const dialog = await screen.findByRole("alertdialog");
    await userEvent.click(
      within(dialog).getByRole("button", { name: "Hủy lịch" }),
    );
    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "Chưa hủy được",
    );
  });

  it("không còn lối 'Thanh toán sau tại quầy'", async () => {
    route({});
    render(
      <AppointmentPayOsPayment
        appointmentId="apt-1"
        onCancelHold={() => {}}
        onFinish={() => {}}
      />,
    );
    await screen.findByLabelText("Quét mã QR");
    expect(screen.queryByText(/sau tại quầy/i)).not.toBeInTheDocument();
  });
});

describe("AppointmentPayOsPayment - trang /payments/appointment (không nhúng)", () => {
  it("lịch đã hết hạn/hủy: thông báo + link Đặt lịch lại, không lỗi thô", async () => {
    route({
      invoiceOfAppointment: () => {
        throw new ApiError(409, { errorCode: "SOURCE_NOT_BILLABLE" });
      },
    });
    render(<AppointmentPayOsPayment appointmentId="apt-1" />);
    expect(
      await screen.findByText("Hết thời gian giữ lịch"),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Đặt lịch lại" })).toHaveAttribute(
      "href",
      "/portal/appointments/new",
    );
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});

describe("AppointmentPayOsPayment - chế độ hóa đơn lẻ", () => {
  it("không đổi: hiện QR, không có đếm ngược giữ lịch và không có nút Hủy", async () => {
    route({});
    render(
      <AppointmentPayOsPayment
        invoiceId="inv-1"
        onCancelHold={() => {}}
        paymentDeadline={inThePast(1_000)}
      />,
    );
    expect(await screen.findByLabelText("Quét mã QR")).toBeInTheDocument();
    expect(screen.queryByText(/Giữ lịch còn/)).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Hủy thanh toán" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText("Hết thời gian giữ lịch"),
    ).not.toBeInTheDocument();
  });

  it("hóa đơn VOID: thông báo rõ, không có 'Tạo mã mới'", async () => {
    route({ invoiceById: () => invoice({ status: "VOID" }) });
    render(<AppointmentPayOsPayment invoiceId="inv-1" />);
    expect(
      await screen.findByText("Hóa đơn không còn thanh toán được"),
    ).toBeInTheDocument();
    expect(screen.getByText("Đã hủy")).toBeInTheDocument();
    expect(screen.queryByText("Tạo mã thanh toán mới")).not.toBeInTheDocument();
    expect(
      screen.queryByText("Hết thời gian giữ lịch"),
    ).not.toBeInTheDocument();
  });

  it("INVOICE_NOT_PAYABLE khi tạo mã: thông báo rõ, không báo lỗi thô", async () => {
    let reads = 0;
    route({
      invoiceById: () => {
        reads += 1;
        return reads === 1 ? invoice() : invoice({ status: "VOID" });
      },
      createAttempt: () => {
        throw notPayable();
      },
    });
    render(<AppointmentPayOsPayment invoiceId="inv-1" />);
    expect(
      await screen.findByText("Hóa đơn không còn thanh toán được"),
    ).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
