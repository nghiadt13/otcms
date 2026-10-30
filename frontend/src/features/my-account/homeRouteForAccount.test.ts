import { describe, expect, it } from "vitest";
import { homeRouteForAccount } from "./homeRouteForAccount";

describe("homeRouteForAccount", () => {
  it("bệnh nhân vào cổng bệnh nhân", () => {
    expect(homeRouteForAccount("PATIENT")).toBe("/portal/account");
  });

  it.each(["ADMIN"] as const)("%s vào khu quản trị", (accountType) => {
    expect(homeRouteForAccount(accountType)).toBe("/admin");
  });

  it.each(["RECEPTIONIST", "DOCTOR"] as const)(
    "nhân viên %s vào khu lễ tân",
    (accountType) => {
      expect(homeRouteForAccount(accountType)).toBe("/reception");
    },
  );
});

it("chủ phòng khám vào quản trị tài khoản Admin", () => {
  expect(homeRouteForAccount("OWNER")).toBe("/owner/accounts");
});
