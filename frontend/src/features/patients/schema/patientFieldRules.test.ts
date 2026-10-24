import { afterEach, describe, expect, it, vi } from "vitest";
import {
  PATIENT_FIELD_HINTS,
  formatDateOfBirthInput,
  isValidAddress,
  isValidBirthDate,
  isValidCitizenId,
  isValidEmail,
  isValidFullName,
  isValidPatientCode,
  isValidPhone,
  normalizeCitizenId,
  normalizeFullName,
  normalizePatientCode,
  normalizePhone,
  parseDateOfBirth,
  toDateOfBirthText,
  toPatientFieldErrors,
} from "./patientFieldRules";

afterEach(() => {
  vi.useRealTimers();
});

describe("số điện thoại", () => {
  it("bỏ khoảng trắng, dấu chấm, gạch rồi kiểm 10 chữ số bắt đầu bằng 0", () => {
    expect(normalizePhone("0912 345.678")).toBe("0912345678");
    expect(isValidPhone("0912345678")).toBe(true);
    expect(isValidPhone("912345678")).toBe(false);
    expect(isValidPhone("09123456789")).toBe(false);
    expect(isValidPhone("09123abcde")).toBe(false);
  });
});

describe("mã bệnh nhân", () => {
  it("viết hoa và tự thêm gạch sau BN", () => {
    expect(normalizePatientCode(" bn000123 ")).toBe("BN-000123");
    expect(normalizePatientCode("BN-000123")).toBe("BN-000123");
    expect(isValidPatientCode("BN-000123")).toBe(true);
    expect(isValidPatientCode("BN-12")).toBe(false);
    expect(isValidPatientCode("XX-000123")).toBe(false);
  });
});

describe("ngày sinh gõ liền", () => {
  it("chèn / sau ngày và tháng, bỏ chữ không phải số, cắt tối đa 8 chữ số", () => {
    expect(formatDateOfBirthInput("1")).toBe("1");
    expect(formatDateOfBirthInput("12")).toBe("12");
    expect(formatDateOfBirthInput("120")).toBe("12/0");
    expect(formatDateOfBirthInput("12031958")).toBe("12/03/1958");
    expect(formatDateOfBirthInput("12/03/19589")).toBe("12/03/1958");
    expect(formatDateOfBirthInput("12/3")).toBe("12/3");
  });

  it("đọc dd/mm/yyyy thành YYYY-MM-DD và loại ngày không có thật", () => {
    expect(parseDateOfBirth("12/03/1958")).toBe("1958-03-12");
    expect(parseDateOfBirth("29/02/2000")).toBe("2000-02-29");
    expect(parseDateOfBirth("29/02/2001")).toBeNull();
    expect(parseDateOfBirth("31/04/1990")).toBeNull();
    expect(parseDateOfBirth("12-03-1958")).toBeNull();
    expect(parseDateOfBirth("1958-03-12")).toBeNull();
    expect(parseDateOfBirth("01/01/1899")).toBeNull();
    expect(parseDateOfBirth("")).toBeNull();
  });

  it("không chấp nhận ngày ở tương lai (mốc hôm nay theo giờ Việt Nam)", () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-10-24T12:00:00+07:00"));
    expect(parseDateOfBirth("24/10/2026")).toBe("2026-10-24");
    expect(parseDateOfBirth("25/10/2026")).toBeNull();
    expect(isValidBirthDate("2999-01-01")).toBe(false);
  });

  it("đổi ISO về dd/mm/yyyy để điền lại ô", () => {
    expect(toDateOfBirthText("1958-03-12")).toBe("12/03/1958");
  });
});

describe("họ tên, địa chỉ, CCCD, email", () => {
  it("họ tên gọn khoảng trắng, cần ít nhất hai chữ có chữ cái", () => {
    expect(normalizeFullName("  Nguyễn   Văn An ")).toBe("Nguyễn Văn An");
    expect(isValidFullName("Nguyễn Văn An")).toBe(true);
    expect(isValidFullName("Trần Gia Bảo")).toBe(true);
    expect(isValidFullName("An")).toBe(false);
    expect(isValidFullName("   ")).toBe(false);
    expect(isValidFullName("Nguyễn Văn 2")).toBe(false);
    expect(isValidFullName("A .")).toBe(false);
    expect(isValidFullName(`${"Nguyễn ".repeat(20)}An`)).toBe(false);
  });

  it("địa chỉ không rỗng", () => {
    expect(isValidAddress("12 Nguyễn Trãi")).toBe(true);
    expect(isValidAddress("   ")).toBe(false);
  });

  it("CCCD không bắt buộc; có thì 8 đến 12 chữ hoặc số", () => {
    expect(normalizeCitizenId(" p1234567 ")).toBe("P1234567");
    expect(isValidCitizenId("")).toBe(true);
    expect(isValidCitizenId("001058012345")).toBe(true);
    expect(isValidCitizenId("P1234567")).toBe(true);
    expect(isValidCitizenId("AB123")).toBe(false);
  });

  it("email không bắt buộc; có thì đúng dạng địa chỉ", () => {
    expect(isValidEmail("")).toBe(true);
    expect(isValidEmail("ten@example.com")).toBe(true);
    expect(isValidEmail("abc")).toBe(false);
    expect(isValidEmail("a@b")).toBe(false);
  });
});

describe("toPatientFieldErrors", () => {
  it("chỉ giữ trường có trên form, dùng câu gợi ý của trường đó", () => {
    expect(
      toPatientFieldErrors([
        { field: "phone", code: "MSG-17" },
        { field: "toString", code: "MSG-17" },
        { field: "unknownField", code: "MSG-17" },
      ]),
    ).toEqual({ phone: PATIENT_FIELD_HINTS.phone });
  });
});
