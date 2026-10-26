import { beforeEach, describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { addDays } from "@/lib/format/formatDate";
import {
  getLinkedProfileRecords,
  resetLinkedProfilesMock,
} from "@/lib/patient-scope/linkedProfilesMockStore";
import { DOCTOR_IDS } from "./appointmentMockData";
import {
  createAppointment,
  getOpenTimes,
  listBookableServices,
} from "./bookingApi";
import {
  FAILING_SERVICE_ID,
  FULL_DAY_OFFSET,
  LOCKED_PATIENT_ID,
  REFUSING_DAY_OFFSET,
  TAKEN_TODAY_TIME,
} from "./bookingMockCatalog";
import { readStoredAppointments, resetBookingMock } from "./bookingMockStore";
import type { CreateAppointmentRequest } from "../types/booking";

// Thứ sáu 23/10/2026, 07:00 giờ VN: mọi giờ 08:00 trở đi còn ở phía trước.
const NOW = new Date("2026-10-23T07:00:00+07:00");
const TODAY = "2026-10-23";
const BINH = "pat-binh";

const linkedId = (patientCode: string) =>
  getLinkedProfileRecords().find(
    (profile) => profile.patientCode === patientCode,
  )?.id ?? "";

async function failure(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    return error as ApiError;
  }
  throw new Error("Mong đợi ApiError nhưng không có lỗi");
}

const request = (
  overrides: Partial<CreateAppointmentRequest> & { startAt: string },
): CreateAppointmentRequest => ({
  patientId: BINH,
  serviceId: "svc-kham-tq",
  doctorId: null,
  ...overrides,
});

beforeEach(() => {
  resetLinkedProfilesMock();
  resetBookingMock({ now: NOW });
});

describe("listBookableServices", () => {
  it("chỉ trả dịch vụ Khám, sắp theo tên", async () => {
    const { items } = await listBookableServices();
    expect(items.map((item) => [item.name, item.price])).toEqual([
      ["Khám chỉnh hình tổng quát", 200000],
      ["Khám cột sống", 250000],
    ]);
    expect(items[0].bookingDetails).toBe("Mang theo phim chụp cũ nếu có.");
  });
});

describe("getOpenTimes", () => {
  it("mặc định 14 ngày từ hôm nay, có serverNow và bước giờ 15 phút", async () => {
    const response = await getOpenTimes({
      serviceId: "svc-kham-tq",
      patientId: BINH,
    });
    expect(response.from).toBe(TODAY);
    expect(response.to).toBe(addDays(TODAY, 13));
    expect(response.days).toHaveLength(14);
    expect(response.slotMinutes).toBe(15);
    expect(response.serverNow).toBe("2026-10-23T07:00:00+07:00");
  });

  it("loại giờ đã qua so với đồng hồ máy chủ", async () => {
    resetBookingMock({ now: new Date("2026-10-23T08:05:00+07:00") });
    const response = await getOpenTimes({
      serviceId: "svc-kham-tq",
      patientId: BINH,
    });
    const today = response.days[0];
    expect(today.times.map((time) => time.startAt)).not.toContain(
      "2026-10-23T08:00:00+07:00",
    );
    expect(today.times[0].startAt).toBe("2026-10-23T08:15:00+07:00");
  });

  it("Chủ nhật không có ca nên không có giờ", async () => {
    const sunday = "2026-11-01";
    const response = await getOpenTimes({
      serviceId: "svc-kham-tq",
      patientId: BINH,
      from: sunday,
      to: sunday,
    });
    expect(response.days).toEqual([{ date: sunday, times: [] }]);
  });

  it("ngày kín lịch trên dải trả times rỗng", async () => {
    const full = addDays(TODAY, FULL_DAY_OFFSET);
    const response = await getOpenTimes({
      serviceId: "svc-kham-tq",
      patientId: BINH,
      from: full,
      to: full,
    });
    expect(response.days[0].times).toEqual([]);
  });

  it("giờ đã có lịch của một bác sĩ thì chỉ liệt kê bác sĩ còn trống", async () => {
    // Thứ ba 27/10: Hà nhận lịch 10:00 đầu tiên, sau đó giờ đó chỉ còn Minh và Tuấn.
    const day = "2026-10-27";
    await createAppointment(request({ startAt: `${day}T10:00:00+07:00` }));
    const response = await getOpenTimes({
      serviceId: "svc-kham-tq",
      patientId: BINH,
      from: day,
      to: day,
    });
    const slot = response.days[0].times.find(
      (time) => time.startAt === `${day}T10:00:00+07:00`,
    );
    expect(slot?.doctors.map((doctor) => doctor.id)).toEqual([
      DOCTOR_IDS.minh,
      DOCTOR_IDS.tuan,
    ]);
  });

  it("khoảng sai (từ sau đến, quá 14 ngày, quá khứ) → 400 MSG-31", async () => {
    const reversed = await failure(
      getOpenTimes({
        serviceId: "svc-kham-tq",
        patientId: BINH,
        from: "2026-10-28",
        to: "2026-10-27",
      }),
    );
    expect(reversed).toMatchObject({ status: 400, code: "MSG-31" });
    expect(reversed.fieldErrors).toEqual([{ field: "to", code: "MSG-31" }]);

    const tooLong = await failure(
      getOpenTimes({
        serviceId: "svc-kham-tq",
        patientId: BINH,
        from: TODAY,
        to: addDays(TODAY, 14),
      }),
    );
    expect(tooLong).toMatchObject({ status: 400, code: "MSG-31" });

    const past = await failure(
      getOpenTimes({
        serviceId: "svc-kham-tq",
        patientId: BINH,
        from: "2026-10-20",
        to: "2026-10-21",
      }),
    );
    expect(past).toMatchObject({ status: 400, code: "MSG-31" });
  });

  it("dịch vụ không đặt được → 404 MSG-29; lỗi máy chủ giả → 500 MSG-04", async () => {
    expect(
      await failure(getOpenTimes({ serviceId: "svc-xquang", patientId: BINH })),
    ).toMatchObject({ status: 404, code: "MSG-29" });
    expect(
      await failure(
        getOpenTimes({ serviceId: FAILING_SERVICE_ID, patientId: BINH }),
      ),
    ).toMatchObject({ status: 500, code: "MSG-04" });
  });

  it("người bị khóa → 403 MSG-29; không có hồ sơ → 404 MSG-29", async () => {
    expect(
      await failure(
        getOpenTimes({
          serviceId: "svc-kham-tq",
          patientId: LOCKED_PATIENT_ID,
        }),
      ),
    ).toMatchObject({ status: 403, code: "MSG-29" });
    expect(
      await failure(
        getOpenTimes({ serviceId: "svc-kham-tq", patientId: "pat-khong-co" }),
      ),
    ).toMatchObject({ status: 404, code: "MSG-29" });
  });
});

