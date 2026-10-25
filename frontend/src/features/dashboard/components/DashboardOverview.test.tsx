import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ApiError } from "@/lib/api/apiError";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { getDashboardOverview } from "../api/dashboardApi";
import {
  DASHBOARD_FIXTURE,
  buildEmptyOverview,
  withoutReviews,
} from "../api/dashboardMockData";
import { previousPeriodOf } from "../lib/dashboardPeriodRules";
import type {
  DashboardOverview as Overview,
  DashboardPeriod,
} from "../types/dashboardOverview";
import { DashboardOverview } from "./DashboardOverview";

// Đi qua lớp API giả (không mock hook): chỉ thay thân getDashboardOverview để bỏ độ trễ.
vi.mock("../api/dashboardApi", () => ({ getDashboardOverview: vi.fn() }));
const api = vi.mocked(getDashboardOverview);

const ready = (period: DashboardPeriod): Overview => ({
  ...structuredClone(DASHBOARD_FIXTURE),
  period,
  previousPeriod: previousPeriodOf(period),
});

const COLLECTED = formatCurrencyVnd(12450000);
const DEFAULT_PERIOD = { from: "2026-10-14", to: "2026-10-20" };

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-10-20T05:00:00Z"));
  api.mockReset();
  api.mockImplementation(async (period) => ready(period));
});
afterEach(() => vi.useRealTimers());

// Số tiền chứa khoảng trắng không ngắt nên so thẳng textContent thay vì chuỗi đã chuẩn hóa.
const collectedCell = () =>
  screen.getByText((_, el) => el?.tagName === "DD" && el.textContent === COLLECTED);

const setDates =(from: string, to: string) => {
  fireEvent.change(screen.getByLabelText("Từ ngày"), { target: { value: from } });
  fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: to } });
};

