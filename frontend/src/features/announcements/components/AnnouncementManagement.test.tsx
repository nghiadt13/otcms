import { render, screen, within, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { AnnouncementManagement } from "./AnnouncementManagement";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
const row = {
  id: "notice",
  title: "Lịch nghỉ lễ",
  body: "Phòng khám nghỉ thứ hai",
  audience: "PUBLIC",
  channels: ["IN_APP"],
  publishedAt: "2026-11-01T02:00:00Z",
  publishedByName: "admin",
  deliveryStatus: "SENT",
  allowedActions: ["view"],
};
beforeEach(() => {
  request.mockReset();
  request.mockImplementation(async (path: string) => {
    if (path.endsWith("/options"))
      return {
        audiences: ["PUBLIC"],
        channels: ["IN_APP"],
        allowedActions: ["create"],
      };
    return path.includes("?")
      ? { items: [row], page: 0, size: 20, totalItems: 1, totalPages: 1 }
      : row;
  });
});
it("loads real announcements and opens their read-only detail", async () => {
  render(<AnnouncementManagement />);
  await screen.findByText(row.title);
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: /Xem thông báo/ }));
  const dialog = await screen.findByRole("dialog");
  await within(dialog).findByText(row.body);
  expect(request.mock.calls.some(([path]) => path.endsWith("/notice"))).toBe(
    true,
  );
});
it("only offers recipients enabled by the backend and explains unavailable private delivery", async () => {
  render(<AnnouncementManagement />);
  await screen.findByText(row.title);
  await userEvent.click(screen.getByRole("button", { name: /Đăng thông báo/ }));
  const dialog = await screen.findByRole("dialog");
  await waitFor(() =>
    expect(
      within(dialog).getByText(
        /Gửi riêng cho bệnh nhân và nhân viên chưa được bật/,
      ),
    ).toBeVisible(),
  );
  const audience = within(dialog).getByLabelText(
    "Nhóm người nhận",
  ) as HTMLSelectElement;
  expect(Array.from(audience.options).map((option) => option.value)).toEqual([
    "",
    "PUBLIC",
  ]);
});
