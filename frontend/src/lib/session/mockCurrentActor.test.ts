import { describe, expect, it } from "vitest";
import { getMockCurrentActor } from "./mockCurrentActor";

describe("getMockCurrentActor", () => {
  it("khu bệnh nhân trả tài khoản An, vai trò PATIENT, có email", () => {
    expect(getMockCurrentActor("patient")).toEqual({
      accountId: "acc-an",
      role: "PATIENT",
      displayName: "Nguyễn Văn An",
      email: "an@example.com",
    });
  });

  it("khu lễ tân trả Trần Thu Ngân, không có email", () => {
    const actor = getMockCurrentActor("staff");
    expect(actor.role).toBe("RECEPTIONIST");
    expect(actor.displayName).toBe("Trần Thu Ngân");
    expect(actor.email).toBeUndefined();
  });

  it("trả bản sao: sửa kết quả không đổi lần gọi sau", () => {
    const first = getMockCurrentActor("patient");
    first.displayName = "Đã sửa";
    expect(getMockCurrentActor("patient").displayName).toBe("Nguyễn Văn An");
  });
});
