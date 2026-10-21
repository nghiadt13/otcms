import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as paymentApi from "../api/paymentApi";
import { PAYMENT_FORBIDDEN_ID, PAYMENT_SEED } from "../api/paymentMockData";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";

// formatCurrencyVnd dùng khoảng trắng không ngắt; Testing Library chuẩn hóa DOM về khoảng trắng thường.
const money = (amount: number) => formatCurrencyVnd(amount).replace(/\s/g, " ");
import { PaymentHistoryDetailSheet } from "./PaymentHistoryDetailSheet";
import { PaymentHistoryManagement } from "./PaymentHistoryManagement";

// Debounce 250 ms + độ trễ API giả: để dư thời gian cho máy chậm.
const SLOW = { timeout: 4000 };
const loaded = () => screen.findByText(/Trang 1\/3/, undefined, SLOW);
const searchBox = () => screen.getByRole("searchbox", { name: "Tìm thanh toán" });
const viewButtons = () => screen.queryAllByRole("button", { name: /^Xem phiếu thu/ });

async function search(user: ReturnType<typeof userEvent.setup>, text: string) {
  await user.click(searchBox());
  await user.paste(text);
}

beforeEach(() => {
  // Chỉ giả Date để khoảng ngày mặc định là 2026-09-20 → 2026-10-20; timer vẫn thật.
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-10-20T03:00:00Z"));
});
afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe("PaymentHistoryManagement", () => {
  it("hiện h1, 8 cột, 20 dòng và dòng tổng lấy từ summary; không có nút chính", async () => {
    render(<PaymentHistoryManagement />);
    await loaded();
    expect(screen.getByRole("heading", { level: 1, name: "Lịch sử thanh toán" })).toBeInTheDocument();
    expect(screen.getAllByRole("columnheader")).toHaveLength(8);
    expect(viewButtons()).toHaveLength(20);

    const inRange = PAYMENT_SEED.filter((p) => p.paidAt >= "2026-09-20");
    const cash = inRange.filter((p) => p.method === "CASH").reduce((s, p) => s + p.amount, 0);
    const vnpay = inRange.filter((p) => p.method === "VNPAY").reduce((s, p) => s + p.amount, 0);
    expect(screen.getByText("Tổng kỳ đang lọc")).toBeInTheDocument();
    expect(screen.getByText(money(cash + vnpay))).toBeInTheDocument();
    expect(
      screen.getByText(`Tiền mặt ${money(cash)} · VNPay ${money(vnpay)}`),
    ).toBeInTheDocument();
  });

  it("không có nút hoàn tiền, hủy, sửa, thu tiền, in phiếu, xuất", async () => {
    render(<PaymentHistoryManagement />);
    await loaded();
    for (const button of screen.getAllByRole("button"))
      expect(button).not.toHaveAccessibleName(/hoàn|hủy|sửa|thu tiền|in phiếu|xuất/i);
  });

  it("ô số tiền căn phải và tabular-nums; thu ngân VNPay là —", async () => {
    render(<PaymentHistoryManagement />);
    await loaded();
    const rows = screen.getAllByRole("row").filter((row) => within(row).queryByRole("button", { name: /^Xem phiếu thu/ }));
    const cell = within(rows[0]).getByText(/₫$/).closest("td");
    expect(cell).toHaveClass("text-right", "tabular-nums");
    const vnpayRow = rows.find((row) => within(row).queryByText("VNPay"));
    expect(vnpayRow).toBeDefined();
    expect(within(vnpayRow!).getByText("—")).toBeInTheDocument();
  });

  it("chọn Tiền mặt thì gọi method=CASH và về trang 0", async () => {
    const user = userEvent.setup();
    const spy = vi.spyOn(paymentApi, "listPayments");
    render(<PaymentHistoryManagement />);
    await loaded();
    await user.click(screen.getByRole("button", { name: "Sau" }));
    await screen.findByText(/Trang 2\/3/, undefined, SLOW);

    await user.click(screen.getByRole("button", { name: "Tiền mặt" }));
    await waitFor(() =>
      expect(spy).toHaveBeenLastCalledWith(
        expect.objectContaining({ method: "CASH", page: 0 }),
        expect.anything(),
      ),
    SLOW);
    await waitFor(() => expect(screen.getByText(/Trang 1\/2/)).toBeInTheDocument(), SLOW);
  });

  it("gõ từ khóa thì gọi với q, xóa thì q trống", async () => {
    const user = userEvent.setup();
    const spy = vi.spyOn(paymentApi, "listPayments");
    render(<PaymentHistoryManagement />);
    await loaded();
    await search(user, "Bích");
    await waitFor(() =>
      expect(spy).toHaveBeenLastCalledWith(expect.objectContaining({ q: "Bích" }), expect.anything()),
    SLOW);
    await user.clear(searchBox());
    await waitFor(() =>
      expect(spy).toHaveBeenLastCalledWith(expect.objectContaining({ q: "" }), expect.anything()),
    SLOW);
  });

  it("từ > đến: MSG-54 dưới ô, bảng nhắc chọn lại, không Thử lại, không dòng tổng, không gọi mạng", async () => {
    const spy = vi.spyOn(paymentApi, "listPayments");
    render(<PaymentHistoryManagement />);
    await loaded();
    const calls = spy.mock.calls.length;

    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "2026-09-01" } });
    expect(
      await screen.findByText("Tiêu chí lọc chưa hợp lệ. Vui lòng kiểm tra lại."),
    ).toBeInTheDocument();
    expect(screen.getByText("Chọn lại khoảng ngày để xem thanh toán.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
    expect(screen.queryByText("Tổng kỳ đang lọc")).not.toBeInTheDocument();
    expect(spy.mock.calls.length).toBe(calls);
  });

  it("lỗi ERR-500: hiện câu lỗi + Thử lại, bấm thì gọi lại", async () => {
    const user = userEvent.setup();
    const spy = vi.spyOn(paymentApi, "listPayments");
    render(<PaymentHistoryManagement />);
    await loaded();
    await search(user, "ERR-500");
    const retry = await screen.findByRole("button", { name: "Thử lại" }, SLOW);
    expect(screen.getByRole("alert")).toHaveTextContent("Hệ thống đang gặp sự cố");
    const calls = spy.mock.calls.length;
    await user.click(retry);
    await waitFor(() => expect(spy.mock.calls.length).toBe(calls + 1), SLOW);
  });

  it("hai trạng thái trống dùng đúng hai câu", async () => {
    const user = userEvent.setup();
    render(<PaymentHistoryManagement />);
    await loaded();
    fireEvent.change(screen.getByLabelText("Từ ngày"), { target: { value: "2026-01-01" } });
    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "2026-01-31" } });
    await waitFor(
      () => expect(screen.getByText("Chưa có khoản thanh toán nào trong khoảng ngày này. Khoản thu ở Lễ tân hoặc qua VNPay sẽ hiện ở đây sau khi hoàn tất.")).toBeInTheDocument(),
      SLOW,
    );

    await search(user, "zzz");
    await waitFor(
      () => expect(screen.getByText("Không có khoản nào khớp bộ lọc. Đổi khoảng ngày, từ khóa hoặc phương thức để xem lại.")).toBeInTheDocument(),
      SLOW,
    );
  });

  it("bấm Xem mở Sheet chỉ có nút Đóng; Đóng thì Sheet biến mất", async () => {
    const user = userEvent.setup();
    render(<PaymentHistoryManagement />);
    await loaded();
    await user.click(viewButtons()[0]);
    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findByText("Khoản đã trả", undefined, SLOW)).toBeInTheDocument();
    expect(within(dialog).getByText("Tổng")).toBeInTheDocument();
    const buttons = within(dialog).getAllByRole("button");
    expect(buttons).toHaveLength(1);
    expect(buttons[0]).toHaveAccessibleName("Đóng");
    await user.click(buttons[0]);
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("allowedActions rỗng thì không có nút Xem", async () => {
    const real = await paymentApi.listPayments({ from: "2026-09-20", to: "2026-10-20", q: "", method: "" });
    vi.spyOn(paymentApi, "listPayments").mockResolvedValue({
      ...real,
      items: real.items.map((item) => ({ ...item, allowedActions: [] })),
    });
    render(<PaymentHistoryManagement />);
    await loaded();
    expect(viewButtons()).toHaveLength(0);
  });
});

describe("PaymentHistoryDetailSheet", () => {
  it("403: hiện câu lỗi MSG-50, không Thử lại, vẫn có Đóng", async () => {
    render(
      <PaymentHistoryDetailSheet paymentId={PAYMENT_FORBIDDEN_ID} receiptNumber="PT-2026-000999" onClose={() => {}} />,
    );
    const alert = await screen.findByRole("alert", undefined, SLOW);
    expect(alert).toHaveTextContent("không còn hoặc bạn không đủ quyền");
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Đóng" })).toBeInTheDocument();
  });
});
