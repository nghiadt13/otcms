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
import { ActivityLogManagement } from "./ActivityLogManagement";
import { formatAppMessage } from "@/lib/messages/appMessages";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
const item = {
  id: "room:11111111-1111-1111-1111-111111111111",
  occurredAt: "2026-11-01T02:00:00Z",
  actorUsername: "admin",
  actorRole: "ADMIN",
  action: "CREATE_ROOM",
  actionRole: null,
  recordType: "ROOM",
  recordLabel: "P.101",
  correctionReason: null,
  allowedActions: ["view"],
};
beforeEach(() => {
  request.mockReset();
  request.mockImplementation(async (path: string) => {
    if (!path.includes("?"))
      return { ...item, recordCode: "P.101", systemNote: null };
    const q = new URL(path, "http://local").searchParams;
    return {
      items: [item],
      page: Number(q.get("page") ?? 0),
      size: 20,
      totalItems: 21,
      totalPages: 2,
    };
  });
});
it("loads the real endpoint, paginates on the server and resets pagination after filtering", async () => {
  render(<ActivityLogManagement />);
  await screen.findByText(/Trang 1\/2/);
  const q = new URL(request.mock.calls[0][0], "http://local").searchParams;
  expect(
    (Date.parse(q.get("to")!) - Date.parse(q.get("from")!)) / 86400000,
  ).toBe(6);
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Sau" }));
  await screen.findByText(/Trang 2\/2/);
  await user.selectOptions(screen.getByLabelText("Loại hành động"), "ROOM");
  await waitFor(() =>
    expect(
      new URL(request.mock.calls.at(-1)![0], "http://local").searchParams.get(
        "page",
      ),
    ).toBe("0"),
  );
  expect(
    new URL(request.mock.calls.at(-1)![0], "http://local").searchParams.get(
      "action",
    ),
  ).toBe("ROOM");
});
it("keeps existing results and does not fetch when dates are reversed", async () => {
  render(<ActivityLogManagement />);
  await screen.findByText("P.101", { exact: false });
  const calls = request.mock.calls.length;
  fireEvent.change(screen.getByLabelText("Từ ngày"), {
    target: { value: "2099-01-01" },
  });
  expect(await screen.findByRole("alert")).toHaveTextContent(
    formatAppMessage("MSG-54"),
  );
  await new Promise((resolve) => setTimeout(resolve, 300));
  expect(request).toHaveBeenCalledTimes(calls);
});
it("opens a read-only detail without old or new values", async () => {
  render(<ActivityLogManagement />);
  await screen.findByText(/Trang 1\/2/);
  const user = userEvent.setup();
  await user.click(
    within(screen.getAllByRole("row")[1]).getByRole("button", {
      name: /Thao tác cho/,
    }),
  );
  await user.click(
    await screen.findByRole("menuitem", { name: "Xem chi tiết" }),
  );
  const dialog = await screen.findByRole("dialog");
  await within(dialog).findByText("Mã bản ghi");
  expect(
    within(dialog)
      .getAllByRole("button")
      .map((b) => b.textContent),
  ).toEqual(["Đóng"]);
  expect(within(dialog).queryByText(/trước|sau/i)).toBeNull();
  expect(request.mock.calls.some(([path]) => path.includes("/room%3A"))).toBe(
    true,
  );
});
it("shows backend failure with retry, without manufacturing audit rows", async () => {
  request.mockRejectedValueOnce(new ApiError(500, { code: "MSG-04" }));
  render(<ActivityLogManagement />);
  await screen.findByText(formatAppMessage("MSG-04"));
  await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
  await screen.findByText(/Trang 1\/2/);
});
