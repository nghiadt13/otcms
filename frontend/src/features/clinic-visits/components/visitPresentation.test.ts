import { describe, expect, it } from "vitest";
import type {
  VisitOrderProgress,
  VisitQueuePosition,
} from "../types/visitProgress";
import {
  orderPlaceLabel,
  peopleAheadText,
  visitDateLabel,
  visitPatientLabel,
} from "./visitPresentation";

const queue = (overrides: Partial<VisitQueuePosition>): VisitQueuePosition => ({
  roomName: "Phòng khám 2",
  queueType: "EXAMINATION",
  queueNumber: 12,
  peopleAhead: 3,
  status: "WAITING",
  ...overrides,
});

describe("visitDateLabel", () => {
  it("ghi thứ theo ngày, không lệch theo múi giờ máy", () => {
    expect(visitDateLabel("2026-10-23")).toBe("Thứ sáu 23/10/2026");
    expect(visitDateLabel("2026-10-26")).toBe("Thứ hai 26/10/2026");
  });
});

describe("visitPatientLabel", () => {
  it("luôn ghi người khám và quan hệ", () => {
    expect(
      visitPatientLabel({
        id: "p1",
        fullName: "Nguyễn Thị Lan",
        relationship: "PARENT",
      }),
    ).toBe("Nguyễn Thị Lan · Cha/Mẹ");
  });
});

describe("peopleAheadText", () => {
  it("đang chờ: ghi số người phía trước bằng chữ", () => {
    expect(peopleAheadText(queue({ peopleAhead: 3 }))).toBe(
      "Còn 3 người trước bạn.",
    );
  });

  it("đang chờ và không còn ai trước: báo đến lượt kế tiếp", () => {
    expect(peopleAheadText(queue({ peopleAhead: 0 }))).toBe(
      "Bạn là người tiếp theo.",
    );
  });

  it("đang được gọi: dòng ghi 'Đang gọi bạn'", () => {
    expect(
      peopleAheadText(queue({ status: "BEING_SERVED", peopleAhead: 0 })),
    ).toBe("Đang gọi bạn");
  });

  it("vắng mặt: hướng dẫn quay lại quầy lễ tân", () => {
    expect(peopleAheadText(queue({ status: "ABSENT" }))).toContain(
      "quầy lễ tân",
    );
  });

  it("trạng thái đã đóng không hiện trong danh sách chờ", () => {
    expect(peopleAheadText(queue({ status: "DONE" }))).toBe("");
  });
});

describe("orderPlaceLabel", () => {
  const order = (
    overrides: Partial<VisitOrderProgress>,
  ): VisitOrderProgress => ({
    id: "svc-1",
    serviceName: "X-quang khớp gối",
    kind: "IMAGING",
    roomName: null,
    status: "PENDING_PAYMENT",
    cancellationReason: null,
    ...overrides,
  });

  it("ghi loại và phòng khi đã có phòng", () => {
    expect(orderPlaceLabel(order({ roomName: "Phòng X-quang 1" }))).toBe(
      "Chẩn đoán hình ảnh · Phòng X-quang 1",
    );
  });

  it("chưa có phòng thì chỉ ghi loại", () => {
    expect(orderPlaceLabel(order({ kind: "LABORATORY" }))).toBe("Xét nghiệm");
  });
});
