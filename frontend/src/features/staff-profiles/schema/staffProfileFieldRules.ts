import type { MsgCode } from "@/lib/api/apiError";
import type { StaffRole } from "@/lib/domain/clinicRoles";
import type { DoctorInfo } from "../types/staffProfile";

export type StaffProfileFieldKey =
  | "displayName"
  | "workContact"
  | "photo"
  | "doctor.qualifications"
  | "doctor.biography"
  | "doctor.consultationInfo";

export type StaffProfileFieldErrors = Partial<
  Record<StaffProfileFieldKey, MsgCode>
>;

export const PHOTO_MAX_BYTES = 2 * 1024 * 1024;
export const PHOTO_ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"];

const NAME_MAX = 100;
const CONTACT_MAX = 100;
const QUALIFICATIONS_MAX = 200;
const BIOGRAPHY_MAX = 2000;
const CONSULTATION_MAX = 1000;
const PHONE_PATTERN = /^[0-9+()\s.-]{8,20}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Liên hệ công việc: số điện thoại hoặc email (R3 chưa chốt, đề xuất). */
export function isValidWorkContact(value: string): boolean {
  const text = value.trim();
  return (
    text.length > 0 &&
    text.length <= CONTACT_MAX &&
    (PHONE_PATTERN.test(text) || EMAIL_PATTERN.test(text))
  );
}

type Values = {
  displayName: string;
  workContact: string;
  doctor?: Pick<
    DoctorInfo,
    "qualifications" | "biography" | "consultationInfo"
  >;
};

/** Trả bản đồ ô → MSG-12; rỗng nghĩa là hợp lệ. Nhóm bác sĩ chỉ xét khi vai trò là bác sĩ. */
export function validateStaffProfileInput(
  values: Values,
  role: StaffRole,
): StaffProfileFieldErrors {
  const errors: StaffProfileFieldErrors = {};
  const name = values.displayName.trim();
  if (name.length === 0 || name.length > NAME_MAX) errors.displayName = "MSG-12";
  if (!isValidWorkContact(values.workContact)) errors.workContact = "MSG-12";
  if (role === "ORTHOPEDIC_DOCTOR" && values.doctor) {
    if (values.doctor.qualifications.length > QUALIFICATIONS_MAX)
      errors["doctor.qualifications"] = "MSG-12";
    if (values.doctor.biography.length > BIOGRAPHY_MAX)
      errors["doctor.biography"] = "MSG-12";
    if (values.doctor.consultationInfo.length > CONSULTATION_MAX)
      errors["doctor.consultationInfo"] = "MSG-12";
  }
  return errors;
}

/** Kiểm sơ bộ ảnh; BE kiểm lại theo nội dung file. */
export function validatePhotoFile(file: File): MsgCode | null {
  if (!PHOTO_ACCEPTED_TYPES.includes(file.type)) return "MSG-12";
  if (file.size > PHOTO_MAX_BYTES) return "MSG-12";
  return null;
}
