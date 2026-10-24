import { describe, expect, it } from "vitest";
import {
  findMockPatient,
  registerMockPatient,
  type MockPatientIdentity,
} from "./sharedPatientDirectoryMock";

const lucia: MockPatientIdentity = {
  id: "pat-new-1",
  patientCode: "BN-000203",
  fullName: "Lê Thị Cúc",
  dateOfBirth: "1990-07-01",
  sex: "FEMALE",
  phone: "0987654321",
};

// Mỗi test dùng id riêng nên kho dùng chung trong module không làm lẫn kết quả giữa các test.
describe("kho bệnh nhân giả dùng chung", () => {
  it("tra lại đúng hồ sơ đã đăng ký theo id", () => {
    registerMockPatient(lucia);
    expect(findMockPatient("pat-new-1")).toEqual(lucia);
  });

  it("id chưa đăng ký trả undefined", () => {
    expect(findMockPatient("pat-new-404")).toBeUndefined();
  });

  it("đăng ký lại cùng id thì ghi đè", () => {
    registerMockPatient({ ...lucia, id: "pat-new-2", phone: "0900000000" });
    registerMockPatient({ ...lucia, id: "pat-new-2", phone: "0911111111" });
    expect(findMockPatient("pat-new-2")?.phone).toBe("0911111111");
  });

  it("sửa bản ghi trả về không đổi kho; sửa đối tượng truyền vào không đổi kho", () => {
    const input = { ...lucia, id: "pat-new-3" };
    registerMockPatient(input);
    input.fullName = "Tên bị sửa";
    const found = findMockPatient("pat-new-3");
    expect(found?.fullName).toBe("Lê Thị Cúc");
    if (found) found.fullName = "Sửa ngoài kho";
    expect(findMockPatient("pat-new-3")?.fullName).toBe("Lê Thị Cúc");
  });
});
