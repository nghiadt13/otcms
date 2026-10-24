import type { CareRelationship } from "@/lib/domain/careRelationship";
import type {
  EditableProfileField,
  PatientSex,
} from "../types/patientProfile";
import { validateDateOfBirthText } from "./dateOfBirthText";
import {
  checkAddress,
  checkCitizenId,
  checkEmail,
  checkFullName,
  checkPhone,
  checkRelationship,
  checkSex,
} from "./patientProfileFieldChecks";

// Bộ luật hồ sơ bệnh nhân: tên trường, danh sách trường theo ngữ cảnh, và kiểm tra cả
// nhóm trường. Dùng chung cho form (chuỗi người dùng gõ) và lớp API giả (kiểm tra thân request
// như BE). Lớp API giả chỉ dùng tên trường để trả MSG-17 kèm fieldErrors.

export type ProfileFieldName = "relationship" | EditableProfileField;
export type ProfileFieldErrors = Partial<Record<ProfileFieldName, string>>;

/** Giá trị dạng chữ của form. Ngày sinh dd/mm/yyyy; giới tính và quan hệ rỗng khi chưa chọn. */
export interface ProfileFieldValues {
  relationship: CareRelationship | "";
  fullName: string;
  dateOfBirth: string;
  sex: PatientSex | "";
  phone: string;
  address: string;
  citizenId: string;
  email: string;
}

export const CREATE_PROFILE_FIELDS: readonly ProfileFieldName[] = [
  "relationship",
  "fullName",
  "dateOfBirth",
  "sex",
  "phone",
  "address",
  "citizenId",
  "email",
];

/** Hồ sơ chưa có lượt khám: sửa được mọi trường hành chính. */
export const EDITABLE_FIELDS_BEFORE_VISIT: EditableProfileField[] = [
  "fullName",
  "dateOfBirth",
  "sex",
  "phone",
  "address",
  "citizenId",
  "email",
];

/** Hồ sơ đã có lượt khám: khóa họ tên và ngày sinh, tránh lệch hồ sơ y tế. */
export const EDITABLE_FIELDS_AFTER_VISIT: EditableProfileField[] =
  EDITABLE_FIELDS_BEFORE_VISIT.filter(
    (field) => field !== "fullName" && field !== "dateOfBirth",
  );

/** Tên trường trong `fieldErrors` của BE có thuộc bộ luật không (dùng khi ánh xạ lỗi). */
export function isProfileFieldName(value: string): value is ProfileFieldName {
  return (CREATE_PROFILE_FIELDS as readonly string[]).includes(value);
}

function checkField(
  field: ProfileFieldName,
  values: ProfileFieldValues,
  today: Date,
): string | undefined {
  switch (field) {
    case "relationship":
      return checkRelationship(values.relationship);
    case "fullName":
      return checkFullName(values.fullName);
    case "dateOfBirth":
      return validateDateOfBirthText(values.dateOfBirth, today);
    case "sex":
      return checkSex(values.sex);
    case "phone":
      return checkPhone(values.phone);
    case "address":
      return checkAddress(values.address);
    case "citizenId":
      return checkCitizenId(values.citizenId);
    case "email":
      return checkEmail(values.email);
  }
}

/** Kiểm tra các trường được liệt kê; trả về lỗi theo trường, rỗng nghĩa là hợp lệ. */
export function validateProfileFields(
  values: ProfileFieldValues,
  fields: readonly ProfileFieldName[],
  today: Date = new Date(),
): ProfileFieldErrors {
  const errors: ProfileFieldErrors = {};
  for (const field of fields) {
    const message = checkField(field, values, today);
    if (message) errors[field] = message;
  }
  return errors;
}
