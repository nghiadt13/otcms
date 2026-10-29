import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  groupSlotsByDay,
  toBookableService,
  toBookingApiError,
} from "./bookingLiveMapping";

describe("toBookableService", () => {
  it("lấy giá từ trường price của backend", () => {
    expect(
      toBookableService({
        id: "s1",
        name: "Khám",
        description: null,
        price: 1800000,
      }),
    ).toEqual({ id: "s1", name: "Khám", price: 1800000, bookingDetails: null });
  });

  it("giữ mô tả làm hướng dẫn đặt lịch", () => {
    expect(
      toBookableService({
        id: "s2",
        name: "Khám cột sống",
        description: "Mang theo phim cũ.",
        price: 250000.5,
      }),
    ).toEqual({
      id: "s2",
      name: "Khám cột sống",
      price: 250000.5,
      bookingDetails: "Mang theo phim cũ.",
    });
  });
});

describe("groupSlotsByDay", () => {
  const slot = (doctorId: string, name: string, startTime: string) => ({
    doctorId,
    doctorDisplayName: name,
    startTime,
  });

  it("gom theo ngày giờ VN, giờ, rồi bác sĩ; ngày trống vẫn có mặt", () => {
    // 01:00Z = 08:00 +07:00; 17:30Z = 00:30 ngày hôm sau theo giờ VN.
    const days = groupSlotsByDay(
      [
        slot("d2", "BS. Trần", "2026-10-29T01:00:00Z"),
        slot("d1", "BS. Lê", "2026-10-29T01:00:00Z"),
        slot("d1", "BS. Lê", "2026-10-29T01:15:00Z"),
        slot("d1", "BS. Lê", "2026-10-29T17:30:00Z"),
      ],
      "2026-10-29",
      "2026-10-31",
    );
    expect(days.map((day) => day.date)).toEqual([
      "2026-10-29",
      "2026-10-30",
      "2026-10-31",
    ]);
    expect(days[0].times.map((time) => time.startAt)).toEqual([
      "2026-10-29T08:00:00+07:00",
      "2026-10-29T08:15:00+07:00",
    ]);
    expect(days[0].times[0].doctors.map((doctor) => doctor.id)).toEqual([
      "d1",
      "d2",
    ]);
    expect(days[1].times.map((time) => time.startAt)).toEqual([
      "2026-10-30T00:30:00+07:00",
    ]);
    expect(days[2].times).toEqual([]);
  });
});

describe("toBookingApiError", () => {
  it("đổi mã lỗi thô của backend sang mã MSG", () => {
    const mapped = toBookingApiError(
      new ApiError(409, { errorCode: "SLOT_UNAVAILABLE" }),
    );
    expect(mapped).toBeInstanceOf(ApiError);
    expect((mapped as ApiError).code).toBe("MSG-30");
  });

  it("giữ nguyên lỗi không có trong bảng", () => {
    const error = new ApiError(500, { errorCode: "BOOM" });
    expect(toBookingApiError(error)).toBe(error);
  });
});
