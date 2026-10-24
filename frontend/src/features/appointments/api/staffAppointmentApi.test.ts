import { beforeEach, describe, expect, it } from "vitest";
import { isApiError, type ApiError } from "@/lib/api/apiError";
import type { AppointmentStatus } from "@/lib/domain/clinicVisit";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { DOCTOR_IDS } from "./appointmentMockData";
import { resetBookingMock } from "./bookingMockStore";
import {
  getBookingPatient,
  listStaffAppointments,
  listStaffDoctors,
} from "./staffAppointmentApi";
import { resetStaffAppointmentMock } from "./staffAppointmentMockStore";

// Đồng hồ cố định: thứ Bảy 24/10/2026, 09:00 giờ VN. Lịch mẫu hôm nay (kho chung, P03):
// apt-1004 08:00 Đã tiếp nhận (Hà) · apt-1041 08:00 Đã đặt (Minh vắng, Em: AF1) · apt-1042 08:00 Đã đặt
// (Tuấn, Cường) · apt-1005 08:15 Không đến (Hà) · apt-1006 08:30 Đã hủy (Minh) · apt-1007 16:45 Đã đặt
// (Tuấn, Cường). Chuyển bác sĩ và báo bệnh nhân: staffAppointmentReassignApi.test.ts.
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

describe("listStaffAppointments", () => {
  it("mặc định là hôm nay, sắp theo giờ, không kèm lịch của ngày khác", async () => {
    const page = await listStaffAppointments({});
    expect(page.totalItems).toBe(6);
    expect(idsOf(page.items)).toEqual([
      "apt-1004",
      "apt-1041",
      "apt-1042",
      "apt-1005",
      "apt-1006",
      "apt-1007",
    ]);
  });

  it("lọc theo ngày, bác sĩ và trạng thái", async () => {
    const dayOne = await listStaffAppointments({ date: "2026-10-26" });
    expect(idsOf(dayOne.items)).toEqual(["apt-1001"]);

    const byDoctor = await listStaffAppointments({ date: TODAY, doctorId: DOCTOR_IDS.ha });
    expect(idsOf(byDoctor.items)).toEqual(["apt-1004", "apt-1005"]);

    const booked = await listStaffAppointments({ date: TODAY, status: "BOOKED" });
    expect(idsOf(booked.items)).toEqual(["apt-1041", "apt-1042", "apt-1007"]);
  });

  it("tìm theo tên không phân biệt dấu và theo SĐT có khoảng trắng", async () => {
    const byName = await listStaffAppointments({ q: "cuong" });
    expect(idsOf(byName.items)).toEqual(["apt-1042", "apt-1007"]);

    const byPhone = await listStaffAppointments({ q: "0977 555" });
    expect(idsOf(byPhone.items)).toEqual(["apt-1042", "apt-1007"]);
  });

  it("phân trang theo size", async () => {
    const second = await listStaffAppointments({ size: 2, page: 1 });
    expect(second.totalPages).toBe(3);
    expect(second.page).toBe(1);
    expect(idsOf(second.items)).toEqual(["apt-1042", "apt-1005"]);
  });

  it("chỉ vẽ thao tác BE cho phép: đã tiếp nhận không có thao tác, đã đặt hôm nay có tiếp nhận và chuyển", async () => {
    const page = await listStaffAppointments({ date: TODAY });
    const byId = Object.fromEntries(page.items.map((item) => [item.id, item]));
    expect(byId["apt-1004"].allowedActions).toEqual([]);
    expect(byId["apt-1005"].allowedActions).toEqual([]);
    expect(byId["apt-1007"].allowedActions).toEqual(["checkIn", "reassign"]);
  });

  it("AF1: 08:00 không có bác sĩ thay thì lịch vẫn có Chuyển bác sĩ và Báo bệnh nhân", async () => {
    const page = await listStaffAppointments({ date: TODAY });
    const byId = Object.fromEntries(page.items.map((item) => [item.id, item]));
    expect(byId["apt-1041"].allowedActions).toEqual(["checkIn", "reassign", "notifyNoReplacement"]);
    expect(byId["apt-1042"].allowedActions).toEqual(["checkIn", "reassign", "notifyNoReplacement"]);
  });

  it("không trả trường nội bộ của kho (người đặt, các trường gốc)", async () => {
    const [item] = (await listStaffAppointments({ date: TODAY })).items;
    expect(Object.keys(item).sort()).toEqual(
      [
        "allowedActions",
        "bookedFor",
        "doctor",
        "examFee",
        "id",
        "isFollowUp",
        "patient",
        "roomName",
        "service",
        "startAt",
        "status",
      ].sort(),
    );
    expect(Object.keys(item.patient).sort()).toEqual(
      ["dateOfBirth", "fullName", "id", "patientCode", "phone"].sort(),
    );
  });

  it("bộ lọc sai dạng trả 400 MSG-54 đúng ô", async () => {
    const badDate = await failureOf(listStaffAppointments({ date: "2026-02-30" }));
    expect(badDate.status).toBe(400);
    expect(badDate.code).toBe("MSG-54");
    expect(badDate.fieldErrors).toEqual([{ field: "date", code: "MSG-54" }]);

    // Trạng thái lạ chỉ có ở JS (không qua kiểu), nên ép kiểu qua unknown để thử đúng nhánh sai.
    const badStatus = await failureOf(
      listStaffAppointments({ status: "CANCEL" as unknown as AppointmentStatus }),
    );
    expect(badStatus.fieldErrors).toEqual([{ field: "status", code: "MSG-54" }]);
  });
});

describe("getBookingPatient", () => {
  it("mã từ tìm kiếm (pat-an) quy về mã hồ sơ gắn tài khoản cùng BN-000123", async () => {
    const patient = await getBookingPatient("pat-an");
    expect(patient).toEqual({
      id: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f01",
      fullName: "Nguyễn Văn An",
      dateOfBirth: "1992-07-21",
      relationship: null,
    });
  });

  it("người chưa gắn tài khoản dùng chính mã lễ tân", async () => {
    const patient = await getBookingPatient("pat-binh");
    expect(patient).toEqual({
      id: "pat-binh",
      fullName: "Trần Thị Bình",
      dateOfBirth: "1985-09-02",
      relationship: null,
    });
  });

  it("không có hồ sơ → 404 MSG-29; bị khóa đặt lịch → 403 MSG-29", async () => {
    const missing = await failureOf(getBookingPatient("pat-khong-co"));
    expect(missing).toMatchObject({ status: 404, code: "MSG-29" });

    const locked = await failureOf(getBookingPatient("pat-locked"));
    expect(locked).toMatchObject({ status: 403, code: "MSG-29" });
  });
});

describe("listStaffDoctors", () => {
  it("trả mọi bác sĩ có ca; bookable = đang nhận đặt hôm nay (Minh vắng, Hà và Tuấn nhận)", async () => {
    const { items } = await listStaffDoctors();
    expect(items.map((doctor) => doctor.id)).toContain(DOCTOR_IDS.tuan);
    const bookableById = Object.fromEntries(items.map((doctor) => [doctor.id, doctor.bookable]));
    expect(bookableById).toEqual({
      [DOCTOR_IDS.ha]: true,
      [DOCTOR_IDS.minh]: false,
      [DOCTOR_IDS.tuan]: true,
    });
  });
});
