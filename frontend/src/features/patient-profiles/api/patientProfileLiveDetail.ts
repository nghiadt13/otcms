import { ApiError } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";
import { listLinkedProfiles } from "@/lib/patient-scope/linkedProfilesApi";
import type { PatientProfileDetail } from "../types/patientProfile";

/** PatientProfileDetailResponse của BE (GET /me/patient-profile và /me/dependents/{id}). */
interface ProfileDetailResponse {
  patientProfileId: string;
  fullName: string;
  phoneCanonical: string | null;
  dateOfBirth: string;
  gender: string;
  address: string | null;
  nationalId: string | null;
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
    entry.relationship === "SELF"
      ? "/api/v1/me/patient-profile"
      : `/api/v1/me/dependents/${encodeURIComponent(profileId)}`,
    { signal },
  );
  return {
    id: response.patientProfileId,
    patientCode: "",
    relationship: entry.relationship,
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
