import { describe, expect, it } from "vitest";
import type { PatientRegistrationInput } from "../types/auth";
import {
  validateEmail,
  validatePassword,
  validateRegistration,
  validateStaffEmail,
  validateVerificationCode,
} from "./authFieldRules";

const validInput: PatientRegistrationInput = {
  fullName: "Nguyễn Văn An",
  dateOfBirth: "20/05/1990",
  gender: "male",
  phone: "0901 234 567",
  email: "an@example.com",
  password: "matkhau123",
  confirmPassword: "matkhau123",
  consent: true,
};

describe("authFieldRules", () => {
  it("chấp nhận thông tin đăng ký hợp lệ", () => {
    expect(validateRegistration(validInput)).toEqual({});
  });

  it("báo lỗi từng trường thiếu hoặc sai", () => {
    const errors = validateRegistration(
      {
        ...validInput,
        fullName: " ",
        dateOfBirth: "01/01/2999",
        gender: "",
        phone: "12345",
        confirmPassword: "khac123",
        consent: false,
      },
      new Date("2026-10-17"),
    );
    expect(Object.keys(errors).sort()).toEqual(
      [
        "confirmPassword",
        "consent",
        "dateOfBirth",
        "fullName",
        "gender",
        "phone",
      ].sort(),
    );
  });

  it("kiểm tra email, mật khẩu và mã xác minh", () => {
    expect(validateEmail("sai-email")).toBeDefined();
    expect(validateEmail("a@b.vn")).toBeUndefined();
    expect(validateEmail("demo.staff@localhost")).toBeDefined();
    expect(validateStaffEmail("demo.staff@localhost")).toBeUndefined();
    expect(validateStaffEmail("not-an-email")).toBeDefined();
    expect(validatePassword("chuacoso")).toBeDefined();
    expect(validatePassword("12345678")).toBeDefined();
    expect(validatePassword("đúngrồi12")).toBeUndefined();
    expect(validateVerificationCode("12345")).toBeDefined();
    expect(validateVerificationCode("123456")).toBeUndefined();
  });
});
