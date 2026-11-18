import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ReportsManagement } from "./ReportsManagement";
const mock = vi.hoisted(() => ({ request: vi.fn(), download: vi.fn() }));
vi.mock("@/lib/api/client", () => ({ apiRequest: mock.request }));
vi.mock("@/lib/api/apiDownload", () => ({ apiDownload: mock.download }));
const subjects = {
  items: [
    {
      code: "APPOINTMENTS",
      label: "Lịch hẹn theo ngày",
      breakdownOptions: ["DATE"],
    },
    {
      code: "PAYMENTS",
      label: "Thanh toán đã thu",
      breakdownOptions: ["DATE", "RECEPTIONIST", "METHOD"],
    },
  ],
};
function respond(allowedActions: string[]) {
  mock.request.mockImplementation(async (_path, init) =>
    init?.method
      ? {
          query: JSON.parse(init.body),
          hasData: true,
          generatedAt: new Date().toISOString(),
          sections: [
            {
              title: "Thanh toán đã thu theo phương thức",
              columns: [
                { key: "method", label: "Phương thức", kind: "text" },
                { key: "count", label: "Số giao dịch", kind: "count" },
                { key: "amount", label: "Số tiền (VND)", kind: "money" },
              ],
              rows: [{ method: "Tiền mặt", count: 2, amount: 300000 }],
              totalRow: { count: 2, amount: 300000 },
            },
          ],
          allowedActions,
        }
      : subjects,
  );
}
beforeEach(() => {
  mock.request.mockReset();
  mock.download.mockReset();
});
it("uses backend subject options and offers no export when the backend allows none", async () => {
  respond([]);
  render(<ReportsManagement initialFrom="2026-11-01" initialTo="2026-11-07" />);
  await screen.findByRole("option", { name: "Lịch hẹn theo ngày" });
  fireEvent.change(screen.getByLabelText("Chủ đề báo cáo"), {
    target: { value: "APPOINTMENTS" },
  });
  expect(screen.queryByRole("button", { name: /Xuất/ })).toBeNull();
  expect(mock.request.mock.calls[0][0]).toBe("/api/v1/admin/reports/subjects");
});
it("runs a payments report and downloads it as CSV for the generated period", async () => {
  respond(["export"]);
  mock.download.mockResolvedValue(undefined);
  render(<ReportsManagement initialFrom="2026-11-01" initialTo="2026-11-07" />);
  await screen.findByRole("option", { name: "Thanh toán đã thu" });
  fireEvent.change(screen.getByLabelText("Chủ đề báo cáo"), {
    target: { value: "PAYMENTS" },
  });
  fireEvent.change(screen.getByLabelText("Tách theo"), {
    target: { value: "METHOD" },
  });
  fireEvent.click(screen.getByRole("button", { name: /Tạo báo cáo/ }));
  expect(await screen.findByText("Tiền mặt")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "Xuất CSV" }));
  await waitFor(() => expect(mock.download).toHaveBeenCalledTimes(1));
  expect(mock.download.mock.calls[0][0]).toBe(
    "/api/v1/admin/reports/export?subject=PAYMENTS&from=2026-11-01&to=2026-11-07&breakdown=METHOD&format=csv",
  );
});
