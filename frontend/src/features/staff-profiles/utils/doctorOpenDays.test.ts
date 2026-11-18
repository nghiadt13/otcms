import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { openDaysFromToday, vnDateOf, vnTimeOf } from "./doctorOpenDays";

describe("vnDateOf và vnTimeOf", () => {
  it("tính theo giờ VN, kể cả khi UTC vẫn còn ngày trước", () => {
    // 17:30 UTC of 18/11 is 00:30 of 19/11 in Vietnam.
    expect(vnDateOf("2026-11-18T17:30:00Z")).toBe("2026-11-19");
    expect(vnTimeOf("2026-11-18T17:30:00Z")).toBe("00:30");
  });
});

describe("openDaysFromToday", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-11-18T03:00:00+07:00"));
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("trả đủ 7 ngày từ hôm nay; ngày không có giờ vẫn có mặt với danh sách rỗng", () => {
    const days = openDaysFromToday(
      [{ serviceId: "svc-a", startAt: "2026-11-19T02:30:00Z" }],
      7,
    );
    expect(days.map((day) => day.date)).toEqual([
      "2026-11-18",
      "2026-11-19",
      "2026-11-20",
      "2026-11-21",
      "2026-11-22",
      "2026-11-23",
      "2026-11-24",
    ]);
    expect(days[0].times).toEqual([]);
    // 02:30 UTC is 09:30 in Vietnam on 19/11.
    expect(days[1].times).toEqual([
      { serviceId: "svc-a", startAt: "2026-11-19T02:30:00Z" },
    ]);
  });

  it("bỏ qua giờ nằm ngoài 7 ngày", () => {
    const days = openDaysFromToday(
      [{ serviceId: "svc-a", startAt: "2026-12-30T02:30:00Z" }],
      7,
    );
    expect(days.every((day) => day.times.length === 0)).toBe(true);
  });
});
