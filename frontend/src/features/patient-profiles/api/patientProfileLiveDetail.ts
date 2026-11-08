import { ApiError } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";
import type { CareRelationship } from "@/lib/domain/careRelationship";
import { listLinkedProfiles } from "@/lib/patient-scope/linkedProfilesApi";
import type { PatientProfileDetail } from "../types/patientProfile";

/** PatientProfileDetailResponse của BE (GET/PUT /me/patient-profile và /me/dependents/{id}). */
export interface ProfileDetailResponse {
  patientProfileId: string;
  fullName: string;
  phoneCanonical: string | null;
  phoneOwnerType?: string | null;
  dateOfBirth: string;
  gender: string;
  address: string | null;
  nationalId: string | null;
  identityDocumentType?: string | null;
  /** Số phiên bản dùng cho khóa lạc quan khi PUT hồ sơ bản thân. */
  version?: number;
}

/** Đường dẫn đọc/ghi hồ sơ: bản thân ở /me/patient-profile, người thân ở /me/dependents/{id}. */
export function profileEndpoint(
  relationship: CareRelationship,
  profileId: string,
): `/api/v1/${string}` {
  return relationship === "SELF"
    ? "/api/v1/me/patient-profile"
    : `/api/v1/me/dependents/${encodeURIComponent(profileId)}`;
}

/** Phản hồi BE → chi tiết hồ sơ của FE. BE không trả mã BN, email hay lượt khám nên để mặc định. */
export function toLiveProfileDetail(
  response: ProfileDetailResponse,
  relationship: CareRelationship,
): PatientProfileDetail {
  return {
    id: response.patientProfileId,
    patientCode: "",
    relationship,
    fullName: response.fullName,
    dateOfBirth: response.dateOfBirth,
    sex: response.gender === "FEMALE" ? "FEMALE" : "MALE",
    phone: response.phoneCanonical ?? "",
    address: response.address,
    citizenId: response.nationalId,
    email: null,
    hasVisits: false,
    editableFields: [
      "fullName",
      "dateOfBirth",
      "sex",
      "phone",
      "address",
      "citizenId",
    ],
    allowedActions: ["update", "unlink", "editHistory"],
  };
}

/**
 * Chi tiết một hồ sơ đã gắn từ BE: hồ sơ Bản thân đọc ở /me/patient-profile, người thân ở
 * /me/dependents/{id}. BE không trả quan hệ, mã BN, email hay lượt khám, nên quan hệ lấy từ
 * danh sách đã gắn; hồ sơ không có trong danh sách → 404 MSG-41 (không lộ hồ sơ người khác).
 */
export async function getLinkedProfileDetailLive(
  profileId: string,
  signal?: AbortSignal,
): Promise<PatientProfileDetail> {
  const { items } = await listLinkedProfiles(signal);
  const entry = items.find((item) => item.id === profileId);
  if (!entry) throw new ApiError(404, { code: "MSG-41" });
  const response = await apiRequest<ProfileDetailResponse>(
    profileEndpoint(entry.relationship, profileId),
    { signal },
  );
  return toLiveProfileDetail(response, entry.relationship);
}
