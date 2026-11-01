import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { ReportsManagement } from "./ReportsManagement";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
it("uses backend subject options and never offers unapproved export actions", async () => {
  request.mockImplementation(async (_path, init) =>
    init?.method
      ? {
          query: JSON.parse(init.body),
          hasData: false,
          generatedAt: new Date().toISOString(),
          sections: [],
          allowedActions: [],
        }
      : {
          items: [
            {
              code: "APPOINTMENTS",
              label: "Lịch hẹn theo ngày",
              breakdownOptions: ["DATE"],
            },
          ],
        },
  );
  render(<ReportsManagement initialFrom="2026-11-01" initialTo="2026-11-07" />);
  await screen.findByRole("option", { name: "Lịch hẹn theo ngày" });
  fireEvent.change(screen.getByLabelText("Chủ đề báo cáo"), {
    target: { value: "APPOINTMENTS" },
  });
  expect(screen.queryByRole("button", { name: /Excel|PDF/ })).toBeNull();
  expect(request.mock.calls[0][0]).toBe("/api/v1/admin/reports/subjects");
});
