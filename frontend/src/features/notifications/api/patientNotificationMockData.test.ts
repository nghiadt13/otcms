import { describe, expect, it } from "vitest";
import { MOCK_PATIENT_NOTIFICATIONS } from "./patientNotificationMockData";

describe("MOCK_PATIENT_NOTIFICATIONS", () => {
  it("8 thông báo đủ bốn loại bản ghi, 3 chưa đọc, 1 bản ghi không còn khả dụng", () => {
    expect(MOCK_PATIENT_NOTIFICATIONS).toHaveLength(8);
    const kinds = new Set(
      MOCK_PATIENT_NOTIFICATIONS.map((item) => item.relatedRecord?.kind),
    );
    expect([...kinds].sort()).toEqual([
      "APPOINTMENT",
      "PAYMENT",
      "VISIT",
      "VISIT_RESULT",
    ]);
    expect(
      MOCK_PATIENT_NOTIFICATIONS.filter((item) => !item.read),
    ).toHaveLength(3);
    expect(
      MOCK_PATIENT_NOTIFICATIONS.filter(
        (item) => item.relatedRecord?.available === false,
      ),
    ).toHaveLength(1);
  });

  it("mới nhất trước và không còn tham số {…} chưa điền", () => {
    const times = MOCK_PATIENT_NOTIFICATIONS.map((item) =>
      Date.parse(item.createdAt),
    );
    expect(times).toEqual([...times].sort((a, b) => b - a));
    for (const item of MOCK_PATIENT_NOTIFICATIONS) {
      expect(item.content).not.toMatch(/[{}]/);
      expect(item.title).not.toMatch(/[{}]/);
    }
  });

  it("chưa đọc có markRead, đã đọc không có hành động nào", () => {
    for (const item of MOCK_PATIENT_NOTIFICATIONS) {
      expect(item.allowedActions).toEqual(item.read ? [] : ["markRead"]);
    }
  });
});