describe("DashboardOverview", () => {
  it("mặc định 7 ngày: gọi đúng kỳ, 5 ô dải, 7 link kèm from/to", async () => {
    render(<DashboardOverview />);
    expect(await screen.findByText("Chờ thanh toán", { selector: "dt" })).toBeInTheDocument();

    expect(api).toHaveBeenCalledTimes(1);
    expect(api.mock.calls[0][0]).toEqual(DEFAULT_PERIOD);
    expect(screen.getByRole("button", { name: "7 ngày" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(document.querySelectorAll("dl dt")).toHaveLength(5);
    expect(collectedCell()).toBeInTheDocument();
    expect(
      screen.getByText("Kỳ 14/10/2026 – 20/10/2026, so với 07/10/2026 – 13/10/2026"),
    ).toBeInTheDocument();

    const links = screen.getAllByRole("link", { name: /Xem chi tiết/ });
    expect(links).toHaveLength(7);
    for (const link of links)
      expect(link.getAttribute("href")).toMatch(/\?from=2026-10-14&to=2026-10-20$/);
  });

  it("bấm 30 ngày tải lại; sửa ô ngày bỏ chọn nhóm và chỉ tải khi bấm Xem", async () => {
    const user = userEvent.setup();
    render(<DashboardOverview />);
    await screen.findByText("Lịch hẹn", { selector: "dt" });

    await user.click(screen.getByRole("button", { name: "30 ngày" }));
    await waitFor(() => expect(api).toHaveBeenCalledTimes(2));
    expect(api.mock.calls[1][0]).toEqual({ from: "2026-09-21", to: "2026-10-20" });
    await screen.findByText("Lịch hẹn", { selector: "dt" });

    setDates("2026-10-01", "2026-10-10");
    expect(screen.getByRole("button", { name: "30 ngày" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
    expect(api).toHaveBeenCalledTimes(2);

    await user.click(screen.getByRole("button", { name: "Xem" }));
    await waitFor(() => expect(api).toHaveBeenCalledTimes(3));
    expect(api.mock.calls[2][0]).toEqual({ from: "2026-10-01", to: "2026-10-10" });
    expect(await screen.findByText(/Kỳ 01\/10\/2026 – 10\/10\/2026/)).toBeInTheDocument();
  });

  it("từ > đến: lỗi dưới ô ngày, không gọi API, giữ số cũ", async () => {
    const user = userEvent.setup();
    render(<DashboardOverview />);
    await screen.findByText("Lịch hẹn", { selector: "dt" });

    setDates("2026-10-20", "2026-10-14");
    await user.click(screen.getByRole("button", { name: "Xem" }));

    expect(screen.getByRole("alert")).toHaveTextContent(/Khoảng thời gian chưa hợp lệ/);
    expect(api).toHaveBeenCalledTimes(1);
    expect(collectedCell()).toBeInTheDocument();
  });

  it("lỗi 500: thông báo kèm Thử lại, bấm thì tải lại thành công", async () => {
    const user = userEvent.setup();
    api.mockRejectedValueOnce(new ApiError(500, { code: "MSG-04" }));
    render(<DashboardOverview />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/Hệ thống đang gặp sự cố/);
    await user.click(screen.getByRole("button", { name: "Thử lại" }));

    expect(await screen.findByText("Lịch hẹn", { selector: "dt" })).toBeInTheDocument();
    expect(api).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("lỗi 403: báo không đủ quyền, không có Thử lại, khóa thanh chọn kỳ", async () => {
    api.mockRejectedValueOnce(new ApiError(403, { code: "MSG-50" }));
    render(<DashboardOverview />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/không đủ quyền/);
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Xem" })).toBeDisabled();
  });

  it("400 MSG-72 từ BE: lỗi dưới ô ngày, số cũ còn", async () => {
    const user = userEvent.setup();
    render(<DashboardOverview />);
    await screen.findByText("Lịch hẹn", { selector: "dt" });

    api.mockRejectedValueOnce(new ApiError(400, { code: "MSG-72" }));
    await user.click(screen.getByRole("button", { name: "Hôm nay" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/Khoảng thời gian chưa hợp lệ/);
    expect(collectedCell()).toBeInTheDocument();
  });

  it("khối NO_SOURCE: gạch, badge, câu giải thích; link vẫn có", async () => {
    api.mockImplementation(async (period) => ({
      ...withoutReviews(ready(period)),
    }));
    render(<DashboardOverview />);

    expect((await screen.findAllByText("Chưa có dữ liệu")).length).toBeGreaterThan(0);
    expect(
      screen.getByText("Chưa có dữ liệu để tổng hợp cho khối này."),
    ).toBeInTheDocument();
    expect(screen.getByText("—", { selector: "dd" })).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: /Xem chi tiết/ })).toHaveLength(7);
    expect(screen.getByRole("link", { name: /Phản hồi bệnh nhân/ })).toBeInTheDocument();
  });

  it("kỳ không hoạt động: số 0, câu dạy cách dùng, không báo lỗi", async () => {
    api.mockImplementation(async (period) =>
      buildEmptyOverview(period, previousPeriodOf(period)),
    );
    render(<DashboardOverview />);

    expect(await screen.findByText(/Chưa có dịch vụ nào được dùng trong kỳ này/)).toBeInTheDocument();
    expect(screen.getByText(/Chưa có lượt khám hay người chờ nào/)).toBeInTheDocument();
    expect(screen.getByText("Chưa có đánh giá trong kỳ này", { selector: "dd" })).toBeInTheDocument();
    expect(screen.getAllByText("0", { selector: "dd" }).length).toBeGreaterThanOrEqual(3);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("chỉ đọc: không nút Xuất/In/Tải, không lộ mã nội bộ", async () => {
    render(<DashboardOverview />);
    await screen.findByText("Lịch hẹn", { selector: "dt" });

    expect(screen.queryByRole("button", { name: /Xuất|In|Tải/ })).not.toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/MSG-|UC77|BR-62/);
  });

  it("đổi kỳ liên tiếp: phản hồi muộn của kỳ cũ không ghi đè", async () => {
    const user = userEvent.setup();
    let resolveFirst: (value: Overview) => void = () => {};
    api.mockImplementationOnce(
      () => new Promise<Overview>((resolve) => (resolveFirst = resolve)),
    );
    render(<DashboardOverview />);

    await user.click(screen.getByRole("button", { name: "Hôm nay" }));
    expect(
      await screen.findByText("Kỳ 20/10/2026 – 20/10/2026, so với 19/10/2026 – 19/10/2026"),
    ).toBeInTheDocument();

    resolveFirst(ready(DEFAULT_PERIOD));
    await Promise.resolve();
    expect(screen.getByText(/Kỳ 20\/10\/2026 – 20\/10\/2026/)).toBeInTheDocument();
    expect(screen.queryByText(/Kỳ 14\/10\/2026/)).not.toBeInTheDocument();
  });
});
