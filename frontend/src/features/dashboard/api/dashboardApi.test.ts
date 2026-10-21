import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { getDashboardOverview } from "./dashboardApi";

const PERIOD = { from: "2026-10-14", to: "2026-10-20" };

async function expectApiError(promise: Promise<unknown>, status: number, code: string) {
  const err = await promise.then(
    () => null,
    (e: unknown) => e,
  );
  expect(err).toBeInstanceOf(ApiError);
  expect((err as ApiError).status).toBe(status);
  expect((err as ApiError).code).toBe(code);
}

describe("dashboardApi (mock)", () => {
  it("kỳ thường: đủ 7 khối READY, kỳ trước cùng số ngày", async () => {
    const data = await getDashboardOverview(PERIOD);
    expect(data.period).toEqual(PERIOD);
    expect(data.previousPeriod).toEqual({ from: "2026-10-07", to: "2026-10-13" });
    for (const key of [
      "appointments",
      "visits",
      "imagingAndLab",
      "services",
      "payments",
      "pendingAndCancelled",
      "reviews",
    ] as const)
      expect(data[key].status).toBe("READY");
  });

  it("số trong fixture khớp nhau", async () => {
    const data = await getDashboardOverview(PERIOD);
    if (data.appointments.status !== "READY" || data.payments.status !== "READY")
      throw new Error("fixture phải READY");
    expect(data.appointments.byStatus.reduce((s, r) => s + r.value, 0)).toBe(
      data.appointments.total.value,
    );
    expect(data.payments.byMethod.reduce((s, r) => s + r.value, 0)).toBe(
      data.payments.collected.value,
    );
  });

  it("không trả về cùng tham chiếu fixture (UI sửa không làm hỏng kho)", async () => {
    const a = await getDashboardOverview(PERIOD);
    const b = await getDashboardOverview(PERIOD);
    expect(a).not.toBe(b);
    expect(a.appointments).not.toBe(b.appointments);
  });

  it("kỳ không hoạt động: toàn 0, danh sách rỗng, chưa có điểm", async () => {
    const data = await getDashboardOverview({ from: "2000-01-01", to: "2000-01-07" });
    if (
      data.appointments.status !== "READY" ||
      data.services.status !== "READY" ||
      data.reviews.status !== "READY"
    )
      throw new Error("kỳ rỗng vẫn READY");
    expect(data.appointments.total.value).toBe(0);
    expect(data.services.top).toEqual([]);
    expect(data.reviews.averageRating.value).toBeNull();
  });

  it("kỳ số lớn có khối giảm so với kỳ trước", async () => {
    const data = await getDashboardOverview({ from: "1996-01-01", to: "1996-01-31" });
    if (data.payments.status !== "READY") throw new Error("READY");
    expect(data.payments.collected.value).toBeGreaterThan(1_000_000_000);
    expect(data.payments.collected.value).toBeLessThan(
      data.payments.collected.previousValue!,
    );
  });

  it("khối chưa có nguồn: reviews NO_SOURCE, các khối khác vẫn READY", async () => {
    const data = await getDashboardOverview({ from: "1997-01-01", to: "1997-01-07" });
    expect(data.reviews).toEqual({ status: "NO_SOURCE" });
    expect(data.appointments.status).toBe("READY");
  });

  it("ném đúng ApiError: 500 MSG-04, 403 MSG-50", async () => {
    await expectApiError(
      getDashboardOverview({ from: "1998-01-01", to: "1998-01-07" }),
      500,
      "MSG-04",
    );
    await expectApiError(
      getDashboardOverview({ from: "1999-01-01", to: "1999-01-07" }),
      403,
      "MSG-50",
    );
  });

  it("kỳ sai: from > to, sai định dạng, vượt tối đa đều 400 MSG-72", async () => {
    await expectApiError(
      getDashboardOverview({ from: "2026-10-20", to: "2026-10-14" }),
      400,
      "MSG-72",
    );
    await expectApiError(getDashboardOverview({ from: "", to: "" }), 400, "MSG-72");
    await expectApiError(
      getDashboardOverview({ from: "2024-01-01", to: "2026-10-20" }),
      400,
      "MSG-72",
    );
  });

  it("signal đã hủy thì reject AbortError", async () => {
    const controller = new AbortController();
    controller.abort();
    await expect(getDashboardOverview(PERIOD, controller.signal)).rejects.toMatchObject({
      name: "AbortError",
    });
  });
});
