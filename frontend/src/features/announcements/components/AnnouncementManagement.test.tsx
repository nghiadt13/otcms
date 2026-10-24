import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  mockConfig,
  mockStore,
  resetAnnouncementMock,
} from "../api/announcementMockData";
import * as api from "../api/announcementApi";
import type { AnnouncementDetail } from "../types/announcement";
import { AnnouncementManagement } from "./AnnouncementManagement";
import { formatAppMessage } from "@/lib/messages/appMessages";

/** Nút Đóng ở chân Dialog, tách khỏi nút X có nhãn sr-only cùng tên "Đóng". */
function footerClose(dialog: HTMLElement) {
  const footer = dialog.querySelector<HTMLElement>('[data-slot="dialog-footer"]');
  if (!footer) throw new Error("Dialog không có chân");
  return within(footer).getByRole("button", { name: "Đóng" });
}

vi.setConfig({ testTimeout: 30000 });

const notify = vi.hoisted(() => ({
  notifySuccess: vi.fn(),
  notifyError: vi.fn(),
  notifyWarning: vi.fn(),
}));
vi.mock("@/lib/messages/notify", () => notify);

const row = (patch: Partial<AnnouncementDetail> = {}): AnnouncementDetail => ({
  id: "11111111-1111-4111-8111-111111111111",
  title: "Lịch nghỉ lễ",
  body: "Dòng một\nDòng hai",
  audience: "PATIENT",
  channels: ["EMAIL", "IN_APP"],
  publishedAt: "2026-10-01T09:00:00+07:00",
  publishedByName: "Nguyễn Quản Trị",
  deliveryStatus: "SENT",
  allowedActions: ["view"],
  ...patch,
});

async function openForm(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole("button", { name: "Đăng thông báo" }));
  return screen.findByRole("dialog");
}

beforeEach(() => {
  mockConfig.latencyMs = 0;
  resetAnnouncementMock();
  vi.clearAllMocks();
});

describe("AnnouncementManagement danh sách", () => {
  it("hiện đủ cột, kênh ghép bằng dấu phẩy, deliveryStatus null là —", async () => {
    resetAnnouncementMock({
      announcements: [row(), row({ id: "2", title: "Không rõ", deliveryStatus: null })],
    });
    render(<AnnouncementManagement />);
    expect(await screen.findByText("Lịch nghỉ lễ")).toBeInTheDocument();
    for (const name of ["Tiêu đề", "Nhóm người nhận", "Kênh", "Ngày đăng", "Trạng thái giao", "Thao tác"])
      expect(screen.getByRole("columnheader", { name })).toBeInTheDocument();
    const first = screen.getByText("Lịch nghỉ lễ").closest("tr")!;
    expect(within(first).getByText("Email, Trong hệ thống")).toBeInTheDocument();
    expect(within(first).getByText("Đã gửi")).toBeInTheDocument();
    expect(within(first).getByText("01/10/2026 09:00")).toBeInTheDocument();
    const second = screen.getByText("Không rõ").closest("tr")!;
    expect(within(second).getByText("—")).toBeInTheDocument();
  });

  it("ẩn cột trạng thái giao khi BE không trả field", async () => {
    const { deliveryStatus, ...withoutStatus } = row();
    void deliveryStatus;
    resetAnnouncementMock({ announcements: [withoutStatus as AnnouncementDetail] });
    render(<AnnouncementManagement />);
    await screen.findByText("Lịch nghỉ lễ");
    expect(screen.queryByRole("columnheader", { name: "Trạng thái giao" })).toBeNull();
  });

  it("trống chưa lọc dạy cách dùng", async () => {
    resetAnnouncementMock({ announcements: [] });
    render(<AnnouncementManagement />);
    expect(
      await screen.findByText(/Chưa có thông báo nào\. Bấm Đăng thông báo/),
    ).toBeInTheDocument();
  });

  it("trống do lọc và lỗi tải có Thử lại", async () => {
    const user = userEvent.setup({ delay: null });
    render(<AnnouncementManagement />);
    const search = await screen.findByRole("searchbox");
    await user.type(search, "khong-co");
    expect(
      await screen.findByText(/Không có thông báo khớp bộ lọc/, {}, { timeout: 2000 }),
    ).toBeInTheDocument();

    await user.clear(search);
    await user.type(search, "loi-tai");
    const alert = await screen.findByText(formatAppMessage("MSG-04"), {}, { timeout: 2000 });
    expect(alert).toBeInTheDocument();
    const spy = vi.spyOn(api, "listAnnouncements");
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    await waitFor(() => expect(spy).toHaveBeenCalled());
  });

  it("ẩn Xem khi thiếu view; ẩn Đăng thông báo khi options thiếu create", async () => {
    resetAnnouncementMock({
      announcements: [row({ allowedActions: [] })],
      options: { allowedActions: [] },
    });
    render(<AnnouncementManagement />);
    await screen.findByText("Lịch nghỉ lễ");
    expect(screen.queryByRole("button", { name: /^Xem/ })).toBeNull();
    expect(screen.queryByRole("button", { name: "Đăng thông báo" })).toBeNull();
  });

  it("từ > đến hiện lỗi dưới ô và không gọi API", async () => {
    const user = userEvent.setup({ delay: null });
    render(<AnnouncementManagement />);
    await screen.findByRole("searchbox");
    await waitFor(() => expect(screen.getAllByRole("row").length).toBeGreaterThan(2));
    const spy = vi.spyOn(api, "listAnnouncements");
    await user.type(screen.getByLabelText("Từ ngày"), "2026-10-20");
    await user.type(screen.getByLabelText("Đến ngày"), "2026-10-01");
    expect(await screen.findByText(formatAppMessage("MSG-54"))).toBeInTheDocument();
    const callsWithBadRange = spy.mock.calls.filter(
      ([q]) => q.from && q.to && q.from > q.to,
    );
    expect(callsWithBadRange).toHaveLength(0);
  });
});

