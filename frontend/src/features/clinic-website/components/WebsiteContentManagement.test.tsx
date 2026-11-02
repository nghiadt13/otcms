import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { WebsiteContentManagement } from "./WebsiteContentManagement";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
const article = {
  id: "article",
  type: "ABOUT_PAGE",
  title: "Giới thiệu",
  body: "Nội dung đang đăng",
  publishedAt: "2026-11-01T02:00:00Z",
  updatedAt: "2026-11-01T02:00:00Z",
  publishedBy: "admin",
  allowedActions: ["edit", "viewOnWebsite"],
};
beforeEach(() => {
  request.mockReset();
  request.mockImplementation(async (path: string, init?: RequestInit) => {
    if (init?.method) return { ...article, ...JSON.parse(init.body as string) };
    return path.includes("?")
      ? { items: [article], page: 0, size: 20, totalItems: 1, totalPages: 1 }
      : article;
  });
});
it("loads published content through the backend and preserves existing fields in the editor", async () => {
  render(<WebsiteContentManagement />);
  await screen.findByText("Giới thiệu");
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: /Thao tác cho/ }));
  await user.click(await screen.findByRole("menuitem", { name: /Sửa/ }));
  const dialog = await screen.findByRole("dialog", { name: /nội dung/i });
  await waitFor(() =>
    expect(within(dialog).getByLabelText(/Tiêu đề/)).toHaveValue("Giới thiệu"),
  );
  expect(request.mock.calls.some(([path]) => path.endsWith("/article"))).toBe(
    true,
  );
});
it("uses actual load failures with retry instead of the old magic search values", async () => {
  request.mockRejectedValueOnce(new ApiError(500, { code: "MSG-04" }));
  render(<WebsiteContentManagement />);
  await userEvent.click(await screen.findByRole("button", { name: "Thử lại" }));
  await screen.findByText("Giới thiệu");
  fireEvent.change(screen.getByLabelText("Tìm nội dung theo tiêu đề"), {
    target: { value: "loi-dich-vu" },
  });
  await waitFor(() =>
    expect(request.mock.calls.at(-1)![0]).toContain("loi-dich-vu"),
  );
});
