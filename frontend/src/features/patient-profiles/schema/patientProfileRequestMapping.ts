import { dateOfBirthTextToIso, isoToDateOfBirthText } from "./dateOfBirthText";
import { normalizePhone } from "./patientProfileFieldChecks";
import type { ProfileFieldValues } from "./patientProfileFieldRules";
import type {
  CreateProfileRequest,
  EditableProfileField,
  PatientProfileDetail,
  ProfileRequestInput,
  UpdateProfileRequest,
} from "../types/patientProfile";

// Chuyển đổi giữa giá trị form (chữ, ngày dd/mm/yyyy) và thân API (ISO, số điện thoại gọn).
// Gọi các hàm `…FromValues` sau khi đã kiểm tra bằng validateProfileFields.

function requireIsoDate(text: string): string {
  const iso = dateOfBirthTextToIso(text.trim());
  // Chỉ gọi sau khi kiểm tra đã qua; nếu không, là lỗi lập trình.
  if (!iso) throw new Error("Ngày sinh chưa được kiểm tra trước khi gửi.");
  return iso;
}

/** Form tạo hồ sơ → thân POST. Gọi sau khi đã kiểm tra đủ trường. */
export function createRequestFromValues(
  values: ProfileFieldValues,
): CreateProfileRequest {
  const { relationship, sex } = values;
  if (!relationship || !sex)
    throw new Error("Thiếu quan hệ hoặc giới tính khi gửi form tạo hồ sơ.");
  return {
    relationship,
    fullName: values.fullName.trim(),
    dateOfBirth: requireIsoDate(values.dateOfBirth),
    sex,
    phone: normalizePhone(values.phone),
    address: values.address.trim(),
    citizenId: values.citizenId.trim() || undefined,
    email: values.email.trim() || undefined,
  };
}

/** Form sửa hồ sơ → thân PATCH, chỉ gồm các trường được phép sửa. Ô rỗng xóa trường tùy chọn. */
export function updateRequestFromValues(
  values: ProfileFieldValues,
  fields: readonly EditableProfileField[],
): UpdateProfileRequest {
  const request: UpdateProfileRequest = {};
  for (const field of fields) {
    switch (field) {
      case "fullName":
        request.fullName = values.fullName.trim();
        break;
      case "dateOfBirth":
        request.dateOfBirth = requireIsoDate(values.dateOfBirth);
        break;
      case "sex":
        if (values.sex) request.sex = values.sex;
        break;
      case "phone":
        request.phone = normalizePhone(values.phone);
        break;
      case "address":
        request.address = values.address.trim();
        break;
      case "citizenId":
        request.citizenId = values.citizenId.trim() || null;
        break;
      case "email":
        request.email = values.email.trim() || null;
        break;
    }
  }
  return request;
}

/** Hồ sơ đang xem → giá trị form sửa. */
export function valuesFromDetail(
  detail: PatientProfileDetail,
): ProfileFieldValues {
  return {
    relationship: detail.relationship,
    fullName: detail.fullName,
    dateOfBirth: isoToDateOfBirthText(detail.dateOfBirth),
    sex: detail.sex,
    phone: detail.phone,
    address: detail.address ?? "",
    citizenId: detail.citizenId ?? "",
    email: detail.email ?? "",
  };
}

/** Thân request (dạng API) → giá trị dạng chữ để kiểm tra như form. Lớp API giả dùng. */
export function valuesFromRequest(
  input: ProfileRequestInput,
): ProfileFieldValues {
  return {
    relationship: input.relationship ?? "",
    fullName: input.fullName ?? "",
    dateOfBirth: input.dateOfBirth
      ? isoToDateOfBirthText(input.dateOfBirth)
      : "",
    sex: input.sex ?? "",
    phone: input.phone ?? "",
    address: input.address ?? "",
    citizenId: input.citizenId ?? "",
    email: input.email ?? "",
  };
}
