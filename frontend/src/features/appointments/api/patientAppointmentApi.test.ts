import { beforeEach, describe, expect, it } from "vitest";
import { isApiError, type ApiError } from "@/lib/api/apiError";
import {
  getLinkedProfileRecords,
  resetLinkedProfilesMock,
} from "@/lib/patient-scope/linkedProfilesMockStore";
import { createAppointment } from "./bookingApi";
import { resetBookingMock } from "./bookingMockStore";
import {
  getMyAppointment,
  listMyAppointments,
  resetPatientAppointmentMock,
} from "./patientAppointmentApi";
import { RACE_APPOINTMENT_ID } from "./patientAppointmentMockData";

// Đồng hồ cố định: thứ bảy 24/10/2026, 09:00 giờ VN. Lịch mẫu tính theo ngày này.
const NOW = new Date("2026-10-24T09:00:00+07:00");

function profileId(patientCode: string): string {
  const profile = getLinkedProfileRecords().find(
    (item) => item.patientCode === patientCode,
  );
  if (!profile) throw new Error(`thiếu hồ sơ ${patientCode}`);
  return profile.id;
}

/** Lỗi mà lớp API ném ra; test thất bại nếu lời gọi không ném. */
async function errorOf(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    if (isApiError(error)) return error;
    throw error;
  }
  throw new Error("lời gọi không ném lỗi");
}

beforeEach(() => {
  resetLinkedProfilesMock();
  resetBookingMock({ now: NOW });
  resetPatientAppointmentMock();
});

describe("listMyAppointments", () => {
  it("sắp tới: chỉ lịch Đã đặt của mọi hồ sơ, sớm trước; kho dùng chung và lịch riêng đều có", async () => {
    const page = await listMyAppointments({ when: "upcoming" });
    expect(page.items.map((item) => item.id)).toEqual([
      "apt-1001",
      "apt-2101",
      "apt-1003",
      RACE_APPOINTMENT_ID,
    ]);
    expect(page.totalItems).toBe(4);
  });

  it("đã qua: mọi trạng thái khác, mới trước, chia trang 10 dòng", async () => {
    const first = await listMyAppointments({ when: "past", page: 0, size: 10 });
    expect(first.totalItems).toBe(16);
    expect(first.totalPages).toBe(2);
    expect(first.items).toHaveLength(10);
    expect(first.items[0].id).toBe("apt-expired-2");
    expect(first.items[0].status).toBe("BOOKED");

    const second = await listMyAppointments({
      when: "past",
      page: 1,
      size: 10,
    });
    expect(second.items).toHaveLength(6);
    expect(
      new Set([...first.items, ...second.items].map((item) => item.id)).size,
    ).toBe(16);
  });

  it("lịch quá hạn chưa thanh toán tự thành Đã hủy; lịch quá hạn đã trả giữ Đã đặt nhưng không có thao tác", async () => {
    const page = await listMyAppointments({ when: "past", page: 0, size: 20 });
    const unpaidPast = page.items.find((item) => item.id === "apt-expired-1");
    const paidPast = page.items.find((item) => item.id === "apt-expired-2");
    expect(unpaidPast).toMatchObject({ status: "CANCELLED", allowedActions: [] });
    expect(paidPast).toMatchObject({ status: "BOOKED", examFee: { status: "PAID" }, allowedActions: [] });
  });

  it("lọc theo hồ sơ đã gắn; người khám nào cũng ghi tên và quan hệ", async () => {
    const lan = profileId("BN-000456");
    const page = await listMyAppointments({ when: "upcoming", patientId: lan });
    expect(page.items.map((item) => item.id)).toEqual(["apt-1003"]);
    expect(page.items[0].patient).toEqual({
      id: lan,
      fullName: "Nguyễn Thị Lan",
      relationship: "PARENT",
    });
  });

  it("mã hồ sơ không thuộc tài khoản → 403 MSG-29", async () => {
    const error = await errorOf(
      listMyAppointments({ when: "upcoming", patientId: "pat-binh" }),
    );
    expect(error).toMatchObject({ status: 403, code: "MSG-29" });
  });

  it("tab không hợp lệ → 400 MSG-31 đúng ô", async () => {
    const error = await errorOf(
      listMyAppointments({ when: "soon" as "upcoming" }),
    );
    expect(error).toMatchObject({
      status: 400,
      code: "MSG-31",
      fieldErrors: [{ field: "when", code: "MSG-31" }],
    });
  });

  it("lịch vừa đặt từ luồng đặt lịch xuất hiện trong danh sách sắp tới", async () => {
    const an = profileId("BN-000123");
    const booked = await createAppointment({
      patientId: an,
      serviceId: "svc-kham-tq",
      startAt: "2026-10-27T10:00:00+07:00",
      doctorId: null,
    });
    const page = await listMyAppointments({ when: "upcoming", patientId: an });
    expect(page.items.map((item) => item.id)).toContain(booked.id);
    const created = page.items.find((item) => item.id === booked.id);
    expect(created?.allowedActions).toEqual(["cancel", "payExamFee"]);
  });

  it("hồ sơ bị bỏ gắn: lịch của hồ sơ đó không còn hiện", async () => {
    resetLinkedProfilesMock({ variant: "selfOnly" });
    const page = await listMyAppointments({ when: "upcoming" });
    expect(page.items.map((item) => item.id)).toEqual([
      "apt-1001",
      RACE_APPOINTMENT_ID,
    ]);
  });
});

describe("getMyAppointment", () => {
  it("lịch đã thanh toán: không có thao tác thanh toán, chỉ được hủy", async () => {
    const item = await getMyAppointment("apt-1003");
    expect(item.examFee).toEqual({ amount: 200000, status: "PAID" });
    expect(item.allowedActions).toEqual(["cancel"]);
  });

  it("lịch chưa thanh toán: được hủy và được trả online", async () => {
    const item = await getMyAppointment("apt-1001");
    expect(item.allowedActions).toEqual(["cancel", "payExamFee"]);
    expect(item.doctor.fullName).toBe("BS. Phạm Thu Hà");
    expect(item.roomName).toBe("Phòng khám 2");
  });

  it("lượt đã check-in: có visitId, không còn thao tác", async () => {
    const item = await getMyAppointment("apt-2001");
    expect(item).toMatchObject({
      status: "CHECKED_IN",
      visitId: "vis-an-today",
      allowedActions: [],
      isFollowUp: false,
    });
  });

  it("bác sĩ vắng không có người thay: cờ bật và vẫn được hủy", async () => {
    const item = await getMyAppointment("apt-2101");
    expect(item.doctorUnavailable).toBe(true);
    expect(item.allowedActions).toContain("cancel");
  });

  it("lịch của người khác hoặc không tồn tại → 404 MSG-29 như nhau", async () => {
    expect(await errorOf(getMyAppointment("apt-1004"))).toMatchObject({
      status: 404,
      code: "MSG-29",
    });
    expect(await errorOf(getMyAppointment("apt-khong-co"))).toMatchObject({
      status: 404,
      code: "MSG-29",
    });
  });

  it("hồ sơ đã bỏ gắn: lịch của nó trả 404 MSG-29", async () => {
    resetLinkedProfilesMock({ variant: "selfOnly" });
    expect(await errorOf(getMyAppointment("apt-1003"))).toMatchObject({
      status: 404,
      code: "MSG-29",
    });
  });
});
