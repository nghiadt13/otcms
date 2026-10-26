import { describe, expect, it } from "vitest";
import {
  APPOINTMENT_STATUS_LABELS,
  APPOINTMENT_STATUS_TONE,
  EXAM_FEE_STATUS_LABELS,
  EXAM_FEE_STATUS_TONE,
  PAYMENT_STATUS_LABELS,
  PAYMENT_STATUS_TONE,
  QUEUE_STATUS_LABELS,
  QUEUE_STATUS_TONE,
  SERVICE_ORDER_STATUS_LABELS,
  SERVICE_ORDER_STATUS_TONE,
  VISIT_STATUS_LABELS,
  VISIT_STATUS_TONE,
} from "./clinicVisit";

describe("clinicVisit: nhãn và tone", () => {
  it("nhãn lịch hẹn theo bảng đã chốt (admin và lễ tân dùng chung)", () => {
    expect(APPOINTMENT_STATUS_LABELS).toEqual({
      BOOKED: "Đã đặt",
      CHECKED_IN: "Đã tiếp nhận",
      CANCELLED: "Đã hủy",
      NO_SHOW: "Không đến",
    });
  });

  it.each([
    ["lịch hẹn", APPOINTMENT_STATUS_LABELS, APPOINTMENT_STATUS_TONE],
    ["lượt khám", VISIT_STATUS_LABELS, VISIT_STATUS_TONE],
    ["hàng đợi", QUEUE_STATUS_LABELS, QUEUE_STATUS_TONE],
    ["chỉ định", SERVICE_ORDER_STATUS_LABELS, SERVICE_ORDER_STATUS_TONE],
    ["phí khám", EXAM_FEE_STATUS_LABELS, EXAM_FEE_STATUS_TONE],
    ["thanh toán", PAYMENT_STATUS_LABELS, PAYMENT_STATUS_TONE],
  ])("mỗi trạng thái %s đều có nhãn và tone", (_name, labels, tones) => {
    expect(Object.keys(tones).sort()).toEqual(Object.keys(labels).sort());
    for (const value of Object.values(labels)) {
      expect(value.trim().length).toBeGreaterThan(0);
    }
  });
});
