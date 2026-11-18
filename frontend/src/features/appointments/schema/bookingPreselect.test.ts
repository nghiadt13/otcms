import { describe, expect, it } from "vitest";
import type { OpenDay } from "../types/booking";
import { resolvePreselectedSlot } from "./bookingPreselect";

// Days in the same shape the booking API returns (ISO +07:00 start times).
const days: OpenDay[] = [
  {
    date: "2026-11-19",
    times: [
      {
        startAt: "2026-11-19T08:15:00+07:00",
        doctors: [
          { id: "doc-1", fullName: "BS An" },
          { id: "doc-2", fullName: "BS Binh" },
        ],
      },
    ],
  },
  { date: "2026-11-20", times: [] },
];

const slot = (overrides: Partial<Parameters<typeof resolvePreselectedSlot>[1]>) => ({
  serviceId: "svc-kham-tq",
  doctorId: "doc-1",
  date: "2026-11-19",
  time: "08:15",
  ...overrides,
});

describe("resolvePreselectedSlot", () => {
  it("tìm được ngày, giờ và bác sĩ còn ở giờ đó", () => {
    const outcome = resolvePreselectedSlot(days, slot({}));
    expect(outcome.day?.date).toBe("2026-11-19");
    expect(outcome.time?.startAt).toBe("2026-11-19T08:15:00+07:00");
    expect(outcome.doctorAvailable).toBe(true);
  });

  it("ngày không còn trong dải thì không có ngày và không có giờ", () => {
    const outcome = resolvePreselectedSlot(days, slot({ date: "2026-12-01" }));
    expect(outcome).toEqual({ day: null, time: null, doctorAvailable: false });
  });

  it("giờ đã hết trong ngày thì giữ ngày nhưng không có giờ", () => {
    const outcome = resolvePreselectedSlot(days, slot({ time: "09:00" }));
    expect(outcome.day?.date).toBe("2026-11-19");
    expect(outcome.time).toBeNull();
    expect(outcome.doctorAvailable).toBe(false);
  });

  it("bác sĩ không còn ở giờ đó thì báo không khả dụng, giờ vẫn còn", () => {
    const outcome = resolvePreselectedSlot(days, slot({ doctorId: "doc-9" }));
    expect(outcome.time).not.toBeNull();
    expect(outcome.doctorAvailable).toBe(false);
  });

  it("không có bác sĩ trong link thì không cần bác sĩ còn trống", () => {
    const outcome = resolvePreselectedSlot(days, slot({ doctorId: null }));
    expect(outcome.time).not.toBeNull();
    expect(outcome.doctorAvailable).toBe(true);
  });
});
