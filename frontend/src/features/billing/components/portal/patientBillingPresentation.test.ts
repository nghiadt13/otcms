import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { historyHref, isMissingRecordError, paymentsHref, readDateParam, readStringParam } from "./patientBillingPresentation";
describe("patient billing presentation", () => {
  it("đọc tham số chuỗi và ngày hợp lệ", () => {
    expect(readStringParam(["x"])).toBeUndefined(); expect(readStringParam("x")).toBe("x");
    expect(readDateParam("2026-11-17")).toBe("2026-11-17"); expect(readDateParam("17/11/2026")).toBeUndefined();
  });
  it("tạo đường dẫn có bộ lọc", () => {
    expect(paymentsHref("p1")).toContain("profileId=p1"); expect(historyHref({ from: "2026-01-01" })).toContain("from=2026-01-01");
  });
  it("nhận diện lỗi không tìm thấy", () => {
    expect(isMissingRecordError(new ApiError(404))).toBe(true); expect(isMissingRecordError(new ApiError(500))).toBe(false);
  });
});
