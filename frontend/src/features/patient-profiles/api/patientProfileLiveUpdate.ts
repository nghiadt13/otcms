import { ApiError } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";
import type { CareRelationship } from "@/lib/domain/careRelationship";
import { listLinkedProfiles } from "@/lib/patient-scope/linkedProfilesApi";
import type {
  PatientProfileDetail,
  UpdateProfileRequest,
} from "../types/patientProfile";
import {
  profileEndpoint,
  toLiveProfileDetail,
  type ProfileDetailResponse,
} from "./patientProfileLiveDetail";

/** Thân PUT /me/patient-profile (BE UpdatePatientProfileRequest), thay toàn bộ hồ sơ bản thân. */
export interface SelfProfilePutBody {
  fullName: string;
  phone: string;
  phoneOwnerType: string;
  dateOfBirth: string;
  gender: string;
  address: string | null;
  nationalId: string | null;
  identityDocumentType: string | null;
  version: number;
}

/** Thân PUT /me/dependents/{id} (BE UpdateDependentRequest): không có version, phoneOwnerType. */
export interface DependentProfilePutBody {
  fullName: string;
  dateOfBirth: string;
  gender: string;
  phone: string | null;
  address: string | null;
  nationalId: string | null;
  identityDocumentType: string | null;
}

/** Số định danh có thì phải có loại giấy tờ; form chỉ có một ô nên mặc định CCCD (như lúc thêm người thân). */
function identityFields(
  current: ProfileDetailResponse,
  citizenId: UpdateProfileRequest["citizenId"],
) {
  const nationalId = citizenId === undefined ? current.nationalId : citizenId;
  return {
    nationalId: nationalId || null,
    identityDocumentType: nationalId
      ? (current.identityDocumentType ?? "CCCD")
      : null,
  };
}

/**
 * Gộp các trường người dùng vừa sửa vào hồ sơ hiện tại của BE thành thân PUT đầy đủ. PUT thay
 * toàn bộ nên mọi trường không sửa lấy lại từ `current` (vừa đọc), kể cả `version` và
 * `phoneOwnerType` mà form không có.
 */
export function buildProfilePutBody(
  relationship: CareRelationship,
  current: ProfileDetailResponse,
  changes: UpdateProfileRequest,
): SelfProfilePutBody | DependentProfilePutBody {
  const common = {
    fullName: changes.fullName ?? current.fullName,
    dateOfBirth: changes.dateOfBirth ?? current.dateOfBirth,
    gender: changes.sex ?? current.gender,
    address: changes.address === undefined ? current.address : changes.address,
    ...identityFields(current, changes.citizenId),
  };
  const phone = changes.phone ?? current.phoneCanonical;
  if (relationship !== "SELF") return { ...common, phone: phone || null };
  return {
    ...common,
    phone: phone ?? "",
    phoneOwnerType: current.phoneOwnerType ?? "PATIENT",
    version: current.version ?? 0,
  };
}

/**
 * Lưu hồ sơ đã gắn lên BE: đọc hồ sơ hiện tại, gộp phần vừa sửa, rồi PUT. Bản thân dùng
 * PUT /me/patient-profile, người thân dùng PUT /me/dependents/{id}. Hồ sơ không có trong danh sách
 * đã gắn → 404 MSG-41.
 */
export async function updateLinkedProfileLive(
  profileId: string,
  changes: UpdateProfileRequest,
): Promise<PatientProfileDetail> {
  const { items } = await listLinkedProfiles();
  const entry = items.find((item) => item.id === profileId);
  if (!entry) throw new ApiError(404, { code: "MSG-41" });
  const path = profileEndpoint(entry.relationship, profileId);
  const current = await apiRequest<ProfileDetailResponse>(path);
  const response = await apiRequest<ProfileDetailResponse>(path, {
    method: "PUT",
    body: JSON.stringify(
      buildProfilePutBody(entry.relationship, current, changes),
    ),
  });
  return toLiveProfileDetail(response, entry.relationship);
}
