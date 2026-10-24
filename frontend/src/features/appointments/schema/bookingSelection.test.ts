import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import type { OpenTimesResponse, OpenTime } from "../types/booking";
import {
  EMPTY_BOOKING_SELECTION,
  allDaysClosed,
  bookingSelectionReducer,
  bookingStepStates,
  groupTimesByPeriod,
  upcomingDays,
  type BookingSelection,
} from "./bookingSelection";
import { bookingSubmitOutcome } from "./bookingSubmitOutcome";
import { dateOfStart, hhmmOfStart, isBeforeServerNow, longStartLabel, vnIsoOf } from "./bookingTime";

const HA = "doc-ha";
const MINH = "doc-minh";
const picked: BookingSelection = {
  patientId: "pat-an",
  serviceId: "svc-kham-tq",
  date: "2026-10-27",
  startAt: "2026-10-27T10:00:00+07:00",
  doctorId: HA,
};

describe("bookingSelectionReducer", () => {
  it("đổi dịch vụ xóa ngày, giờ và bác sĩ", () => {
    const next = bookingSelectionReducer(picked, { type: "selectService", serviceId: "svc-kham-cs" });
    expect(next).toEqual({ ...picked, serviceId: "svc-kham-cs", date: null, startAt: null, doctorId: null });
  });

  it("đổi ngày xóa giờ và bác sĩ, giữ dịch vụ", () => {
    const next = bookingSelectionReducer(picked, { type: "selectDate", date: "2026-10-28" });
    expect(next).toEqual({ ...picked, date: "2026-10-28", startAt: null, doctorId: null });
  });

  it("đổi giờ giữ bác sĩ nếu bác sĩ đó còn ở giờ mới, không thì về phòng khám sắp xếp", () => {
    const kept = bookingSelectionReducer(picked, {
      type: "selectTime",
      startAt: "2026-10-27T11:00:00+07:00",
      doctorIdsAtTime: [HA, MINH],
    });
    expect(kept.doctorId).toBe(HA);

    const dropped = bookingSelectionReducer(picked, {
      type: "selectTime",
      startAt: "2026-10-27T11:00:00+07:00",
      doctorIdsAtTime: [MINH],
    });
    expect(dropped.doctorId).toBeNull();
  });

  it("mở lại khối giờ bỏ giờ nhưng giữ bác sĩ để dùng cho giờ mới", () => {
    const next = bookingSelectionReducer(picked, { type: "reopen", step: "time" });
    expect(next).toEqual({ ...picked, startAt: null });
  });

  it("mở lại khối dịch vụ, ngày hoặc người khám xóa mọi lựa chọn từ khối đó trở đi", () => {
    expect(bookingSelectionReducer(picked, { type: "reopen", step: "service" })).toEqual({
      ...EMPTY_BOOKING_SELECTION,
      patientId: "pat-an",
    });
    expect(bookingSelectionReducer(picked, { type: "reopen", step: "date" })).toEqual({
      ...picked,
      date: null,
      startAt: null,
      doctorId: null,
    });
    expect(bookingSelectionReducer(picked, { type: "reopen", step: "patient" })).toEqual(
      EMPTY_BOOKING_SELECTION,
    );
  });

  it("chọn bác sĩ và quay về phòng khám sắp xếp", () => {
    expect(bookingSelectionReducer(picked, { type: "selectDoctor", doctorId: null }).doctorId).toBeNull();
  });
});

describe("bookingStepStates", () => {
  it("khu bệnh nhân bắt đầu từ khối người khám; khối sau ẩn cho tới khi khối trước đã chọn", () => {
    const start = bookingStepStates(EMPTY_BOOKING_SELECTION, true);
    expect(start).toMatchObject({ patient: "open", service: "hidden", date: "hidden", time: "hidden", doctor: "hidden", summary: false });

    const withPatient = bookingStepStates({ ...EMPTY_BOOKING_SELECTION, patientId: "pat-an" }, true);
    expect(withPatient).toMatchObject({ patient: "done", service: "open", date: "hidden" });
  });

  it("khu Lễ tân không có khối người khám; bác sĩ hiện từ khi có giờ; tóm tắt hiện từ khi có dịch vụ", () => {
    expect(bookingStepStates(EMPTY_BOOKING_SELECTION, false)).toMatchObject({ patient: "hidden", service: "open", summary: false });
    expect(bookingStepStates({ ...EMPTY_BOOKING_SELECTION, serviceId: "svc-kham-tq" }, false)).toMatchObject({ date: "open", summary: true });
    expect(bookingStepStates(picked, false)).toMatchObject({ date: "done", time: "done", doctor: "open" });
  });
});

