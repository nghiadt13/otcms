import { beforeEach, describe, expect, it } from "vitest";
import {
  clearMockSignedInKind,
  getMockSignedInKind,
  setMockSignedInKind,
} from "./mockSignedInKind";

beforeEach(() => window.localStorage.clear());

describe("mockSignedInKind (phiên giả)", () => {
  it("chưa có phiên giả thì là tài khoản nhân viên/quản trị", () => {
    expect(getMockSignedInKind()).toBe("STAFF");
  });

  it("đăng nhập bệnh nhân ghi PATIENT; đăng xuất (xóa) trả về STAFF", () => {
    setMockSignedInKind("PATIENT");
    expect(getMockSignedInKind()).toBe("PATIENT");
    clearMockSignedInKind();
    expect(getMockSignedInKind()).toBe("STAFF");
  });

  it("giá trị lạ trong bộ nhớ tạm không làm lộ tài khoản bệnh nhân", () => {
    window.localStorage.setItem("otcms.mock.signedInKind", "ADMIN_HACK");
    expect(getMockSignedInKind()).toBe("STAFF");
  });
});
