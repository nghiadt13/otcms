import { describe, expect, it } from "vitest";
import type { PatientProfileDetail } from "../types/patientProfile";
import { normalizePhone } from "./patientProfileFieldChecks";
import {
  CREATE_PROFILE_FIELDS,
  EDITABLE_FIELDS_AFTER_VISIT,
  isProfileFieldName,
  validateProfileFields,
  type ProfileFieldValues,
} from "./patientProfileFieldRules";
import {
  createRequestFromValues,
  updateRequestFromValues,
  valuesFromDetail,
  valuesFromRequest,
} from "./patientProfileRequestMapping";

const TODAY = new Date(2026, 9, 24, 12);

const VALID: ProfileFieldValues = {
  relationship: "CHILD",
  fullName: "Nguyễn Gia Bảo",
  dateOfBirth: "14/02/2018",
  sex: "MALE",
  phone: "0933 445 566",
  address: "45 Nguyễn Huệ, Quận 1",
  citizenId: "",
  email: "",
};

describe("validateProfileFields", () => {
  it("chấp nhận bộ giá trị đầy đủ, trường tùy chọn để trống", () => {
    expect(validateProfileFields(VALID, CREATE_PROFILE_FIELDS, TODAY)).toEqual(
      {},
    );
  });

  it("báo đúng trường sai bằng câu gợi ý dưới ô", () => {
    const errors = validateProfileFields(
      {
        ...VALID,
        relationship: "",
        fullName: "A",
        sex: "",
        phone: "12345",
        citizenId: "AB",
        email: "ten@",
      },
      CREATE_PROFILE_FIELDS,
      TODAY,
    );
    expect(errors).toEqual({
      relationship: "Chọn quan hệ với bạn.",
      fullName: "Họ và tên gồm ít nhất hai chữ, không có số.",
      sex: "Chọn giới tính.",
      phone: "Số điện thoại gồm 10 chữ số, bắt đầu bằng 0.",
      citizenId: "Số định danh gồm 8 đến 12 chữ hoặc số.",
      email: "Email chưa đúng định dạng, ví dụ ten@example.com.",
    });
  });

  it("tên có số bị từ chối, tên có dấu tiếng Việt được chấp nhận", () => {
    expect(
      validateProfileFields({ ...VALID, fullName: "Nguyễn Văn 2" }, [
        "fullName",
      ]),
    ).toEqual({ fullName: "Họ và tên gồm ít nhất hai chữ, không có số." });
    expect(
      validateProfileFields({ ...VALID, fullName: "Đặng Thị Ỷ" }, ["fullName"]),
    ).toEqual({});
  });

  it("địa chỉ bắt buộc và có giới hạn độ dài", () => {
    expect(
      validateProfileFields({ ...VALID, address: "   " }, ["address"]),
    ).toEqual({ address: "Nhập địa chỉ." });
    expect(
      validateProfileFields({ ...VALID, address: "a".repeat(256) }, [
        "address",
      ]),
    ).toEqual({ address: "Địa chỉ tối đa 255 ký tự." });
  });

  it("số điện thoại bỏ khoảng trắng, phải bắt đầu bằng 0 và đủ 10 số", () => {
    expect(normalizePhone("0912 345.678")).toBe("0912345678");
    expect(
      validateProfileFields({ ...VALID, phone: "0912 345 678" }, ["phone"]),
    ).toEqual({});
    expect(validateProfileFields({ ...VALID, phone: "" }, ["phone"])).toEqual({
      phone: "Nhập số điện thoại.",
    });
  });

  it("số định danh và email tùy chọn: rỗng thì hợp lệ, sai dạng thì báo", () => {
    expect(
      validateProfileFields(
        { ...VALID, citizenId: "079098001234", email: "" },
        ["citizenId", "email"],
      ),
    ).toEqual({});
    expect(
      validateProfileFields({ ...VALID, email: "an@example.com" }, ["email"]),
    ).toEqual({});
  });

  it("chỉ kiểm tra các trường được yêu cầu", () => {
    expect(
      validateProfileFields({ ...VALID, fullName: "" }, ["phone"]),
    ).toEqual({});
  });
});

describe("isProfileFieldName", () => {
  it("nhận đúng tên trường của hồ sơ, loại tên lạ", () => {
    expect(isProfileFieldName("phone")).toBe(true);
    expect(isProfileFieldName("relationship")).toBe(true);
    expect(isProfileFieldName("conditions")).toBe(false);
  });
});

describe("createRequestFromValues", () => {
  it("đổi ngày sang ISO, bỏ khoảng trắng số điện thoại, ô tùy chọn rỗng thành undefined", () => {
    expect(createRequestFromValues(VALID)).toEqual({
      relationship: "CHILD",
      fullName: "Nguyễn Gia Bảo",
      dateOfBirth: "2018-02-14",
      sex: "MALE",
      phone: "0933445566",
      address: "45 Nguyễn Huệ, Quận 1",
      citizenId: undefined,
      email: undefined,
    });
  });

  it("từ chối gửi khi chưa chọn quan hệ hoặc giới tính", () => {
    expect(() =>
      createRequestFromValues({ ...VALID, relationship: "" }),
    ).toThrow("Thiếu quan hệ hoặc giới tính");
    expect(() => createRequestFromValues({ ...VALID, sex: "" })).toThrow(
      "Thiếu quan hệ hoặc giới tính",
    );
  });
});

describe("updateRequestFromValues", () => {
  it("chỉ gồm các trường được phép sửa, ô rỗng thành null", () => {
    expect(
      updateRequestFromValues({ ...VALID, email: "  ", citizenId: "" }, [
        "phone",
        "email",
        "citizenId",
      ]),
    ).toEqual({ phone: "0933445566", email: null, citizenId: null });
  });
});

describe("valuesFromDetail và valuesFromRequest", () => {
  const detail: PatientProfileDetail = {
    id: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f02",
    patientCode: "BN-000456",
    relationship: "PARENT",
    fullName: "Nguyễn Thị Lan",
    dateOfBirth: "1936-03-14",
    sex: "FEMALE",
    phone: "0987654321",
    address: null,
    citizenId: "079136001234",
    email: null,
    hasVisits: true,
    editableFields: [...EDITABLE_FIELDS_AFTER_VISIT],
    allowedActions: ["update", "unlink", "editHistory"],
  };

  it("đổi hai chiều giữa ngày ISO và dd/mm/yyyy, ô null thành chuỗi rỗng", () => {
    const values = valuesFromDetail(detail);
    expect(values).toMatchObject({
      dateOfBirth: "14/03/1936",
      address: "",
      email: "",
      citizenId: "079136001234",
    });
    expect(valuesFromRequest({ dateOfBirth: "1936-03-14" }).dateOfBirth).toBe(
      "14/03/1936",
    );
  });

  it("thân request thiếu trường thì giá trị form là rỗng", () => {
    expect(valuesFromRequest({ relationship: "SELF" })).toEqual({
      relationship: "SELF",
      fullName: "",
      dateOfBirth: "",
      sex: "",
      phone: "",
      address: "",
      citizenId: "",
      email: "",
    });
  });

  it("hồ sơ đã khám không cho sửa họ tên và ngày sinh", () => {
    expect(EDITABLE_FIELDS_AFTER_VISIT).toEqual([
      "sex",
      "phone",
      "address",
      "citizenId",
      "email",
    ]);
  });
});