describe("groupTimesByPeriod", () => {
  it("chia buổi sáng (trước 12:00) và buổi chiều theo giờ VN", () => {
    const times: OpenTime[] = [
      { startAt: "2026-10-27T11:45:00+07:00", doctors: [] },
      { startAt: "2026-10-27T12:00:00+07:00", doctors: [] },
      { startAt: "2026-10-27T13:30:00+07:00", doctors: [] },
    ];
    const { morning, afternoon } = groupTimesByPeriod(times);
    expect(morning.map((time) => time.startAt)).toEqual(["2026-10-27T11:45:00+07:00"]);
    expect(afternoon.map((time) => time.startAt)).toEqual([
      "2026-10-27T12:00:00+07:00",
      "2026-10-27T13:30:00+07:00",
    ]);
  });
});

describe("upcomingDays và allDaysClosed", () => {
  const response: OpenTimesResponse = {
    serviceId: "svc-kham-tq",
    from: "2026-10-23",
    to: "2026-10-24",
    slotMinutes: 15,
    serverNow: "2026-10-23T08:05:00+07:00",
    days: [
      {
        date: "2026-10-23",
        times: [
          { startAt: "2026-10-23T08:00:00+07:00", doctors: [] },
          { startAt: "2026-10-23T08:15:00+07:00", doctors: [] },
        ],
      },
      { date: "2026-10-24", times: [{ startAt: "2026-10-24T08:00:00+07:00", doctors: [] }] },
    ],
  };

  it("bỏ giờ đã qua theo serverNow và giữ đủ ngày", () => {
    const days = upcomingDays(response);
    expect(days).toHaveLength(2);
    expect(days[0].times.map((time) => time.startAt)).toEqual(["2026-10-23T08:15:00+07:00"]);
  });

  it("cả dải không còn giờ sau khi lọc thì báo đóng hết", () => {
    expect(allDaysClosed(upcomingDays(response))).toBe(false);
    const closed = upcomingDays({ ...response, serverNow: "2026-10-25T00:00:00+07:00" });
    expect(allDaysClosed(closed)).toBe(true);
  });
});

describe("bookingSubmitOutcome", () => {
  it("MSG-30 bỏ giờ, MSG-24 về phòng khám sắp xếp, còn lại giữ form", () => {
    expect(bookingSubmitOutcome(new ApiError(409, { code: "MSG-30" }))).toBe("slotTaken");
    expect(bookingSubmitOutcome(new ApiError(409, { code: "MSG-24" }))).toBe("doctorUnavailable");
    expect(bookingSubmitOutcome(new ApiError(403, { code: "MSG-29" }))).toBe("keepForm");
    expect(bookingSubmitOutcome(new ApiError(400, { code: "MSG-31" }))).toBe("keepForm");
    expect(bookingSubmitOutcome(new Error("mạng"))).toBe("keepForm");
  });
});

describe("bookingTime", () => {
  it("đổi thời điểm sang ISO giờ VN bất kể múi giờ máy", () => {
    expect(vnIsoOf(new Date("2026-10-23T01:05:09Z"))).toBe("2026-10-23T08:05:09+07:00");
  });

  it("tách ngày và giờ từ ISO giờ VN; so với serverNow", () => {
    expect(dateOfStart("2026-10-23T08:30:00+07:00")).toBe("2026-10-23");
    expect(hhmmOfStart("2026-10-23T08:30:00+07:00")).toBe("08:30");
    expect(isBeforeServerNow("2026-10-23T08:00:00+07:00", "2026-10-23T08:05:00+07:00")).toBe(true);
    expect(isBeforeServerNow("2026-10-23T08:15:00+07:00", "2026-10-23T08:05:00+07:00")).toBe(false);
  });

  it("nhãn dòng tóm tắt: thứ, ngày và giờ bằng tiếng Việt", () => {
    expect(longStartLabel("2026-10-23T08:30:00+07:00")).toBe("Thứ sáu 23/10/2026 lúc 08:30");
  });
});
