import { describe, expect, it } from "vitest";
import {
  PHOTO_MAX_BYTES,
  isValidWorkContact,
  validatePhotoFile,
  validateStaffProfileInput,
} from "./staffProfileFieldRules";

const doctorGroup = { qualifications: "", biography: "", consultationInfo: "" };

describe("validateStaffProfileInput", () => {
  it("bắt buộc họ tên hiển thị và liên hệ công việc", () => {
    expect(
      validateStaffProfileInput({ displayName: "  ", workContact: "" }, "RECEPTIONIST"),
    ).toEqual({ displayName: "MSG-12", workContact: "MSG-12" });
  });

  it("hợp lệ khi có họ tên và số điện thoại hoặc email", () => {
    expect(
      validateStaffProfileInput({ displayName: "Lê Tân", workContact: "0901 234 567" }, "RECEPTIONIST"),
    ).toEqual({});
    expect(isValidWorkContact("tan@example.com")).toBe(true);
    expect(isValidWorkContact("không phải liên hệ")).toBe(false);
  });

  it("nhóm bác sĩ không bắt buộc nhưng giới hạn độ dài", () => {
    const base = { displayName: "BS. A", workContact: "0901 234 567" };
    expect(
      validateStaffProfileInput({ ...base, doctor: doctorGroup }, "ORTHOPEDIC_DOCTOR"),
    ).toEqual({});
    expect(
      validateStaffProfileInput(
        { ...base, doctor: { ...doctorGroup, biography: "a".repeat(2001) } },
        "ORTHOPEDIC_DOCTOR",
      ),
    ).toEqual({ "doctor.biography": "MSG-12" });
  });

  it("bỏ qua nhóm bác sĩ khi vai trò khác", () => {
    expect(
      validateStaffProfileInput(
        { displayName: "KTV", workContact: "0901 234 567", doctor: { ...doctorGroup, biography: "a".repeat(5000) } },
        "LAB_TECHNICIAN",
      ),
    ).toEqual({});
  });
});

describe("validatePhotoFile", () => {
  const file = (type: string, size: number) => {
    const f = new File(["x"], "anh", { type });
    Object.defineProperty(f, "size", { value: size });
    return f;
  };

  it("nhận jpeg, png, webp trong giới hạn dung lượng", () => {
    expect(validatePhotoFile(file("image/png", 1000))).toBeNull();
    expect(validatePhotoFile(file("image/webp", PHOTO_MAX_BYTES))).toBeNull();
  });

  it("từ chối định dạng lạ hoặc quá dung lượng", () => {
    expect(validatePhotoFile(file("image/gif", 1000))).toBe("MSG-12");
    expect(validatePhotoFile(file("image/jpeg", PHOTO_MAX_BYTES + 1))).toBe("MSG-12");
  });
});
