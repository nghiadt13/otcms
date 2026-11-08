import { apiRequest } from "@/lib/api/client";
import { notifyLinkedProfilesChanged } from "@/lib/patient-scope/linkedProfilesMockStore";
import type { ProfileFieldValues } from "../schema/patientProfileFieldRules";
import { createOwnRequestFromValues } from "../schema/patientProfileRequestMapping";
import type { PatientProfileDetail } from "../types/patientProfile";
import { createPatientProfile, getPatientProfile } from "./patientProfileApi";
import {
  toLiveProfileDetail,
  type ProfileDetailResponse,
} from "./patientProfileLiveDetail";

/**
 * POST /api/v1/me/patient-profile: bệnh nhân điền hồ sơ "Bản thân" lần đầu (UC04 AF). 201 trả hồ
 * sơ vừa tạo; 400 MSG-08 kèm fieldErrors; 409 MSG-08 khi đã có hồ sơ hoặc trùng hồ sơ tạo tại quầy.
 * Trong test dùng kho giả (như các hàm khác của patientProfileApi).
 */
export async function createOwnPatientProfile(
  values: ProfileFieldValues,
): Promise<PatientProfileDetail> {
  const body = createOwnRequestFromValues(values);
  if (process.env.NODE_ENV !== "test") {
    const response = await apiRequest<ProfileDetailResponse>(
      "/api/v1/me/patient-profile",
      { method: "POST", body: JSON.stringify(body) },
    );
    // Cổng khu bệnh nhân và các màn khác đang giữ danh sách cũ (chưa có Bản thân): báo tải lại.
    notifyLinkedProfilesChanged();
    return toLiveProfileDetail(response, "SELF");
  }
  const result = await createPatientProfile({
    relationship: "SELF",
    fullName: body.fullName,
    dateOfBirth: body.dateOfBirth,
    sex: body.gender,
    citizenId: body.nationalId,
    identityDocumentType: body.identityDocumentType,
  });
  if (result.status !== "LINKED") throw new Error("Hồ sơ giả không gắn được.");
  return getPatientProfile(result.profileId);
}