describe("createAppointment", () => {
  it("phòng khám sắp xếp: BE gán bác sĩ đầu tiên còn trống, lịch vào kho chung", async () => {
    const booked = await createAppointment(
      request({ startAt: "2026-10-27T10:00:00+07:00" }),
    );
    expect(booked).toEqual({
      id: expect.stringMatching(/^apt-/),
      startAt: "2026-10-27T10:00:00+07:00",
      patient: { id: BINH, fullName: "Trần Thị Bình" },
      service: { id: "svc-kham-tq", name: "Khám chỉnh hình tổng quát" },
      doctor: { id: DOCTOR_IDS.ha, fullName: "BS. Phạm Thu Hà" },
      roomName: "Phòng khám 2",
      status: "BOOKED",
      examFee: { amount: 200000, status: "PENDING_PAYMENT" },
      isFollowUp: false,
    });
    expect(booked).not.toHaveProperty("patientPhone");
    expect(readStoredAppointments().map((item) => item.id)).toContain(
      booked.id,
    );
  });

  it("chọn bác sĩ cụ thể đang nhận giờ đó: giữ đúng bác sĩ", async () => {
    const booked = await createAppointment(
      request({
        startAt: "2026-10-27T10:30:00+07:00",
        doctorId: DOCTOR_IDS.minh,
      }),
    );
    expect(booked.doctor.id).toBe(DOCTOR_IDS.minh);
  });

  it("giờ 09:00 hôm nay đã bị người khác giữ → 409 MSG-30; sau đó biến khỏi danh sách", async () => {
    const todayNine = `${TODAY}T${TAKEN_TODAY_TIME}:00+07:00`;
    const before = await getOpenTimes({
      serviceId: "svc-kham-tq",
      patientId: BINH,
      from: TODAY,
      to: TODAY,
    });
    expect(before.days[0].times.map((time) => time.startAt)).toContain(
      todayNine,
    );

    const error = await failure(
      createAppointment(request({ startAt: todayNine })),
    );
    expect(error).toMatchObject({ status: 409, code: "MSG-30" });

    const after = await getOpenTimes({
      serviceId: "svc-kham-tq",
      patientId: BINH,
      from: TODAY,
      to: TODAY,
    });
    expect(after.days[0].times.map((time) => time.startAt)).not.toContain(
      todayNine,
    );
    expect(
      await failure(createAppointment(request({ startAt: todayNine }))),
    ).toMatchObject({ code: "MSG-30" });
  });

  it("giờ đã qua → 409 MSG-30", async () => {
    resetBookingMock({ now: new Date("2026-10-23T10:00:00+07:00") });
    const error = await failure(
      createAppointment(request({ startAt: "2026-10-23T08:30:00+07:00" })),
    );
    expect(error).toMatchObject({ status: 409, code: "MSG-30" });
  });

  it("bác sĩ đã chọn không nhận ngày mai → 409 MSG-24", async () => {
    const tomorrow = addDays(TODAY, REFUSING_DAY_OFFSET);
    const error = await failure(
      createAppointment(
        request({
          startAt: `${tomorrow}T09:00:00+07:00`,
          doctorId: DOCTOR_IDS.tuan,
        }),
      ),
    );
    expect(error).toMatchObject({ status: 409, code: "MSG-24" });
  });

  it("bác sĩ đã chọn đang bận đúng giờ → 409 MSG-24", async () => {
    const day = "2026-10-27";
    await createAppointment(
      request({ startAt: `${day}T11:00:00+07:00`, doctorId: DOCTOR_IDS.ha }),
    );
    const error = await failure(
      createAppointment(
        request({
          patientId: "pat-cuong",
          startAt: `${day}T11:00:00+07:00`,
          doctorId: DOCTOR_IDS.ha,
        }),
      ),
    );
    expect(error).toMatchObject({ status: 409, code: "MSG-24" });
  });

  it("cùng một người đặt trùng giờ → 400 MSG-31", async () => {
    const startAt = "2026-10-27T13:30:00+07:00";
    await createAppointment(request({ startAt }));
    const error = await failure(createAppointment(request({ startAt })));
    expect(error).toMatchObject({ status: 400, code: "MSG-31" });
  });

  it("thiếu trường, giờ lệch bước, hoặc ngoài 14 ngày → 400 MSG-31 đúng ô", async () => {
    const offGrid = await failure(
      createAppointment(request({ startAt: "2026-10-27T08:10:00+07:00" })),
    );
    expect(offGrid.fieldErrors).toEqual([{ field: "startAt", code: "MSG-31" }]);

    const missing = await failure(
      createAppointment(
        request({ patientId: "", startAt: "2026-10-27T08:00:00+07:00" }),
      ),
    );
    expect(missing.fieldErrors).toEqual([
      { field: "patientId", code: "MSG-31" },
    ]);

    const beyond = await failure(
      createAppointment(
        request({ startAt: `${addDays(TODAY, 14)}T08:00:00+07:00` }),
      ),
    );
    expect(beyond).toMatchObject({ status: 400, code: "MSG-31" });
  });

  it("người bị khóa → 403 MSG-29; hồ sơ đã gỡ khỏi tài khoản → 404 MSG-29", async () => {
    expect(
      await failure(
        createAppointment(
          request({
            patientId: LOCKED_PATIENT_ID,
            startAt: "2026-10-27T08:00:00+07:00",
          }),
        ),
      ),
    ).toMatchObject({ status: 403, code: "MSG-29" });

    const lan = linkedId("BN-000456");
    resetLinkedProfilesMock({ variant: "selfOnly" });
    expect(
      await failure(
        createAppointment(
          request({ patientId: lan, startAt: "2026-10-27T08:00:00+07:00" }),
        ),
      ),
    ).toMatchObject({ status: 404, code: "MSG-29" });
  });

  it("mã lễ tân pat-an và hồ sơ gắn tài khoản cùng BN-000123 quy về một người", async () => {
    const an = linkedId("BN-000123");
    const booked = await createAppointment(
      request({ patientId: "pat-an", startAt: "2026-10-27T08:00:00+07:00" }),
    );
    expect(booked.patient).toEqual({ id: an, fullName: "Nguyễn Văn An" });
    const stored = readStoredAppointments().find(
      (item) => item.id === booked.id,
    );
    expect(stored?.patientCode).toBe("BN-000123");
    expect(stored?.bookedFor).toEqual({ kind: "SELF" });
  });

  it("người phụ thuộc đã gắn: đặt cho người đó, ghi người giữ tài khoản", async () => {
    const lan = linkedId("BN-000456");
    const booked = await createAppointment(
      request({ patientId: lan, startAt: "2026-10-27T15:00:00+07:00" }),
    );
    expect(booked.patient.id).toBe(lan);
    const stored = readStoredAppointments().find(
      (item) => item.id === booked.id,
    );
    expect(stored?.bookedFor).toEqual({
      kind: "DEPENDENT",
      accountHolderName: "Nguyễn Văn An",
    });
  });
});

describe("kho lịch dùng chung", () => {
  it("có lịch mẫu của khu bệnh nhân và khu Lễ tân, sắp theo giờ", () => {
    const items = readStoredAppointments();
    expect(items.length).toBeGreaterThanOrEqual(7);
    const starts = items.map((item) => item.startAt);
    expect([...starts].sort()).toEqual(starts);
    expect(
      items.some((item) => item.patient.id === linkedId("BN-000123")),
    ).toBe(true);
    expect(items.some((item) => item.status === "CHECKED_IN")).toBe(true);
  });

  it("đọc trả về bản sao: sửa bản sao không đổi kho", () => {
    const copy = readStoredAppointments().find(
      (item) => item.id === "apt-1001",
    );
    if (!copy) throw new Error("thiếu lịch mẫu apt-1001");
    copy.status = "CANCELLED";
    expect(
      readStoredAppointments().find((item) => item.id === "apt-1001")?.status,
    ).toBe("BOOKED");
  });
});
