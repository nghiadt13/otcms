import { describe, expect, it } from "vitest";
import {
  buildLookupQuery,
  buildWalkInRequest,
  findLookupFieldErrors,
  findWalkInFieldErrors,
  type LookupFormInput,
  type WalkInFormValues,
} from "./patientRequestRules";

const phoneForm: LookupFormInput = {
  mode: "phone",
  phone: "0912 345 678",
  dateOfBirth: "21/07/1992",
  patientCode: "",
  fullName: "",
  askFullName: false,
};

const validWalkIn: WalkInFormValues = {
  fullName: "  Lê Thị   Cúc ",
  dateOfBirth: "01/07/1990",
  sex: "FEMALE",
  phone: "0987 654 321",
  address: " 12 Nguyễn Trãi, Thanh Xuân, Hà Nội ",
  citizenId: "",
  email: "",
};

describe("buildLookupQuery", () => {
  it("tìm theo SĐT + ngày sinh: chuẩn hóa và không gửi họ tên khi chưa cần", () => {
    expect(buildLookupQuery(phoneForm)).toEqual({
      query: { phone: "0912345678", dateOfBirth: "1992-07-21" },
    });
  });

  it("khi lần trước trả MULTIPLE thì gửi kèm họ tên đã gọn khoảng trắng", () => {
    expect(
      buildLookupQuery({ ...phoneForm, askFullName: true, fullName: " Trần  Gia Bảo " }),
    ).toEqual({
      query: { phone: "0912345678", dateOfBirth: "1992-07-21", fullName: "Trần Gia Bảo" },
    });
  });

  it("khi cần họ tên mà để trống hoặc chỉ một chữ thì báo lỗi ở ô họ tên", () => {
    const result = buildLookupQuery({ ...phoneForm, askFullName: true, fullName: "An" });
    expect(result).toEqual({ errors: { fullName: expect.any(String) } });
  });

  it("tìm theo mã: tự thêm gạch và viết hoa", () => {
    expect(
      buildLookupQuery({ ...phoneForm, mode: "code", patientCode: " bn000123" }),
    ).toEqual({ query: { patientCode: "BN-000123" } });
  });

  it("sai SĐT hoặc ngày sinh báo lỗi đúng ô", () => {
    expect(buildLookupQuery({ ...phoneForm, phone: "12345", dateOfBirth: "31/02/1990" })).toEqual({
      errors: { phone: expect.any(String), dateOfBirth: expect.any(String) },
    });
  });
});

describe("findLookupFieldErrors", () => {
  it("truy vấn hợp lệ không có lỗi", () => {
    expect(findLookupFieldErrors({ phone: "0912345678", dateOfBirth: "1992-07-21" })).toEqual({});
    expect(findLookupFieldErrors({ patientCode: "BN-000123" })).toEqual({});
  });

  it("mã bệnh nhân sai định dạng báo ở ô mã", () => {
    expect(findLookupFieldErrors({ patientCode: "BN-12" })).toEqual({
      patientCode: expect.any(String),
    });
  });
});

describe("buildWalkInRequest", () => {
  it("đổi sang body của hợp đồng: ngày ISO, SĐT số thường, CCCD và email rỗng bị bỏ", () => {
    expect(buildWalkInRequest(validWalkIn)).toEqual({
      request: {
        fullName: "Lê Thị Cúc",
        dateOfBirth: "1990-07-01",
        sex: "FEMALE",
        phone: "0987654321",
        address: "12 Nguyễn Trãi, Thanh Xuân, Hà Nội",
        citizenId: null,
      },
    });
  });

  it("có email thì gửi kèm; có CCCD thì viết hoa", () => {
    const result = buildWalkInRequest({
      ...validWalkIn,
      citizenId: "p1234567",
      email: " cuc@example.com ",
    });
    expect(result).toEqual({
      request: expect.objectContaining({ citizenId: "P1234567", email: "cuc@example.com" }),
    });
  });

  it("chưa chọn giới tính thì báo lỗi, không tạo body", () => {
    expect(buildWalkInRequest({ ...validWalkIn, sex: "" })).toEqual({
      errors: { sex: expect.any(String) },
    });
  });

  it("nhiều lỗi cùng lúc được báo đủ, mỗi ô một câu", () => {
    const result = buildWalkInRequest({
      ...validWalkIn,
      fullName: "Cúc",
      dateOfBirth: "",
      phone: "123",
      address: " ",
      email: "sai",
    });
    expect(result).toEqual({
      errors: {
        fullName: expect.any(String),
        dateOfBirth: expect.any(String),
        phone: expect.any(String),
        address: expect.any(String),
        email: expect.any(String),
      },
    });
  });
});

describe("findWalkInFieldErrors", () => {
  it("body của BE: sai giới tính hoặc CCCD bị báo đúng trường", () => {
    expect(
      findWalkInFieldErrors({
        fullName: "Lê Thị Cúc",
        dateOfBirth: "1990-07-01",
        sex: "OTHER",
        phone: "0987654321",
        address: "Hà Nội",
        citizenId: "AB",
      }),
    ).toEqual({ sex: expect.any(String), citizenId: expect.any(String) });
  });
});
