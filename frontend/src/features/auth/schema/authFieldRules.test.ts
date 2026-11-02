import { describe, expect, it } from "vitest";
import type { PatientRegistrationInput } from "../types/auth";
import {
  validateEmail,
  validatePassword,
  validateRegistration,
  validateVerificationCode,
} from "./authFieldRules";

const validInput: PatientRegistrationInput = {
  fullName: "Nguyễn Văn An",
  email: "an@example.com",
  password: "matkhau123",
  confirmPassword: "matkhau123",
  consent: true,
};

describe("authFieldRules", () => {
  it("chấp nhận thông tin đăng ký hợp lệ (họ tên, email, mật khẩu, đồng ý)", () => {
    expect(validateRegistration(validInput)).toEqual({});
  });

  it("báo lỗi từng trường thiếu hoặc sai", () => {
    const errors = validateRegistration({
      ...validInput,
      fullName: " ",
      email: "sai-email",
      confirmPassword: "khac123",
      consent: false,
    });
    expect(Object.keys(errors).sort()).toEqual(
      ["confirmPassword", "consent", "email", "fullName"].sort(),
    );
  });

  it("không hỏi ngày sinh, giới tính hay SĐT: các khóa đó không bao giờ có lỗi", () => {
    // Dữ liệu cũ có thêm khóa thì bị bỏ qua, không làm đăng ký thất bại.
    const withLegacyFields = {
      ...validInput,
      dateOfBirth: "",
      gender: "",
      phone: "",
    } as PatientRegistrationInput;
    expect(validateRegistration(withLegacyFields)).toEqual({});
  });

  it("kiểm tra email, mật khẩu và mã xác minh", () => {
    expect(validateEmail("sai-email")).toBeDefined();
    expect(validateEmail("a@b.vn")).toBeUndefined();
    expect(validatePassword("chuacoso")).toBeDefined();
    expect(validatePassword("12345678")).toBeDefined();
    expect(validatePassword("đúngrồi12")).toBeUndefined();
    expect(validateVerificationCode("12345")).toBeDefined();
    expect(validateVerificationCode("123456")).toBeUndefined();
  });
});
