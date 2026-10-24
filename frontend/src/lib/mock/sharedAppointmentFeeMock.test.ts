import { beforeEach, describe, expect, it } from "vitest";
import {
  findMockAppointmentFee,
  listMockAppointmentFees,
  markMockAppointmentCancelled,
  markMockAppointmentFeePaid,
  resetMockAppointmentFees,
  upsertMockAppointmentFee,
  type MockAppointmentFee,
} from "./sharedAppointmentFeeMock";

const booked: MockAppointmentFee = {
  appointmentId: "apt-test-1",
  profileId: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f01",
  patientName: "Nguyễn Văn An",
  relationship: "SELF",
  startAt: "2026-10-27T10:00:00+07:00",
  serviceName: "Khám chỉnh hình tổng quát",
  examFeeAmount: 200000,
  examFeeStatus: "PENDING_PAYMENT",
  appointmentStatus: "BOOKED",
};

beforeEach(() => {
  resetMockAppointmentFees();
});

describe("kho phí khám giả dùng chung", () => {
  it("tra lại đúng phí đã ghi theo mã lịch; mã lạ trả undefined", () => {
    upsertMockAppointmentFee(booked);
    expect(findMockAppointmentFee("apt-test-1")).toEqual(booked);
    expect(findMockAppointmentFee("apt-khong-co")).toBeUndefined();
  });

  it("ghi lại cùng mã lịch thì thay toàn bộ dữ kiện", () => {
    upsertMockAppointmentFee(booked);
    upsertMockAppointmentFee({ ...booked, examFeeAmount: 250000 });
    expect(listMockAppointmentFees()).toHaveLength(1);
    expect(findMockAppointmentFee("apt-test-1")?.examFeeAmount).toBe(250000);
  });

  it("sửa bản ghi trả về hoặc đối tượng truyền vào không đổi kho", () => {
    const input = { ...booked };
    upsertMockAppointmentFee(input);
    input.examFeeAmount = 1;
    const found = findMockAppointmentFee("apt-test-1");
    expect(found?.examFeeAmount).toBe(200000);
    if (found) found.examFeeStatus = "PAID";
    expect(findMockAppointmentFee("apt-test-1")?.examFeeStatus).toBe("PENDING_PAYMENT");
    listMockAppointmentFees()[0].examFeeStatus = "PAID";
    expect(findMockAppointmentFee("apt-test-1")?.examFeeStatus).toBe("PENDING_PAYMENT");
  });

  it("thanh toán thành công đổi phí sang đã trả, các dữ kiện khác giữ nguyên", () => {
    upsertMockAppointmentFee(booked);
    markMockAppointmentFeePaid("apt-test-1");
    expect(findMockAppointmentFee("apt-test-1")).toEqual({
      ...booked,
      examFeeStatus: "PAID",
    });
  });

  it("hủy lịch chỉ đổi trạng thái lịch; phí đã trả không bị đổi", () => {
    upsertMockAppointmentFee(booked);
    markMockAppointmentFeePaid("apt-test-1");
    markMockAppointmentCancelled("apt-test-1");
    expect(findMockAppointmentFee("apt-test-1")).toMatchObject({
      appointmentStatus: "CANCELLED",
      examFeeStatus: "PAID",
    });
  });

  it("đánh dấu cho lịch không có trong kho thì không làm gì và không ném lỗi", () => {
    upsertMockAppointmentFee(booked);
    expect(() => markMockAppointmentFeePaid("apt-khong-co")).not.toThrow();
    expect(() => markMockAppointmentCancelled("apt-khong-co")).not.toThrow();
    expect(listMockAppointmentFees()).toEqual([booked]);
  });

  it("xóa kho thì không còn phí nào", () => {
    upsertMockAppointmentFee(booked);
    resetMockAppointmentFees();
    expect(listMockAppointmentFees()).toEqual([]);
  });
});
