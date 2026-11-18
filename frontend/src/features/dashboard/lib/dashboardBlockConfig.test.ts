import { describe, expect, it } from "vitest";
import { buildEmptyOverview } from "../api/dashboardMockData";
import { buildDashboardBlocks } from "./dashboardBlockConfig";

const period = { from: "2026-11-01", to: "2026-11-07" };

describe("buildDashboardBlocks with the sources the backend now fills (UC-77)", () => {
  const overview = buildEmptyOverview(period, period);
  overview.payments = {
    status: "READY",
    collected: { value: 500000, previousValue: 100000 },
    byMethod: [
      { code: "CASH", value: 300000, previousValue: 100000 },
      { code: "PAYOS", value: 200000, previousValue: 0 },
    ],
  };
  overview.imagingAndLab = {
    status: "READY",
    imagingPerformed: { value: 4, previousValue: 1 },
    labPerformed: { value: 0, previousValue: 0 },
    labStatus: "NO_SOURCE",
  };
  overview.pendingAndCancelled = {
    status: "READY",
    pendingPayment: { value: 3, previousValue: 0 },
    cancelled: { value: 1, previousValue: 2 },
    refundPending: { value: 5, previousValue: null },
  };
  overview.visits = {
    status: "READY",
    total: { value: 2, previousValue: 1 },
    byStatus: [],
    waitingByRoom: [
      { roomId: "r1", roomName: "Phòng 1", value: 3, previousValue: null },
    ],
    waitingByRoomStatus: "READY",
  };
  const blocks = Object.fromEntries(
    buildDashboardBlocks(overview).map((block) => [block.key, block]),
  );

  it("marks no block as missing a source", () => {
    expect(Object.values(blocks).every((block) => !block.noSource)).toBe(true);
  });

  it("shows money by method with PayOS instead of the old VNPay", () => {
    const labels = blocks.payments.rows.map((row) => row.label);
    expect(labels).toEqual(["Tổng đã thu", "Tiền mặt", "PayOS (trực tuyến)"]);
    expect(blocks.payments.rows.every((row) => row.format === "vnd")).toBe(
      true,
    );
    expect(blocks.payments.rows[2].value).toBe(200000);
  });

  it("keeps laboratory as a dash while imaging has a number", () => {
    const [imaging, lab] = blocks.imagingAndLab.rows;
    expect(imaging.value).toBe(4);
    expect(lab.value).toBeNull();
    expect(lab.note).toMatch(/Chưa có dữ liệu nguồn/);
  });

  it("adds the refund-pending snapshot row and the room queue", () => {
    const refund = blocks.pendingAndCancelled.rows.find(
      (row) => row.label === "Chờ hoàn tiền",
    );
    expect(refund).toMatchObject({ value: 5, snapshot: true });
    const room = blocks.visits.rows.find((row) => row.label === "Phòng 1");
    expect(room).toMatchObject({ value: 3, snapshot: true });
    expect(blocks.visits.emptyNotice).toBeUndefined();
  });
});
