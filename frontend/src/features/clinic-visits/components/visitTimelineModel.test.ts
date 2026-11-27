import { describe, expect, it } from "vitest";
import type {
  VisitProgress,
  VisitProgressOrder,
  VisitQueueInfo,
  VisitSummary,
} from "../types/visitProgress";
import { buildVisitTimeline } from "./visitTimelineModel";

const queue = (patch: Partial<VisitQueueInfo> = {}): VisitQueueInfo => ({
  queueNumber: 4,
  status: "WAITING",
  roomCode: "LAB-1",
  peopleAhead: 1,
  beingCalled: false,
  ...patch,
});

const order = (
  patch: Partial<VisitProgressOrder> = {},
): VisitProgressOrder => ({
  orderId: "o-1",
  orderCode: "CLS-1",
  serviceName: "Xét nghiệm máu",
  status: "ORDERED",
  paymentStatus: "PAID",
  roomId: null,
  roomName: "Phòng xét nghiệm",
  queue: null,
  ...patch,
});

const progress = (patch: Partial<VisitProgress> = {}): VisitProgress => ({
  visitId: "v",
  source: "SCHEDULED",
  status: "OPEN",
  currentStage: null,
  arrivedAt: null,
  queuePosition: null,
  currentRoom: null,
  queueNumber: null,
  queueStage: null,
  peopleAhead: null,
  beingCalled: false,
  examQueue: null,
  serviceOrders: [],
  ...patch,
});

const summary = (status: VisitSummary["status"]): VisitSummary => ({
  id: "v",
  visitDate: "2026-11-27",
  status,
  patient: { id: "p", fullName: "An", relationship: null },
});

const titles = (steps: { title: string }[]) => steps.map((s) => s.title);

describe("buildVisitTimeline", () => {
  it("đang chờ khám: một lộ trình, ô việc cần làm báo số và số người trước", () => {
    const { steps, next } = buildVisitTimeline(
      progress({
        examQueue: queue({
          queueNumber: 12,
          roomCode: "EXAM-1",
          peopleAhead: 3,
        }),
      }),
      summary("WAITING_FOR_EXAMINATION"),
    );
    expect(titles(steps)).toEqual(["Khám bệnh", "Bác sĩ kết luận"]);
    expect(steps[0]).toMatchObject({
      state: "active",
      number: 12,
      room: "EXAM-1",
    });
    expect(next).toMatchObject({
      kind: "waiting",
      title: "Đang chờ bác sĩ khám",
      number: 12,
    });
  });

  it("bác sĩ chỉ định xét nghiệm: khám bệnh xong, mỗi chỉ định một dòng có số riêng", () => {
    const { steps, next } = buildVisitTimeline(
      progress({
        serviceOrders: [
          order({ queue: queue() }),
          order({ orderId: "o-2", serviceName: "X-quang", queue: null }),
        ],
      }),
      summary("AWAITING_CONCLUSION"),
    );
    expect(titles(steps)).toEqual([
      "Khám bệnh",
      "Xét nghiệm máu",
      "X-quang",
      "Bác sĩ kết luận",
    ]);
    expect(steps[0].state).toBe("done");
    expect(steps[1]).toMatchObject({ state: "active", number: 4 });
    expect(steps[2]).toMatchObject({ state: "todo", number: null });
    expect(steps[3].state).toBe("todo");
    expect(next).toMatchObject({
      kind: "waiting",
      title: "Đang chờ Xét nghiệm máu",
      number: 4,
    });
  });

  it("được gọi thắng mọi việc khác và nói vào phòng nào", () => {
    const { next } = buildVisitTimeline(
      progress({
        serviceOrders: [
          order({ queue: queue({ peopleAhead: 0 }) }),
          order({
            orderId: "o-2",
            serviceName: "X-quang",
            roomName: "Phòng X-quang",
            queue: queue({ queueNumber: 2, beingCalled: true, peopleAhead: 0 }),
          }),
        ],
      }),
      summary("AWAITING_CONCLUSION"),
    );
    expect(next).toMatchObject({ kind: "called", number: 2 });
    expect(next?.detail).toContain("Phòng X-quang");
    expect(next?.detail).toContain("X-quang");
  });

  it("chọn chỉ định ít người trước nhất và nhắc còn chỉ định khác", () => {
    const { next } = buildVisitTimeline(
      progress({
        serviceOrders: [
          order({ queue: queue({ peopleAhead: 5 }) }),
          order({
            orderId: "o-2",
            serviceName: "X-quang",
            queue: queue({ queueNumber: 8, peopleAhead: 1 }),
          }),
        ],
      }),
      summary("AWAITING_CONCLUSION"),
    );
    expect(next?.title).toBe("Đang chờ X-quang");
    expect(next?.detail).toContain("Còn 1 chỉ định khác");
  });

  it("chỉ định chưa thanh toán vẫn hiện, có nhãn và nhắc thanh toán", () => {
    const { steps, next } = buildVisitTimeline(
      progress({
        serviceOrders: [order({ paymentStatus: "PENDING_PAYMENT" })],
      }),
      summary("AWAITING_CONCLUSION"),
    );
    expect(steps[1].flag).toEqual({
      label: "Chưa thanh toán",
      tone: "warning",
    });
    expect(next).toMatchObject({
      kind: "warning",
      title: "Cần thanh toán 1 chỉ định",
    });
  });

  it("thiếu dữ liệu hàng chờ vẫn thấy đủ chỉ định, không biến mất", () => {
    const { steps } = buildVisitTimeline(
      progress({
        serviceOrders: [order(), order({ orderId: "o-2", serviceName: "MRI" })],
      }),
      null,
    );
    expect(titles(steps)).toEqual([
      "Khám bệnh",
      "Xét nghiệm máu",
      "MRI",
      "Bác sĩ kết luận",
    ]);
  });

  it("đủ kết quả: sang bước kết luận; hoàn tất thì tick hết", () => {
    const done = progress({
      serviceOrders: [order({ status: "RESULTS_AVAILABLE" })],
    });
    const waitingConclusion = buildVisitTimeline(
      done,
      summary("AWAITING_CONCLUSION"),
    );
    expect(waitingConclusion.steps.at(-1)?.state).toBe("active");
    expect(waitingConclusion.next?.title).toBe("Bác sĩ đang xem kết quả");

    const completed = buildVisitTimeline(done, summary("COMPLETED"));
    expect(completed.steps.every((step) => step.state === "done")).toBe(true);
    expect(completed.next?.kind).toBe("done");
  });

  it("chỉ định đã hủy gạch mờ và không tính vào việc phải làm; lượt dở dang không có lộ trình", () => {
    const cancelled = progress({
      serviceOrders: [order({ status: "CANCELLED" })],
    });
    const { steps, next } = buildVisitTimeline(
      cancelled,
      summary("IN_EXAMINATION"),
    );
    expect(steps[1].state).toBe("cancelled");
    expect(next?.title).toBe("Bác sĩ đang khám cho bạn");
    expect(buildVisitTimeline(cancelled, summary("INCOMPLETE"))).toEqual({
      steps: [],
      next: null,
    });
  });

  it("chưa có gì để hiện thì trả rỗng", () => {
    expect(buildVisitTimeline(progress(), null)).toEqual({
      steps: [],
      next: null,
    });
  });
});
