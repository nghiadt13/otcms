import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import {
  getUnreadNotificationCount,
  resetNotificationMockStore,
} from "../api/notificationApi";
import { MOCK_PATIENT_NOTIFICATIONS } from "../api/patientNotificationMockData";
import type { NotificationItem } from "../types/notification";
import type { NotificationRecordHrefResolver } from "../utils/notificationRecordLinks";
import { PatientNotificationsScreen } from "./PatientNotificationsScreen";

vi.mock("next/navigation", () => ({
  usePathname: () => "/portal/notifications",
}));
vi.mock("@/lib/messages/notify", () => ({
  notifySuccess: vi.fn(),
  notifyError: vi.fn(),
}));

// Bản sao của lớp ghép khu bệnh nhân (app/(patient)/portal/notificationRecordHref.ts), đủ để kiểm
// nút Mở. Feature không được import app/, nên test dùng hàm riêng.
const resolveTestHref: NotificationRecordHrefResolver = (record) => {
  if (!record.available) return null;
  if (record.kind === "VISIT_RESULT") return `/portal/results/${record.id}`;
  if (record.kind === "VISIT") return `/portal/visits/${record.id}`;
  return null;
};

const rows = () => screen.getAllByRole("listitem");
const rowButton = (title: string) =>
  screen.findByRole("button", { name: new RegExp(title) });

function renderScreen() {
  return render(
    <PatientNotificationsScreen resolveRecordHref={resolveTestHref} />,
  );
}

beforeEach(() => {
  resetLinkedProfilesMock();
  resetNotificationMockStore(MOCK_PATIENT_NOTIFICATIONS);
  vi.clearAllMocks();
});

describe("PatientNotificationsScreen", () => {
  it("tab Tất cả có 8 thông báo; tab Chưa đọc chỉ còn 3", async () => {
    renderScreen();
    await waitFor(() => expect(rows()).toHaveLength(8));
    await userEvent.click(screen.getByRole("tab", { name: "Chưa đọc" }));
    await waitFor(() => expect(rows()).toHaveLength(3));
  });

  it("mở dòng chưa đọc: tự đánh dấu đã đọc, im lặng (không toast), số chưa đọc giảm", async () => {
    renderScreen();
    await userEvent.click(await rowButton("Đã đủ kết quả cận lâm sàng"));
    const sheet = await screen.findByRole("dialog");
    expect(
      within(sheet).getByText(/Vui lòng đến phòng Khám 2/),
    ).toBeInTheDocument();
    await waitFor(async () =>
      expect((await getUnreadNotificationCount()).unreadCount).toBe(2),
    );
    expect(notifySuccess).not.toHaveBeenCalled();
    expect(
      within(sheet).queryByRole("button", { name: "Đánh dấu đã đọc" }),
    ).not.toBeInTheDocument();
  });

  it("kết quả khám: nút Mở là nút chính và trỏ tới màn kết quả của bản ghi", async () => {
    renderScreen();
    await userEvent.click(await rowButton("Kết quả khám đã được công bố"));
    const sheet = await screen.findByRole("dialog");
    expect(within(sheet).getByRole("link", { name: "Mở" })).toHaveAttribute(
      "href",
      "/portal/results/vis-an-done",
    );
    expect(within(sheet).getByRole("link", { name: "Mở" })).toHaveClass(
      "bg-primary",
    );
  });

  it("bản ghi không còn khả dụng: không có nút Mở, hiện câu MSG-50", async () => {
    renderScreen();
    await userEvent.click(
      await rowButton("Khoản thanh toán của Nguyễn Thị Lan"),
    );
    const sheet = await screen.findByRole("dialog");
    expect(
      within(sheet).queryByRole("link", { name: "Mở" }),
    ).not.toBeInTheDocument();
    expect(within(sheet).getByRole("status")).toHaveTextContent(
      formatAppMessage("MSG-50"),
    );
  });

  it("đánh dấu tất cả đã đọc: toast MSG-60, nút biến mất khi hết chưa đọc", async () => {
    renderScreen();
    await userEvent.click(
      await screen.findByRole("button", { name: "Đánh dấu tất cả đã đọc" }),
    );
    await waitFor(() =>
      expect(notifySuccess).toHaveBeenCalledWith(
        "Đã đánh dấu thông báo là đã đọc.",
      ),
    );
    await waitFor(() =>
      expect(
        screen.queryByRole("button", { name: "Đánh dấu tất cả đã đọc" }),
      ).not.toBeInTheDocument(),
    );
  });

  it("đã đọc hết: tab Chưa đọc hiện câu báo đã đọc hết", async () => {
    resetNotificationMockStore(
      MOCK_PATIENT_NOTIFICATIONS.map((entry: NotificationItem) => ({
        ...entry,
        read: true,
        allowedActions: [],
      })),
    );
    renderScreen();
    await userEvent.click(await screen.findByRole("tab", { name: "Chưa đọc" }));
    expect(
      await screen.findByText("Bạn đã đọc hết thông báo."),
    ).toBeInTheDocument();
  });

  it("nhiều hơn một trang: có Trang trước/Trang sau, bấm sang trang 2", async () => {
    resetNotificationMockStore(
      Array.from({ length: 12 }, (_, index) => ({
        ...MOCK_PATIENT_NOTIFICATIONS[
          index % MOCK_PATIENT_NOTIFICATIONS.length
        ],
        id: `np-x-${index}`,
      })),
    );
    renderScreen();
    expect(await screen.findByText("Trang 1/2")).toBeInTheDocument();
    expect(rows()).toHaveLength(10);
    await userEvent.click(screen.getByRole("button", { name: "Trang sau" }));
    await waitFor(() => expect(rows()).toHaveLength(2));
    expect(screen.getByText("Trang 2/2")).toBeInTheDocument();
  });

  it("lỗi tải thông báo: nói rõ lỗi và có nút Thử lại", async () => {
    resetNotificationMockStore(MOCK_PATIENT_NOTIFICATIONS, { failList: true });
    renderScreen();
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Không tải được thông báo.");
    expect(
      within(alert).getByRole("button", { name: "Thử lại" }),
    ).toBeInTheDocument();
  });

  it("tài khoản chưa có hồ sơ đã gắn: không có danh sách, có hướng dẫn thêm hồ sơ (BR-68)", async () => {
    resetLinkedProfilesMock({ variant: "empty" });
    renderScreen();
    expect(
      await screen.findByText(/chưa nhận được thông báo/),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Thêm hồ sơ bệnh nhân" }),
    ).toHaveAttribute("href", "/portal/profiles/new");
    expect(screen.queryByRole("tab")).not.toBeInTheDocument();
    expect(notifyError).not.toHaveBeenCalled();
  });
});
