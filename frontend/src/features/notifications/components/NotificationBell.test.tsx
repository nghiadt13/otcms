import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { notifySuccess } from "@/lib/messages/notify";
import {
  getUnreadNotificationCount,
  resetNotificationMockStore,
} from "../api/notificationApi";
import { MOCK_NOTIFICATIONS } from "../api/notificationMockData";
import { NotificationBell } from "./NotificationBell";

vi.mock("next/navigation", () => ({ usePathname: () => "/admin" }));
vi.mock("@/lib/messages/notify", () => ({
  notifySuccess: vi.fn(),
  notifyError: vi.fn(),
}));

beforeEach(() => {
  resetNotificationMockStore();
  vi.clearAllMocks();
});

async function openBell(name: RegExp) {
  await userEvent.click(await screen.findByRole("button", { name }));
}

describe("NotificationBell", () => {
  it("badge hiện số chưa đọc, 9+ khi nhiều hơn 9", async () => {
    render(<NotificationBell />);
    const { unreadCount } = await getUnreadNotificationCount();
    expect(unreadCount).toBeGreaterThan(9);
    const bell = await screen.findByRole("button", {
      name: `Thông báo của bạn, ${unreadCount} chưa đọc`,
    });
    expect(bell).toHaveTextContent("9+");
  });

  it("popover chỉ có tối đa 5 dòng và có liên kết Xem tất cả", async () => {
    render(<NotificationBell />);
    await openBell(/Thông báo của bạn/);
    const dialog = await screen.findByText("Thông báo của bạn", {
      selector: "[data-slot=popover-title]",
    });
    expect(dialog).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getAllByRole("listitem")).toHaveLength(5),
    );
    expect(screen.getByRole("link", { name: "Xem tất cả" })).toHaveAttribute(
      "href",
      "/admin/notifications",
    );
  });

  it("đánh dấu đã đọc: toast thành công và số chưa đọc giảm", async () => {
    render(<NotificationBell />);
    const before = (await getUnreadNotificationCount()).unreadCount;
    await openBell(/Thông báo của bạn/);
    const first = MOCK_NOTIFICATIONS[0];
    await userEvent.click(
      await screen.findByRole("button", { name: `Đánh dấu đã đọc: ${first.title}` }),
    );
    await waitFor(() =>
      expect(notifySuccess).toHaveBeenCalledWith("Đã đánh dấu thông báo là đã đọc."),
    );
    await waitFor(async () =>
      expect((await getUnreadNotificationCount()).unreadCount).toBe(before - 1),
    );
    await screen.findByRole("button", {
      name: `Thông báo của bạn, ${before - 1} chưa đọc`,
    });
  });

  it("ca rỗng: hướng dẫn rõ ràng", async () => {
    resetNotificationMockStore([]);
    render(<NotificationBell />);
    await openBell(/Thông báo của bạn/);
    expect(await screen.findByText("Bạn chưa có thông báo nào.")).toBeInTheDocument();
  });

  it("ca lỗi tải: thông báo lỗi và nút Thử lại", async () => {
    resetNotificationMockStore(MOCK_NOTIFICATIONS, { failList: true });
    render(<NotificationBell />);
    await openBell(/Thông báo của bạn/);
    const alert = await screen.findByRole("alert");
    expect(within(alert).getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
  });

  it("bấm tiêu đề mở Sheet chi tiết, không tự đánh dấu đã đọc", async () => {
    render(<NotificationBell />);
    await openBell(/Thông báo của bạn/);
    const first = MOCK_NOTIFICATIONS[0];
    const rows = await screen.findAllByRole("button", { name: new RegExp(first.title) });
    await userEvent.click(rows[0]);
    const sheet = await screen.findByRole("dialog");
    expect(within(sheet).getByText(first.content)).toBeInTheDocument();
    expect(within(sheet).getByRole("button", { name: "Đánh dấu đã đọc" })).toBeInTheDocument();
    expect(notifySuccess).not.toHaveBeenCalled();
  });
});
