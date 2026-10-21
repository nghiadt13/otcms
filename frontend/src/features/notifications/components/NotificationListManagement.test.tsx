import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { notifySuccess } from "@/lib/messages/notify";
import { resetNotificationMockStore } from "../api/notificationApi";
import { MOCK_NOTIFICATIONS } from "../api/notificationMockData";
import { NotificationListManagement } from "./NotificationListManagement";

vi.mock("next/navigation", () => ({ usePathname: () => "/admin/notifications" }));
vi.mock("@/lib/messages/notify", () => ({
  notifySuccess: vi.fn(),
  notifyError: vi.fn(),
}));

beforeEach(() => {
  resetNotificationMockStore();
  vi.clearAllMocks();
});

const dataRows = () => screen.getAllByRole("row").slice(1);

describe("NotificationListManagement", () => {
  it("23 thông báo chia 2 trang: trang 1 có 20 dòng, sang trang 2 còn 3", async () => {
    render(<NotificationListManagement />);
    await waitFor(() => expect(dataRows()).toHaveLength(20));
    expect(screen.getByText(/Trang 1\/2/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Sau/ }));
    await waitFor(() => expect(dataRows()).toHaveLength(3));
  });

  it("đổi lọc sang Chưa đọc: về trang 0 và chỉ còn thông báo chưa đọc", async () => {
    render(<NotificationListManagement />);
    await waitFor(() => expect(dataRows()).toHaveLength(20));
    await userEvent.click(screen.getByRole("button", { name: /Sau/ }));
    await waitFor(() => expect(dataRows()).toHaveLength(3));

    await userEvent.click(screen.getByRole("button", { name: "Chưa đọc" }));
    const unread = MOCK_NOTIFICATIONS.filter((item) => !item.read).length;
    await waitFor(() => expect(dataRows()).toHaveLength(unread));
    expect(screen.getByText(/Trang 1\/1/)).toBeInTheDocument();
  });

  it("danh sách rỗng: hướng dẫn rõ ràng và không có nút đánh dấu tất cả", async () => {
    resetNotificationMockStore([]);
    render(<NotificationListManagement />);
    expect(await screen.findByText("Bạn chưa có thông báo nào.")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Đánh dấu tất cả đã đọc" }),
    ).not.toBeInTheDocument();
  });

  it("lỗi tải: hiện lỗi và Thử lại", async () => {
    resetNotificationMockStore(MOCK_NOTIFICATIONS, { failList: true });
    render(<NotificationListManagement />);
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
  });

  it("Sheet: đánh dấu đã đọc báo thành công và chuyển trạng thái", async () => {
    render(<NotificationListManagement />);
    const first = MOCK_NOTIFICATIONS[0];
    await userEvent.click(
      (await screen.findAllByRole("button", { name: first.title }))[0],
    );
    const sheet = await screen.findByRole("dialog");
    await userEvent.click(within(sheet).getByRole("button", { name: "Đánh dấu đã đọc" }));
    await waitFor(() =>
      expect(notifySuccess).toHaveBeenCalledWith("Đã đánh dấu thông báo là đã đọc."),
    );
    await waitFor(() =>
      expect(
        within(sheet).queryByRole("button", { name: "Đánh dấu đã đọc" }),
      ).not.toBeInTheDocument(),
    );
  });

  it("bản ghi liên quan không còn: không có nút Mở, hiện câu báo không còn", async () => {
    const gone = MOCK_NOTIFICATIONS.find((item) => item.relatedRecord && !item.relatedRecord.available)!;
    resetNotificationMockStore([gone]);
    render(<NotificationListManagement />);
    await userEvent.click(await screen.findByRole("button", { name: gone.title }));
    const sheet = await screen.findByRole("dialog");
    expect(within(sheet).queryByRole("link", { name: "Mở" })).not.toBeInTheDocument();
    expect(within(sheet).getByRole("status")).toHaveTextContent("không còn");
  });

  it("bản ghi liên quan còn: có liên kết Mở", async () => {
    const live = MOCK_NOTIFICATIONS.find((item) => item.relatedRecord?.available)!;
    resetNotificationMockStore([live]);
    render(<NotificationListManagement />);
    await userEvent.click(await screen.findByRole("button", { name: live.title }));
    const sheet = await screen.findByRole("dialog");
    expect(within(sheet).getByRole("link", { name: "Mở" })).toBeInTheDocument();
  });

  it("Đánh dấu tất cả đã đọc: toast và hết thông báo chưa đọc", async () => {
    render(<NotificationListManagement />);
    await userEvent.click(
      await screen.findByRole("button", { name: "Đánh dấu tất cả đã đọc" }),
    );
    await waitFor(() => expect(notifySuccess).toHaveBeenCalled());
    await waitFor(() =>
      expect(
        screen.queryByRole("button", { name: "Đánh dấu tất cả đã đọc" }),
      ).not.toBeInTheDocument(),
    );
  });
});
