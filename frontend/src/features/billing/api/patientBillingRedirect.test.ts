import { describe, expect, it } from "vitest";
import { isSafeRedirectUrl } from "./patientBillingRedirect";

describe("isSafeRedirectUrl", () => {
  it("chấp nhận đường dẫn nội bộ và https tới máy chủ VNPay đã cấu hình", () => {
    expect(isSafeRedirectUrl("/portal/payments/result?paymentId=pay-mock-1")).toBe(true);
    expect(isSafeRedirectUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?x=1")).toBe(true);
  });

  it("từ chối máy chủ lạ, giao thức không an toàn và đường dẫn lệch gốc", () => {
    expect(isSafeRedirectUrl("//evil.example/pay")).toBe(false);
    expect(isSafeRedirectUrl("/\\evil.example/pay")).toBe(false);
    expect(isSafeRedirectUrl("https://evil.example/pay")).toBe(false);
    expect(isSafeRedirectUrl("http://sandbox.vnpayment.vn/pay")).toBe(false);
    expect(isSafeRedirectUrl("javascript:alert(1)")).toBe(false);
    expect(isSafeRedirectUrl("không phải url")).toBe(false);
  });
});
