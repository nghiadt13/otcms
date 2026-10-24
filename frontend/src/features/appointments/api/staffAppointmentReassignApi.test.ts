import { beforeEach, describe, expect, it } from "vitest";
import { isApiError, type ApiError } from "@/lib/api/apiError";
import {
  getLinkedProfileRecords,
  resetLinkedProfilesMock,
} from "@/lib/patient-scope/linkedProfilesMockStore";
import { DOCTOR_IDS } from "./appointmentMockData";
import { createAppointment, getOpenTimes } from "./bookingApi";
import { resetBookingMock } from "./bookingMockStore";
import { listMyAppointments } from "./patientAppointmentApi";
import {
  listStaffAppointments,
  listStaffEligibleDoctors,
  notifyStaffNoReplacement,
  reassignStaffAppointmentDoctor,
} from "./staffAppointmentApi";
import { resetStaffAppointmentMock } from "./staffAppointmentMockStore";

// Cùng kịch bản với staffAppointmentApi.test.ts: thứ Bảy 24/10/2026, 09:00 giờ VN. Lịch 16:45 của
// Cường (apt-1007, bác sĩ Tuấn) là lịch để thử chuyển và báo bệnh nhân. Lịch 08:00 của Em (apt-1041,
// bác sĩ Minh vắng) là AF1: không có ai thay. Lịch 28/10 14:00 của Lan (apt-1003, bác sĩ Tuấn) là
// lịch để thử chuyển có ghi vào kho chung.
const NOW = new Date("2026-10-24T09:00:00+07:00");
const TODAY = "2026-10-24";

beforeEach(() => {
  resetBookingMock({ now: NOW });
  resetStaffAppointmentMock();
  resetLinkedProfilesMock();
});

async function failureOf(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    if (isApiError(error)) return error;
    throw error;
  }
  throw new Error("Lẽ ra phải trả lỗi.");
}

const idsOf = (items: { id: string }[]) => items.map((item) => item.id);

describe("listStaffEligibleDoctors", () => {
  it("chỉ bác sĩ có ca cùng giờ và đang rảnh; không gồm bác sĩ hiện tại", async () => {
    const { items } = await listStaffEligibleDoctors("apt-1007");
    expect(items).toEqual([{ id: DOCTOR_IDS.ha, fullName: "BS. Phạm Thu Hà" }]);
  });

  it("lịch không còn Đã đặt → 409 MSG-32; không có lịch → 404 MSG-29", async () => {
    const checkedIn = await failureOf(listStaffEligibleDoctors("apt-1004"));
    expect(checkedIn).toMatchObject({ status: 409, code: "MSG-32" });
    const missing = await failureOf(listStaffEligibleDoctors("apt-khong-co"));
    expect(missing).toMatchObject({ status: 404, code: "MSG-29" });
  });
});

