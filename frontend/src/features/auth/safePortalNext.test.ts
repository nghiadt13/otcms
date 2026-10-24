import { describe, expect, it } from "vitest";
import { safePortalNext } from "./safePortalNext";

describe("safePortalNext: đích `next` an toàn", () => {
  it.each([
    ["/portal", "/portal"],
    ["/portal/appointments/new", "/portal/appointments/new"],
    [
      "/portal/appointments/new?visitId=12#gio",
      "/portal/appointments/new?visitId=12#gio",
    ],
  ])("nhận đường dẫn trong khu bệnh nhân: %s", (input, expected) => {
    expect(safePortalNext(input)).toBe(expected);
  });

  it.each([
    ["URL ngoài bằng //", "//evil.com"],
    ["gạch chéo ngược sau /", "/\\evil.com"],
    ["URL tuyệt đối https", "https://evil.com/portal"],
    ["URL tuyệt đối http", "http://evil.com"],
    ["giao thức javascript", "javascript:alert(1)"],
    ["đường dẫn không có dấu / đầu", "portal/appointments/new"],
    ["khu quản trị", "/admin"],
    ["khu lễ tân", "/staff"],
    ["tiền tố giống /portal", "/portalx"],
    ["lùi thư mục ra khỏi /portal", "/portal/../admin"],
    ["lùi thư mục mã hóa", "/portal/%2e%2e/admin"],
    ["tab chèn giữa đường dẫn", "/portal\t//evil.com"],
    ["xuống dòng cuối", "/portal\n"],
    ["khoảng trắng đầu", " /portal"],
    ["chuỗi rỗng", ""],
  ])("bỏ về /portal khi %s: %j", (_label, input) => {
    expect(safePortalNext(input)).toBe("/portal");
  });

  it("thiếu giá trị thì về /portal", () => {
    expect(safePortalNext(null)).toBe("/portal");
    expect(safePortalNext(undefined)).toBe("/portal");
  });

  it("chuỗi quá dài thì về /portal", () => {
    expect(safePortalNext(`/portal/${"a".repeat(2100)}`)).toBe("/portal");
  });
});
