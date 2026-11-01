import { render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { DashboardOverview } from "./DashboardOverview";
import { buildEmptyOverview } from "../api/dashboardMockData";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
it("renders live zero values and explicitly marks unsupported sources", async () => {
  const period = { from: "2026-11-01", to: "2026-11-07" };
  const data = buildEmptyOverview(period, period);
  data.payments = { status: "NO_SOURCE" };
  request.mockResolvedValue(data);
  render(<DashboardOverview />);
  expect(await screen.findByText(/Kỳ .*so với/)).toBeVisible();
  expect(screen.getAllByText(/Chưa có dữ liệu nguồn/i).length).toBeGreaterThan(
    0,
  );
  expect(request.mock.calls[0][0]).toContain("/api/v1/admin/dashboard?");
});
