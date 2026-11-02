import { describe, expect, it } from "vitest";
import { formatDateTime } from "@/lib/format/formatDate";
import { notificationTimeLabel } from "./notificationTimeLabel";

const NOW = new Date("2026-10-24T12:00:00+07:00");
const ago = (ms: number) => new Date(NOW.getTime() - ms).toISOString();
const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

describe("notificationTimeLabel", () => {
  it("trong vòng vài phút, vài giờ, vài ngày: tương đối kèm tuyệt đối", () => {
    expect(notificationTimeLabel(ago(30_000), NOW)).toMatch(/^Vừa xong · /);
    expect(notificationTimeLabel(ago(5 * MINUTE), NOW)).toMatch(
      /^5 phút trước · /,
    );
    expect(notificationTimeLabel(ago(3 * HOUR), NOW)).toMatch(
      /^3 giờ trước · /,
    );
    expect(notificationTimeLabel(ago(2 * DAY), NOW)).toMatch(
      /^2 ngày trước · /,
    );
  });

  it("phần tuyệt đối luôn là ngày giờ theo múi Việt Nam", () => {
    const iso = ago(3 * HOUR);
    expect(notificationTimeLabel(iso, NOW)).toBe(
      `3 giờ trước · ${formatDateTime(iso)}`,
    );
  });

  it("quá 7 ngày hoặc ở tương lai: chỉ ghi ngày giờ tuyệt đối", () => {
    const old = ago(8 * DAY);
    expect(notificationTimeLabel(old, NOW)).toBe(formatDateTime(old));
    const future = new Date(NOW.getTime() + HOUR).toISOString();
    expect(notificationTimeLabel(future, NOW)).toBe(formatDateTime(future));
  });
});
