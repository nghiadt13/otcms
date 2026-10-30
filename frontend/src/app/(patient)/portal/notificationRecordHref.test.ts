import { describe, expect, it } from "vitest";
import { resolvePatientRecordHref } from "./notificationRecordHref";

describe("resolvePatientRecordHref", () => {
  it("mỗi loại bản ghi tới đúng màn của khu bệnh nhân", () => {
    expect(
      resolvePatientRecordHref({
        kind: "VISIT_RESULT",
        id: "vis-an-done",
        available: true,
      }),
    ).toBe("/portal/results/vis-an-done");
    expect(
      resolvePatientRecordHref({
        kind: "APPOINTMENT",
        id: "apt-2101",
        available: true,
      }),
    ).toBe("/portal/appointments/apt-2101");
    expect(
      resolvePatientRecordHref({
        kind: "VISIT",
        id: "vis-an-today",
        available: true,
      }),
    ).toBe("/portal/visits/vis-an-today");
    expect(
      resolvePatientRecordHref({
        kind: "PAYMENT",
        id: "pm-1029",
        available: true,
      }),
    ).toBe("/portal/payments/history/pm-1029");
  });

  it("mã bản ghi được mã hóa để không vỡ URL", () => {
    expect(
      resolvePatientRecordHref({ kind: "VISIT", id: "a/b?c", available: true }),
    ).toBe("/portal/visits/a%2Fb%3Fc");
  });

  it("bản ghi không còn khả dụng: không có đường dẫn", () => {
    expect(
      resolvePatientRecordHref({
        kind: "PAYMENT",
        id: "pm-1009",
        available: false,
      }),
    ).toBeNull();
  });
});
