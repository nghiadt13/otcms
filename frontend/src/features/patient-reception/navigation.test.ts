import { afterEach, describe, expect, it, vi } from "vitest";
import {
  isReceptionSectionAvailable,
  receptionNavigation,
  type ReceptionSection,
} from "./navigation";

// Các màn đã nối BE thật (tiếp nhận, lịch hẹn, hàng chờ, chỉ định CĐHA) phải mở được ở production
// không cần cờ demo; các màn còn dữ liệu giả thì bị khoá.
const LIVE_SECTIONS: ReceptionSection[] = [
  "",
  "appointments",
  "queues",
  "orders",
];
const ALL_SECTIONS = receptionNavigation.map((item) => item.section);
const MOCK_SECTIONS = ALL_SECTIONS.filter(
  (section) => !LIVE_SECTIONS.includes(section),
);

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("isReceptionSectionAvailable", () => {
  it("ngoài production mọi màn đều mở", () => {
    vi.stubEnv("NODE_ENV", "development");
    for (const section of ALL_SECTIONS)
      expect(isReceptionSectionAvailable(section)).toBe(true);
  });

  it("production không có cờ demo: chỉ các màn đã nối BE mở", () => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("NEXT_PUBLIC_RECEPTION_DEMO", "");
    for (const section of LIVE_SECTIONS)
      expect(isReceptionSectionAvailable(section)).toBe(true);
    for (const section of MOCK_SECTIONS)
      expect(isReceptionSectionAvailable(section)).toBe(false);
  });

  it("production có cờ demo: mở cả màn còn dữ liệu giả", () => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("NEXT_PUBLIC_RECEPTION_DEMO", "true");
    for (const section of ALL_SECTIONS)
      expect(isReceptionSectionAvailable(section)).toBe(true);
  });
});
