import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetReceptionMockStore } from "./receptionMockStore";
import { rejectionOf } from "./checkInTestErrors";
import { listPriorityGroups, listTodayAppointments, getCheckInContext } from "./checkInApi";
import { MOCK_TODAY, PRIORITY_GROUPS } from "./checkInMockData";
import type { TodayAppointmentStatusFilter } from "../types/checkIn";

beforeEach(() => resetReceptionMockStore());
afterEach(() => vi.useRealTimers());

describe("listTodayAppointments", () => {
  it("mặc định là lịch Đã đặt: 14 dòng và đếm 14 chờ, 9 đã tiếp nhận", async () => {
    const response = await listTodayAppointments("BOOKED");
    expect(response.items).toHaveLength(14);
    expect(response.items.every((row) => row.status === "BOOKED")).toBe(true);
    expect(response.counts).toEqual({ waiting: 14, checkedIn: 9 });
  });

  it("tab Đã tiếp nhận có 9 dòng", async () => {
    const response = await listTodayAppointments("CHECKED_IN");
    expect(response.items).toHaveLength(9);
    expect(response.items.every((row) => row.status === "CHECKED_IN")).toBe(true);
  });

  it("trạng thái rỗng là tất cả, gồm lịch đã hủy, nhưng đếm vẫn theo lịch đang chờ", async () => {
    const response = await listTodayAppointments("");
    expect(response.items).toHaveLength(24);
    expect(response.items.some((row) => row.status === "CANCELLED")).toBe(true);
    expect(response.counts).toEqual({ waiting: 14, checkedIn: 9 });
  });

  it("mọi lịch đều thuộc ngày hôm nay và xếp theo giờ", async () => {
    const { items } = await listTodayAppointments("");
    expect(items.every((row) => row.startAt.startsWith(MOCK_TODAY))).toBe(true);
    const times = items.map((row) => row.startAt);
    expect([...times].sort()).toEqual(times);
  });

  it("trạng thái lạ bị từ chối với MSG-39", async () => {
    const error = await rejectionOf(
      listTodayAppointments("DONE" as TodayAppointmentStatusFilter),
    );
    expect(error).toMatchObject({ status: 400, code: "MSG-39" });
  });
});

describe("getCheckInContext", () => {
  it("lịch hẹn của bệnh nhân: lựa chọn Lịch hẹn kèm Vãng lai, phí chưa trả", async () => {
    const context = await getCheckInContext({ appointmentId: "apt-1001" });
    expect(context.patient).toMatchObject({ id: "pat-an", patientCode: "BN-000123" });
    expect(context.options.map((option) => option.kind)).toEqual(["APPOINTMENT", "WALK_IN"]);
    const [appointment] = context.options;
    expect(appointment.kind === "APPOINTMENT" && appointment.examFee).toEqual({
      amount: 200000,
      status: "PENDING_PAYMENT",
      paidOnline: false,
    });
    expect(context.ineligible).toEqual([]);
    expect(context.notice).toBeNull();
  });

  it("lịch đã trả online: phí PAID và đánh dấu trả online", async () => {
    const context = await getCheckInContext({ patientId: "pat-binh" });
    const appointment = context.options[0];
    expect(appointment.kind === "APPOINTMENT" && appointment.examFee).toEqual({
      amount: 200000,
      status: "PAID",
      paidOnline: true,
    });
  });

  it("lượt đang Chờ kết luận trong 7 ngày: chỉ Quay lại, không thu phí, không vãng lai", async () => {
    const context = await getCheckInContext({ patientId: "pat-cuong" });
    expect(context.options.map((option) => option.kind)).toEqual(["RETURN_VISIT"]);
    const [revisit] = context.options;
    expect(revisit.kind === "RETURN_VISIT" && revisit.dayOfPeriod).toBe(4);
    expect(revisit.kind === "RETURN_VISIT" && revisit.examFee.status).toBe("NOT_REQUIRED");
    expect(revisit.kind === "RETURN_VISIT" && revisit.nextQueue.type).toBe("RESULT_REVIEW");
  });

  it("lượt trước chưa hoàn tất quá 7 ngày: có notice và lựa chọn Vãng lai", async () => {
    const context = await getCheckInContext({ patientId: "pat-dung" });
    expect(context.notice).toBe("PREVIOUS_VISIT_INCOMPLETE");
    expect(context.options.map((option) => option.kind)).toEqual(["WALK_IN"]);
  });

  it("lịch hôm qua không đến và lịch hủy hôm nay chỉ nằm ở ineligible", async () => {
    const context = await getCheckInContext({ patientId: "pat-em" });
    expect(context.ineligible.map((item) => item.reason)).toEqual(["NO_SHOW", "CANCELLED"]);
    expect(context.options.map((option) => option.kind)).toEqual(["WALK_IN"]);
  });

  it("lịch đã tiếp nhận: ineligible có giờ, số và phòng; không còn lựa chọn", async () => {
    const context = await getCheckInContext({ appointmentId: "apt-1020" });
    expect(context.ineligible).toEqual([
      expect.objectContaining({
        appointmentId: "apt-1020",
        reason: "ALREADY_CHECKED_IN",
        checkedIn: expect.objectContaining({ queueNumber: 7, roomName: "Phòng khám 2" }),
      }),
    ]);
    expect(context.options).toEqual([]);
  });

  it("hồ sơ tạo ở UC10 (vãng lai) chỉ có lựa chọn Vãng lai với hai dịch vụ Khám", async () => {
    const context = await getCheckInContext({ patientId: "pat-moi" });
    expect(context.options).toEqual([
      expect.objectContaining({
        kind: "WALK_IN",
        services: [
          expect.objectContaining({ id: "svc-kham-tq", price: 200000 }),
          expect.objectContaining({ id: "svc-kham-cs", price: 250000 }),
        ],
      }),
    ]);
  });

  it("đầu vào sai hoặc không tìm thấy: 400 MSG-39, 404 MSG-41", async () => {
    expect(await rejectionOf(getCheckInContext({ patientId: "  " }))).toMatchObject({
      status: 400,
      code: "MSG-39",
    });
    expect(await rejectionOf(getCheckInContext({ patientId: "pat-khong-co" }))).toMatchObject({
      status: 404,
      code: "MSG-41",
    });
    expect(await rejectionOf(getCheckInContext({ appointmentId: "apt-khong-co" }))).toMatchObject({
      status: 404,
      code: "MSG-41",
    });
  });
});

describe("listPriorityGroups", () => {
  it("trả đủ nhóm ưu tiên theo cấu hình", async () => {
    const { items } = await listPriorityGroups();
    expect(items).toEqual(PRIORITY_GROUPS);
    expect(items[0]).toEqual({ code: "EMERGENCY", label: "Cấp cứu" });
  });
});

