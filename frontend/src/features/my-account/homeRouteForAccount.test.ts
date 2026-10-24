import { describe, expect, it } from "vitest";
import { MOCK_MY_ACCOUNT, MOCK_PATIENT_ACCOUNT } from "./api/myAccountMockData";
import { homeRouteForAccount } from "./homeRouteForAccount";

describe("homeRouteForAccount", () => {
  it("bệnh nhân vào cổng bệnh nhân", () => {
    expect(homeRouteForAccount(MOCK_PATIENT_ACCOUNT)).toBe("/portal");
  });

  it("quản trị viên và chủ phòng khám vào khu quản trị", () => {
    expect(homeRouteForAccount(MOCK_MY_ACCOUNT)).toBe("/admin");
    expect(
      homeRouteForAccount({ ...MOCK_MY_ACCOUNT, role: "CLINIC_OWNER" }),
    ).toBe("/admin");
  });

  it.each(["RECEPTIONIST", "ORTHOPEDIC_DOCTOR", "IMAGING_TECHNICIAN", "LAB_TECHNICIAN"] as const)(
    "nhân viên %s vào khu lễ tân",
    (role) => {
      expect(homeRouteForAccount({ ...MOCK_MY_ACCOUNT, role })).toBe("/staff");
    },
  );
});