describe("AnnouncementManagement đăng thông báo", () => {
  it("chưa chọn nhóm thì lỗi dưới ô nhóm và không mở xác nhận", async () => {
    const user = userEvent.setup({ delay: null });
    render(<AnnouncementManagement />);
    const dialog = await openForm(user);
    await user.type(within(dialog).getByLabelText("Tiêu đề"), "Thông báo thử");
    await user.type(within(dialog).getByLabelText("Nội dung"), "Nội dung");
    await user.click(within(dialog).getByRole("checkbox", { name: "Email" }));
    await user.click(within(dialog).getByRole("button", { name: "Đăng thông báo" }));
    expect(await within(dialog).findByText("Chọn nhóm người nhận.")).toBeInTheDocument();
    expect(screen.queryByRole("alertdialog")).toBeNull();
  });

  it("xác nhận, chỉ một POST, toast thành công và bước kết quả", async () => {
    const user = userEvent.setup({ delay: null });
    const create = vi.spyOn(api, "createAnnouncement");
    render(<AnnouncementManagement />);
    const dialog = await openForm(user);
    await user.type(within(dialog).getByLabelText("Tiêu đề"), "Đổi giờ tiếp nhận");
    await user.type(within(dialog).getByLabelText("Nội dung"), "Từ 7 giờ 30");
    await user.selectOptions(within(dialog).getByLabelText("Nhóm người nhận"), "PATIENT");
    await user.click(within(dialog).getByRole("checkbox", { name: "Email" }));
    await user.click(within(dialog).getByRole("button", { name: "Đăng thông báo" }));

    const confirm = await screen.findByRole("alertdialog");
    expect(
      within(confirm).getByText("Thông báo sẽ gửi tới Bệnh nhân qua Email."),
    ).toBeInTheDocument();
    const send = within(confirm).getByRole("button", { name: "Gửi thông báo" });
    await user.dblClick(send);

    expect(await screen.findByText("Kết quả giao:")).toBeInTheDocument();
    expect(create).toHaveBeenCalledTimes(1);
    expect(notify.notifySuccess).toHaveBeenCalledWith(
      "Đã đăng thông báo cho nhóm người nhận đã chọn.",
    );
    expect(mockStore.announcements[0].title).toBe("Đổi giờ tiếp nhận");
    await user.click(footerClose(screen.getByRole("dialog")));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("lỗi quyền: toast, giữ nội dung form, đóng xác nhận", async () => {
    const user = userEvent.setup({ delay: null });
    render(<AnnouncementManagement />);
    const dialog = await openForm(user);
    await user.type(within(dialog).getByLabelText("Tiêu đề"), "Thử lỗi quyền");
    await user.type(within(dialog).getByLabelText("Nội dung"), "Nội dung giữ lại");
    await user.selectOptions(within(dialog).getByLabelText("Nhóm người nhận"), "PUBLIC");
    await user.click(within(dialog).getByRole("checkbox", { name: "Trong hệ thống" }));
    await user.click(within(dialog).getByRole("button", { name: "Đăng thông báo" }));
    await user.click(await screen.findByRole("button", { name: "Gửi thông báo" }));

    await waitFor(() => expect(notify.notifyError).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
    expect(within(dialog).getByLabelText("Nội dung")).toHaveValue("Nội dung giữ lại");
  });
});

describe("AnnouncementManagement chi tiết", () => {
  it("bấm Xem thì Sheet hiện chi tiết chỉ đọc, không có nút Sửa", async () => {
    const user = userEvent.setup({ delay: null });
    resetAnnouncementMock({ announcements: [row()] });
    render(<AnnouncementManagement />);
    await user.click(await screen.findByRole("button", { name: /^Xem thông báo/ }));
    const sheet = await screen.findByRole("dialog");
    expect(await within(sheet).findByText("Nguyễn Quản Trị")).toBeInTheDocument();
    expect(within(sheet).getByText(/Dòng một/)).toBeInTheDocument();
    expect(within(sheet).queryByRole("button", { name: /Sửa/ })).toBeNull();
    expect(within(sheet).getAllByRole("button", { name: "Đóng" }).length).toBeGreaterThan(0);
  });
});
