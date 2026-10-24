import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import * as unpaidItemApi from "../api/unpaidItemApi";
import {
  UNPAID_ITEM_NOT_FOUND_ID,
  UNPAID_ITEM_SEED,
  resetUnpaidItemMockStore,
} from "../api/unpaidItemMockData";
import { UnpaidItemDetailSheet } from "./UnpaidItemDetailSheet";
import { UnpaidItemsManagement } from "./UnpaidItemsManagement";
import { formatAppMessage } from "@/lib/messages/appMessages";
// formatCurrencyVnd dùng khoảng trắng không ngắt; Testing Library chuẩn hóa DOM về khoảng trắng thường.
const money = (amount: number) => formatCurrencyVnd(amount).replace(/\s/g, " ");
// Debounce 250 ms + độ trễ API giả: để dư thời gian cho máy chậm.
const SLOW = { timeout: 4000 };
const loaded = () => screen.findByText(/Trang 1\/2/, undefined, SLOW);
const searchBox = () => screen.getByRole("searchbox", { name: "Tìm bệnh nhân" });
const viewButtons = () => screen.queryAllByRole("button", { name: /^Xem khoản/ });

beforeEach(() => resetUnpaidItemMockStore());
afterEach(() => vi.restoreAllMocks());

describe("UnpaidItemsManagement", () => {
  it("hiện h1, 6 cột, giá căn phải tabular-nums và dòng tổng toàn kỳ lọc", async () => {
    render(<UnpaidItemsManagement />);
    await loaded();
    expect(screen.getByRole("heading", { level: 1, name: "Khoản chưa thanh toán" })).toBeInTheDocument();
    expect(screen.getAllByRole("columnheader")).toHaveLength(6);

    const total = UNPAID_ITEM_SEED.reduce((sum, item) => sum + item.priceVnd, 0);
    expect(screen.getByText("Tổng 26 khoản chờ thanh toán")).toBeInTheDocument();
    const totalCell = screen.getByText(money(total));
    expect(totalCell).toHaveClass("text-right", "tabular-nums");
  });

  it("không có nút thu tiền, ghi nhận, nhắc, hủy, in phiếu, sửa, xóa", async () => {
    render(<UnpaidItemsManagement />);
    await loaded();
    for (const button of screen.getAllByRole("button"))
      expect(button).not.toHaveAccessibleName(/thu tiền|ghi nhận|nhắc|hủy|in phiếu|sửa|xóa/i);
    expect(screen.getByText("Phí khám và chỉ định đang chờ thanh toán. Màn này chỉ xem.")).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/hóa đơn|còn nợ|đã trả/i);
  });

  it("trống khi chưa lọc: câu AF1 + gợi ý, không dòng tổng", async () => {
    resetUnpaidItemMockStore([]);
    render(<UnpaidItemsManagement />);
    expect(
      await screen.findByText("Hiện không có khoản nào chờ thanh toán.", undefined, SLOW),
    ).toBeInTheDocument();
    expect(screen.getByText(/Thu tiền do Lễ tân thực hiện/)).toBeInTheDocument();
    expect(screen.queryByText(/khoản chờ thanh toán$/)).not.toBeInTheDocument();
  });

  it("trống khi có lọc: thông điệp khớp bộ lọc", async () => {
    const user = userEvent.setup();
    render(<UnpaidItemsManagement />);
    await loaded();
    await user.click(searchBox());
    await user.paste("khong-co-ai");
    await waitFor(
      () => expect(screen.getByText("Không có khoản nào khớp bộ lọc.")).toBeInTheDocument(),
      SLOW,
    );
    expect(screen.getByText("Thử đổi khoảng ngày, từ khóa hoặc bỏ bớt bộ lọc.")).toBeInTheDocument();
  });

  it("lỗi tải: hiện câu lỗi + Thử lại, bấm thì có dữ liệu", async () => {
    const user = userEvent.setup();
    vi.spyOn(unpaidItemApi, "listUnpaidItems").mockRejectedValueOnce(
      new ApiError(500, { code: "MSG-04" }),
    );
    render(<UnpaidItemsManagement />);
    const retry = await screen.findByRole("button", { name: "Thử lại" }, SLOW);
    expect(screen.getByRole("alert")).toHaveTextContent(formatAppMessage("MSG-04"));
    await user.click(retry);
    await loaded();
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
  });

  it("từ > đến: MSG-54 dưới ô và không gọi API thêm", async () => {
    const spy = vi.spyOn(unpaidItemApi, "listUnpaidItems");
    render(<UnpaidItemsManagement />);
    await loaded();
    fireEvent.change(screen.getByLabelText("Từ ngày"), { target: { value: "2026-10-20" } });
    await waitFor(() => expect(spy.mock.calls.length).toBe(2), SLOW);
    await screen.findByText(/Trang 1\/1/, undefined, SLOW); // từ 20/10 chỉ còn 1 trang
    const calls = spy.mock.calls.length;

    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "2026-10-01" } });
    expect(
      await screen.findByText(formatAppMessage("MSG-54")),
    ).toBeInTheDocument();
    expect(spy.mock.calls.length).toBe(calls);
    expect(screen.getByText(/Trang 1\/\d/)).toBeInTheDocument(); // bảng giữ kết quả cũ
  });

  it("Xem mở Sheet chỉ có một nút Đóng (không có nút X)", async () => {
    const user = userEvent.setup();
    render(<UnpaidItemsManagement />);
    await loaded();
    await user.click(viewButtons()[0]);
    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findByText("Bác sĩ chỉ định", undefined, SLOW)).toBeInTheDocument();
    for (const label of ["Bệnh nhân", "Lượt khám / Lịch hẹn", "Khoản", "Giá", "Thời gian chỉ định"])
      expect(within(dialog).getByText(label)).toBeInTheDocument();
    const buttons = within(dialog).getAllByRole("button");
    expect(buttons).toHaveLength(1);
    expect(buttons[0]).toHaveAccessibleName("Đóng");
    await user.click(buttons[0]);
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("khoản allowedActions rỗng không có nút Xem", async () => {
    resetUnpaidItemMockStore(UNPAID_ITEM_SEED.filter((item) => item.allowedActions.length === 0));
    render(<UnpaidItemsManagement />);
    await screen.findByText(/Tổng 1 khoản chờ thanh toán/, undefined, SLOW);
    expect(viewButtons()).toHaveLength(0);
  });

  it("trang 2 có 6 dòng; đổi bộ lọc thì về trang 1 và khóa ô Dịch vụ khi chọn Phí khám", async () => {
    const user = userEvent.setup();
    render(<UnpaidItemsManagement />);
    await loaded();
    await user.click(screen.getByRole("button", { name: "Sau" }));
    await screen.findByText(/Trang 2\/2/, undefined, SLOW);
    // 6 dòng dữ liệu + dòng tiêu đề + dòng tổng
    expect(screen.getAllByRole("row")).toHaveLength(8);

    fireEvent.change(screen.getByLabelText("Loại khoản"), { target: { value: "CONSULTATION_FEE" } });
    await screen.findByText(/Trang 1\/1/, undefined, SLOW);
    expect(screen.getByLabelText("Dịch vụ")).toBeDisabled();
  });
});

describe("UnpaidItemDetailSheet", () => {
  it("404: hiện câu lỗi MSG-50, không có danh sách chi tiết, chỉ còn Đóng", async () => {
    render(
      <UnpaidItemDetailSheet itemId={UNPAID_ITEM_NOT_FOUND_ID} itemName="Phí khám" onClose={() => {}} />,
    );
    const alert = await screen.findByRole("alert", undefined, SLOW);
    expect(alert).toHaveTextContent(formatAppMessage("MSG-50"));
    expect(screen.queryByText("Bệnh nhân")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Đóng" })).toBeInTheDocument();
  });
});
