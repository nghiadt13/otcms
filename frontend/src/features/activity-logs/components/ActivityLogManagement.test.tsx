import { configure, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { resetActivityLogStore } from "../api/activityLogApi";
import { ActivityLogManagement } from "./ActivityLogManagement";

const { notifyError } = vi.hoisted(() => ({ notifyError: vi.fn() }));
vi.mock("@/lib/messages/notify", () => ({ notifyError }));

// Bọc API thật để ép lỗi từng ca (404/5xx chi tiết, lỗi tải danh sách) mà vẫn đi qua mock store.
const apiSpy = vi.hoisted(() => ({ list: vi.fn(), detail: vi.fn() }));
vi.mock("../api/activityLogApi", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../api/activityLogApi")>();
  apiSpy.list.mockImplementation(actual.listActivityLogs);
  apiSpy.detail.mockImplementation(actual.getActivityLog);
  return {
    ...actual,
    listActivityLogs: apiSpy.list,
    getActivityLog: apiSpy.detail,
  };
});

async function renderLoaded() {
  render(<ActivityLogManagement />);
  await screen.findByText(/bản ghi · Trang 1\/7/);
}

// Mock API có độ trễ 300ms + debounce 250ms; máy bận thì cần dư thời gian.
configure({ asyncUtilTimeout: 4000 });
vi.setConfig({ testTimeout: 20000 });

describe("ActivityLogManagement", () => {
  beforeEach(() => {
    resetActivityLogStore();
    notifyError.mockClear();
    apiSpy.list.mockClear();
    apiSpy.detail.mockClear();
  });

  it("hiện danh sách mới nhất trước, thời gian có giây, nhãn tiếng Việt, không lộ mã", async () => {
    await renderLoaded();
    const rows = screen.getAllByRole("row").slice(1);
    expect(rows).toHaveLength(20);
    const firstCells = within(rows[0]).getAllByRole("cell");
    expect(firstCells[0]).toHaveTextContent(/^\d{2}\/\d{2}\/\d{4} \d{2}:\d{2}:\d{2}$/);
    expect(firstCells[1]).toHaveTextContent("Chủ phòng khám");
    expect(firstCells[4]).toHaveTextContent("—");
    expect(screen.queryByRole("button", { name: /thêm|xuất|xóa|sửa/i })).toBeNull();

    const text = document.body.textContent ?? "";
    expect(text).not.toMatch(/MSG-|BR-|UC\d|[A-Z]{2,}_[A-Z_]+/);
  });

  it("mặc định 7 ngày; sang trang 2 rồi đổi bộ lọc thì về trang 1", async () => {
    const user = userEvent.setup();
    await renderLoaded();
    const firstQuery = apiSpy.list.mock.calls[0][0];
    const dayMs = 86_400_000;
    const span = (Date.parse(firstQuery.to) - Date.parse(firstQuery.from)) / dayMs;
    expect(span).toBe(6);

    await user.click(screen.getByRole("button", { name: "Sau" }));
    await screen.findByText(/Trang 2\/7/);
    await user.selectOptions(screen.getByLabelText("Loại hành động"), "ACCOUNT");
    await waitFor(() => expect(screen.getByText(/Trang 1\//)).toBeInTheDocument());
    expect(apiSpy.list.mock.calls.at(-1)?.[0]).toMatchObject({ action: "ACCOUNT", page: 0 });
  });

  it("từ > đến: MSG-54 dưới ô và không gọi API thêm", async () => {
    await renderLoaded();
    const calls = apiSpy.list.mock.calls.length;
    const from = screen.getByLabelText("Từ ngày");
    fireEvent.change(from, { target: { value: "2099-01-01" } });
    expect(await screen.findByRole("alert")).toHaveTextContent("Tiêu chí lọc chưa hợp lệ");
    await new Promise((r) => setTimeout(r, 350));
    expect(apiSpy.list.mock.calls.length).toBe(calls);
  });

  it("không có kết quả: câu AF1 kèm hướng dẫn", async () => {
    await renderLoaded();
    fireEvent.change(screen.getByLabelText("Người thực hiện"), { target: { value: "khong.ton.tai" } });
    expect(await screen.findByText("Không có hoạt động khớp bộ lọc.")).toBeInTheDocument();
    expect(screen.getByText(/mở rộng khoảng thời gian/)).toBeInTheDocument();
  });

  it("lỗi tải: MSG-04 và Thử lại gọi lại", async () => {
    const user = userEvent.setup();
    await renderLoaded();
    fireEvent.change(screen.getByLabelText("Bản ghi bị tác động"), { target: { value: "loi-may-chu" } });
    expect(await screen.findByText(/Hệ thống đang gặp sự cố/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Bản ghi bị tác động"), { target: { value: "" } });
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByText(/bản ghi · Trang 1\/7/);
  });

  async function openFirstDetail(user: ReturnType<typeof userEvent.setup>) {
    const first = screen.getAllByRole("row")[1];
    await user.click(within(first).getByRole("button", { name: /Thao tác cho/ }));
    await user.click(await screen.findByRole("menuitem", { name: "Xem chi tiết" }));
  }

  it("chi tiết: Sheet chỉ có Đóng, không có trước/sau, ẩn ghi chú hệ thống khi null", async () => {
    const user = userEvent.setup();
    await renderLoaded();
    await openFirstDetail(user);
    const dialog = await screen.findByRole("dialog");
    await within(dialog).findByText("Mã bản ghi");
    expect(within(dialog).getAllByRole("button").map((b) => b.textContent)).toEqual(["Đóng"]);
    expect(within(dialog).queryByText(/trước|sau/i)).toBeNull();
    expect(within(dialog).queryByText("Ghi chú hệ thống")).toBeNull();
    await user.click(within(dialog).getByRole("button", { name: "Đóng" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("chi tiết 404: toast lỗi và đóng Sheet", async () => {
    const user = userEvent.setup();
    await renderLoaded();
    apiSpy.detail.mockRejectedValueOnce(new ApiError(404, { code: "MSG-50" }));
    await openFirstDetail(user);
    await waitFor(() => expect(notifyError).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("chi tiết 5xx: Sheet giữ mở với MSG-04 và Thử lại", async () => {
    const user = userEvent.setup();
    await renderLoaded();
    apiSpy.detail.mockRejectedValueOnce(new ApiError(500, { code: "MSG-04" }));
    await openFirstDetail(user);
    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findByText(/Hệ thống đang gặp sự cố/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Thử lại" }));
    expect(await within(dialog).findByText("Mã bản ghi")).toBeInTheDocument();
  });
});
