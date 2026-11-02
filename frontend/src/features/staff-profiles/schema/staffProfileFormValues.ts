import type { StaffRole } from "@/lib/domain/clinicRoles";
import type {
  DoctorInfo,
  StaffProfileDetail,
  StaffProfileInput,
} from "../types/staffProfile";
import type { StaffProfileFieldKey } from "./staffProfileFieldRules";

/** Giá trị các ô nhập của form tạo/sửa (ảnh giữ riêng vì là File). */
export type StaffProfileFormValues = {
  displayName: string;
  workContact: string;
  doctor: DoctorInfo;
};

export const EMPTY_DOCTOR_INFO: DoctorInfo = {
  specialties: [],
  qualifications: "",
  biography: "",
  consultationInfo: "",
  isPublic: false, // mặc định tắt khi tạo (AF1 UC17)
};

export const EMPTY_FORM_VALUES: StaffProfileFormValues = {
  displayName: "",
  workContact: "",
  doctor: EMPTY_DOCTOR_INFO,
};

export function formValuesFromDetail(
  detail: StaffProfileDetail,
): StaffProfileFormValues {
  return {
    displayName: detail.displayName,
    workContact: detail.workContact,
    doctor: detail.doctor ? { ...detail.doctor } : EMPTY_DOCTOR_INFO,
  };
}

/** Payload JSON; nhóm bác sĩ chỉ gửi khi vai trò là bác sĩ. */
export function toStaffProfileInput(
  values: StaffProfileFormValues,
  role: StaffRole,
): Omit<StaffProfileInput, "accountId"> {
  return {
    displayName: values.displayName.trim(),
    workContact: values.workContact.trim(),
    ...(role === "ORTHOPEDIC_DOCTOR" ? { doctor: values.doctor } : {}),
  };
}

/** Câu lỗi cho từng ô: nói rõ lỗi gì và cách sửa. */
export const FIELD_ERROR_TEXT: Record<StaffProfileFieldKey, string> = {
  displayName: "Nhập họ tên hiển thị, tối đa 100 ký tự.",
  workContact: "Nhập số điện thoại hoặc email công việc hợp lệ.",
  photo: "Chọn ảnh JPG, PNG hoặc WebP, dung lượng tối đa 2 MB.",
  "doctor.qualifications": "Trình độ tối đa 200 ký tự.",
  "doctor.biography": "Tiểu sử tối đa 2.000 ký tự.",
  "doctor.consultationInfo": "Thông tin khám tối đa 1.000 ký tự.",
};

export const FIELD_FOCUS_ORDER: StaffProfileFieldKey[] = [
  "displayName",
  "workContact",
  "doctor.qualifications",
  "doctor.biography",
  "doctor.consultationInfo",
  "photo",
];

export const fieldDomId = (key: StaffProfileFieldKey) => `staff-field-${key}`;

/** Đưa con trỏ về ô lỗi đầu tiên theo thứ tự trên form. */
export function focusFirstError(errors: Partial<Record<string, unknown>>) {
  const first = FIELD_FOCUS_ORDER.find((key) => errors[key]);
  if (first) document.getElementById(fieldDomId(first))?.focus();
}