describe("reassignStaffAppointmentDoctor", () => {
  it("chuyển sang bác sĩ có ca cùng giờ: giờ giữ nguyên, phòng theo bác sĩ mới", async () => {
    const updated = await reassignStaffAppointmentDoctor("apt-1007", DOCTOR_IDS.ha);
    expect(updated.doctor).toEqual({ id: DOCTOR_IDS.ha, fullName: "BS. Phạm Thu Hà" });
    expect(updated.roomName).toBe("Phòng khám 2");
    expect(updated.startAt).toBe("2026-10-24T16:45:00+07:00");

    const page = await listStaffAppointments({ date: TODAY, doctorId: DOCTOR_IDS.ha });
    expect(idsOf(page.items)).toEqual(["apt-1004", "apt-1005", "apt-1007"]);
  });

  it("bác sĩ không còn trong danh sách chuyển → 409 MSG-24", async () => {
    // Minh vắng hôm nay nên không nằm trong danh sách chuyển của lịch 16:45.
    const error = await failureOf(reassignStaffAppointmentDoctor("apt-1007", DOCTOR_IDS.minh));
    expect(error).toMatchObject({ status: 409, code: "MSG-24" });
  });

  it("ghi vào kho chung: khu bệnh nhân thấy bác sĩ mới, giờ đó bác sĩ cũ mở ra và bác sĩ mới kín", async () => {
    const lan = getLinkedProfileRecords().find((profile) => profile.patientCode === "BN-000456");
    if (!lan) throw new Error("thiếu hồ sơ mẫu BN-000456");

    const updated = await reassignStaffAppointmentDoctor("apt-1003", DOCTOR_IDS.ha);
    expect(updated).toMatchObject({
      startAt: "2026-10-28T14:00:00+07:00",
      doctor: { id: DOCTOR_IDS.ha, fullName: "BS. Phạm Thu Hà" },
      roomName: "Phòng khám 2",
    });

    const mine = await listMyAppointments({ when: "upcoming", patientId: lan.id, size: 100 });
    expect(mine.items.find((item) => item.id === "apt-1003")).toMatchObject({
      doctor: { id: DOCTOR_IDS.ha, fullName: "BS. Phạm Thu Hà" },
      roomName: "Phòng khám 2",
    });

    const day = await getOpenTimes({
      serviceId: "svc-kham-tq",
      patientId: "pat-binh",
      from: "2026-10-28",
      to: "2026-10-28",
    });
    const slot = day.days[0].times.find((time) => time.startAt === "2026-10-28T14:00:00+07:00");
    expect(slot?.doctors.map((doctor) => doctor.id)).toEqual([DOCTOR_IDS.minh, DOCTOR_IDS.tuan]);
  });

  it("chưa chọn bác sĩ → 400 MSG-12 đúng ô; lịch không Đã đặt → 409 MSG-32; không có lịch → 404 MSG-29", async () => {
    const empty = await failureOf(reassignStaffAppointmentDoctor("apt-1007", ""));
    expect(empty).toMatchObject({ status: 400, code: "MSG-12" });
    expect(empty.fieldErrors).toEqual([{ field: "doctorId", code: "MSG-12" }]);

    const checkedIn = await failureOf(reassignStaffAppointmentDoctor("apt-1004", DOCTOR_IDS.ha));
    expect(checkedIn).toMatchObject({ status: 409, code: "MSG-32" });

    const missing = await failureOf(reassignStaffAppointmentDoctor("apt-khong-co", DOCTOR_IDS.ha));
    expect(missing).toMatchObject({ status: 404, code: "MSG-29" });
  });
});

describe("notifyStaffNoReplacement", () => {
  it("không báo khi vẫn còn bác sĩ thay → 409 MSG-32", async () => {
    const error = await failureOf(notifyStaffNoReplacement("apt-1007"));
    expect(error).toMatchObject({ status: 409, code: "MSG-32" });
  });

  it("không có bác sĩ thay: cho báo một lần, lịch vẫn Đã đặt", async () => {
    // Hà kín lúc 16:45 bởi một lịch khác (đặt qua API giả của P03): Cường không còn ai thay.
    await createAppointment({
      patientId: "pat-binh",
      serviceId: "svc-kham-cs",
      startAt: `${TODAY}T16:45:00+07:00`,
      doctorId: DOCTOR_IDS.ha,
    });

    const { items } = await listStaffEligibleDoctors("apt-1007");
    expect(items).toEqual([]);

    const before = await listStaffAppointments({ date: TODAY });
    const row = before.items.find((item) => item.id === "apt-1007");
    expect(row?.allowedActions).toEqual(["checkIn", "reassign", "notifyNoReplacement"]);

    const notified = await notifyStaffNoReplacement("apt-1007");
    expect(notified.status).toBe("BOOKED");
    expect(notified.allowedActions).toEqual(["checkIn", "reassign"]);

    const again = await failureOf(notifyStaffNoReplacement("apt-1007"));
    expect(again).toMatchObject({ status: 409, code: "MSG-32" });
  });

  it("AF1 sẵn trong kho: 08:00 của bác sĩ vắng không có ai thay, báo một lần rồi bị từ chối", async () => {
    const { items } = await listStaffEligibleDoctors("apt-1041");
    expect(items).toEqual([]);

    const notified = await notifyStaffNoReplacement("apt-1041");
    expect(notified).toMatchObject({ status: "BOOKED", allowedActions: ["checkIn", "reassign"] });

    const again = await failureOf(notifyStaffNoReplacement("apt-1041"));
    expect(again).toMatchObject({ status: 409, code: "MSG-32" });
  });
});
