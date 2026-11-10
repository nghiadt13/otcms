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

/** Form thêm người thân → thân POST. Gọi sau khi đã kiểm tra đủ trường. */
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
    citizenId: values.citizenId.trim() || undefined,
    // Form chỉ có một ô số định danh nên mặc định loại CCCD (BE bắt buộc loại khi có số).
    identityDocumentType: values.citizenId.trim() ? "CCCD" : undefined,
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

/** Giá trị form trống khi bệnh nhân điền hồ sơ "Bản thân" lần đầu; họ tên lấy sẵn từ lúc đăng ký. */
export function emptyOwnProfileValues(fullName: string): ProfileFieldValues {
  return {
    relationship: "SELF",
    fullName,
    dateOfBirth: "",
    sex: "",
    phone: "",
    address: "",
    citizenId: "",
    email: "",
  };
}

/** Thân POST /api/v1/me/patient-profile (BE CreateMyPatientProfileRequest). Gọi sau khi đã kiểm tra đủ trường. */
export interface CreateOwnProfileBody {
  fullName: string;
  phone: string;
  dateOfBirth: string;
  gender: "MALE" | "FEMALE";
  address: string;
  nationalId?: string;
  identityDocumentType?: "CCCD";
}

export function createOwnRequestFromValues(
  values: ProfileFieldValues,
): CreateOwnProfileBody {
  if (!values.sex) throw new Error("Thiếu giới tính khi gửi form hồ sơ.");
  const citizenId = values.citizenId.trim();
  return {
    fullName: values.fullName.trim(),
    phone: normalizePhone(values.phone),
    dateOfBirth: requireIsoDate(values.dateOfBirth),
    gender: values.sex,
    address: values.address.trim(),
    // Form chỉ có một ô số định danh nên mặc định loại CCCD (BE bắt buộc loại khi có số).
    ...(citizenId ? { nationalId: citizenId, identityDocumentType: "CCCD" as const } : {}),
  };
}
