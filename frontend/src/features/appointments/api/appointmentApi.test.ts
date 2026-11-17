import { beforeEach, describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  listAppointments,
  listEligibleDoctors,
  lookupDoctors,
  reassignAppointmentDoctor,
  resetAppointmentMock,
} from "./appointmentApi";
import { DOCTOR_IDS, apptId } from "./appointmentMockData";

async function failure(promise: Promise<unknown>) {
  try {
    await promise;
  } catch (error) {
    return error as ApiError;
  }
  throw new Error("Mong đợi ApiError nhưng không có lỗi");
}

beforeEach(() => resetAppointmentMock());

describe("listAppointments", () => {
  it("lọc theo ngày, bác sĩ, trạng thái và tìm tên không dấu", async () => {
    const byDay = await listAppointments({
      from: "2026-10-20",
      to: "2026-10-20",
    });
    expect(byDay.items.map((i) => i.id)).toEqual([
      apptId(4),
      apptId(5),
      apptId(6),
    ]);

    const byDoctor = await listAppointments({
      doctorId: DOCTOR_IDS.ha,
      status: "BOOKED",
    });
    expect(
      byDoctor.items.every(
        (i) => i.doctor?.id === DOCTOR_IDS.ha && i.status === "BOOKED",
      ),
    ).toBe(true);

    const byName = await listAppointments({ q: "nguyen thi hoa" });
    expect(byName.items.map((i) => i.id)).toEqual([apptId(1)]);
  });

  it("phân trang nhiều trang, sắp xếp theo giờ và không lộ SĐT", async () => {
    const first = await listAppointments({
      from: "2026-10-20",
      to: "2026-10-27",
      page: 0,
      size: 20,
    });
    expect(first.totalItems).toBe(48);
    expect(first.totalPages).toBe(3);
    const times = first.items.map((i) => i.startAt);
    expect([...times].sort()).toEqual(times);
    expect(first.items[0]).not.toHaveProperty("patientPhone");
  });

  it("from > to trả 400 MSG-54", async () => {
    const error = await failure(
      listAppointments({ from: "2026-10-27", to: "2026-10-20" }),
    );
    expect([error.status, error.code]).toEqual([400, "MSG-54"]);
  });
});

describe("reassign", () => {
  it("chuyển thành công: đổi bác sĩ, giữ giờ", async () => {
    const eligible = await listEligibleDoctors(apptId(1));
    expect(eligible.items.map((d) => d.fullName)).toContain("BS. Đỗ Minh Tuấn");
    const updated = await reassignAppointmentDoctor(apptId(1), DOCTOR_IDS.tuan);
    expect(updated.doctor?.id).toBe(DOCTOR_IDS.tuan);
    expect(updated.startAt).toBe("2026-10-21T08:00:00+07:00");
  });

  it("AF1: không có ứng viên; ứng viên sai thì 409 MSG-24", async () => {
    expect((await listEligibleDoctors(apptId(2))).items).toEqual([]);
    const error = await failure(
      reassignAppointmentDoctor(apptId(2), DOCTOR_IDS.tuan),
    );
    expect([error.status, error.code]).toEqual([409, "MSG-24"]);
  });

  it("mất ca khi chọn: 409 MSG-24; đổi trạng thái giữa chừng: 409 MSG-32; không có: 404 MSG-29", async () => {
    const lost = await failure(
      reassignAppointmentDoctor(apptId(9), DOCTOR_IDS.minh),
    );
    expect([lost.status, lost.code]).toEqual([409, "MSG-24"]);
    const stale = await failure(
      reassignAppointmentDoctor(apptId(8), DOCTOR_IDS.tuan),
    );
    expect([stale.status, stale.code]).toEqual([409, "MSG-32"]);
    const notBooked = await failure(listEligibleDoctors(apptId(4)));
    expect([notBooked.status, notBooked.code]).toEqual([409, "MSG-32"]);
    const missing = await failure(
      reassignAppointmentDoctor("khong-co", DOCTOR_IDS.tuan),
    );
    expect([missing.status, missing.code]).toEqual([404, "MSG-29"]);
  });

  it("thiếu doctorId: 400 MSG-12 có fieldErrors", async () => {
    const error = await failure(reassignAppointmentDoctor(apptId(1), ""));
    expect(error.status).toBe(400);
    expect(error.fieldErrors).toEqual([{ field: "doctorId", code: "MSG-12" }]);
  });
});

describe("lookupDoctors", () => {
  it("bookable=true chỉ trả bác sĩ nhận đặt lịch", async () => {
    const all = await lookupDoctors();
    const bookable = await lookupDoctors({ bookable: true });
    expect(all.items).toHaveLength(6);
    expect(bookable.items).toHaveLength(5);
    expect(bookable.items.every((d) => d.bookable)).toBe(true);
  });
});
