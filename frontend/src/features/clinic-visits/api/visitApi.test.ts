import { beforeEach, describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  getLinkedProfileRecords,
  resetLinkedProfilesMock,
} from "@/lib/patient-scope/linkedProfilesMockStore";
import type { VisitWhen } from "../types/visitProgress";
import { getVisitProgress, listVisits } from "./visitApi";
import { resetVisitMock } from "./visitMockStore";

// Hợp đồng lớp API giả của lượt khám: phạm vi hồ sơ, mã lỗi MSG, hàng đợi tiến dần và các hành
// động theo trạng thái. Đồng hồ cố định 24/10/2026 để tab "Gần đây" (30 ngày) ổn định.
const NOW = new Date("2026-10-24T08:00:00+07:00");

function profileIdOf(patientCode: string): string {
  const profile = getLinkedProfileRecords().find(
    (item) => item.patientCode === patientCode,
  );
  if (!profile) throw new Error(`thiếu hồ sơ ${patientCode}`);
  return profile.id;
}

beforeEach(() => {
  resetLinkedProfilesMock();
  resetVisitMock({ now: NOW });
});

describe("listVisits", () => {
  it("đang diễn ra chỉ gồm Chờ khám, Đang khám, Chờ kết luận; mới trước", async () => {
    const page = await listVisits({ when: "active", page: 0, size: 10 });
    expect(page.items.map((item) => item.id)).toEqual([
      "vis-an-today",
      "vis-lan-await",
    ]);
  });

  it("gần đây gồm mọi trạng thái trong 30 ngày, mới trước", async () => {
    const page = await listVisits({ when: "recent", page: 0, size: 10 });
    expect(page.items.map((item) => item.id)).toEqual([
      "vis-huy-left",
      "vis-an-today",
      "vis-lan-await",
      "vis-an-done",
      "vis-lan-incomplete",
    ]);
  });

  it("lọc theo hồ sơ đã gắn qua patientId", async () => {
    const page = await listVisits({
      when: "recent",
      patientId: profileIdOf("BN-000456"),
      page: 0,
      size: 10,
    });
    expect(page.items.map((item) => item.id)).toEqual([
      "vis-lan-await",
      "vis-lan-incomplete",
    ]);
    expect(
      page.items.every((item) => item.patient.relationship === "PARENT"),
    ).toBe(true);
  });

  it("patientId không thuộc tài khoản → 403 MSG-40", async () => {
    await expect(
      listVisits({
        when: "active",
        patientId: "00000000-0000-0000-0000-000000000000",
        page: 0,
        size: 10,
      }),
    ).rejects.toMatchObject({ status: 403, code: "MSG-40" });
  });

  it("when lạ → 400 MSG-31 kèm lỗi theo trường", async () => {
    const error = await listVisits({
      when: "later" as unknown as VisitWhen,
      page: 0,
      size: 10,
    }).catch((err: unknown) => err);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 400,
      code: "MSG-31",
      fieldErrors: [{ field: "when", code: "MSG-31" }],
    });
  });

  it("bỏ gắn hồ sơ thì lượt của hồ sơ đó không còn hiện", async () => {
    resetLinkedProfilesMock({ variant: "selfOnly" });
    const page = await listVisits({ when: "active", page: 0, size: 10 });
    expect(page.items.map((item) => item.id)).toEqual(["vis-an-today"]);
  });
});

describe("getVisitProgress", () => {
  it("Chờ kết luận: hai hàng đợi song song, hạn quay lại, chỉ có thanh toán", async () => {
    const progress = await getVisitProgress("vis-lan-await");
    expect(progress.queues).toEqual([
      {
        roomName: "Phòng X-quang 1",
        queueType: "SERVICE",
        queueNumber: 5,
        peopleAhead: 1,
        status: "WAITING",
      },
      {
        roomName: "Phòng xét nghiệm",
        queueType: "SERVICE",
        queueNumber: 8,
        peopleAhead: 0,
        status: "BEING_SERVED",
      },
    ]);
    expect(progress.returnDeadline).toBe("2026-10-29");
    expect(progress.allowedActions).toEqual(["payOrders"]);
    expect(progress.updatedAt).toMatch(/\+07:00$/);
  });

  it("Hoàn tất: không hàng đợi, có xem kết quả và đánh giá", async () => {
    const progress = await getVisitProgress("vis-an-done");
    expect(progress.queues).toEqual([]);
    expect(progress.allowedActions).toEqual(["viewResults", "review"]);
    expect(progress.returnDeadline).toBeNull();
  });

  it("Đã rời đi: không hàng đợi, không hành động", async () => {
    const progress = await getVisitProgress("vis-huy-left");
    expect(progress.status).toBe("CANCELLED_LEFT");
    expect(progress.queues).toEqual([]);
    expect(progress.allowedActions).toEqual([]);
  });

  it("chờ khám có chỉ định chưa thanh toán: được thanh toán, chưa có kết quả", async () => {
    const progress = await getVisitProgress("vis-an-today");
    expect(progress.allowedActions).toEqual(["payOrders"]);
    expect(
      progress.orders.find((order) => order.id === "svc-cbc")?.status,
    ).toBe("PENDING_PAYMENT");
  });

  it("mỗi lần đọc số người phía trước giảm 1; về 0 thì đang gọi", async () => {
    const seen: Array<[number, string]> = [];
    for (let read = 0; read < 4; read += 1) {
      const progress = await getVisitProgress("vis-an-today");
      seen.push([progress.queues[0].peopleAhead, progress.queues[0].status]);
    }
    expect(seen).toEqual([
      [3, "WAITING"],
      [2, "WAITING"],
      [1, "WAITING"],
      [0, "BEING_SERVED"],
    ]);
  });

  it("mã lượt lạ → 404 MSG-41", async () => {
    await expect(getVisitProgress("vis-khong-co")).rejects.toMatchObject({
      status: 404,
      code: "MSG-41",
    });
  });

  it("lượt của hồ sơ đã bỏ gắn → 403 MSG-40", async () => {
    resetLinkedProfilesMock({ variant: "selfOnly" });
    await expect(getVisitProgress("vis-lan-await")).rejects.toMatchObject({
      status: 403,
      code: "MSG-40",
    });
  });
});
