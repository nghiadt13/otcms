import { apiRequest } from "@/lib/api/client";
import { mockDelay, mockFail } from "@/lib/api/mockServer";
import type { CareRelationship } from "@/lib/domain/careRelationship";
import {
  getLinkedProfileRecords,
  isLinkedProfilesMockFailing,
} from "./linkedProfilesMockStore";
import type { LinkedProfile, LinkedProfileList } from "./types";

// Chỉ đọc. Ngoài môi trường test gọi BE (GET /api/v1/me/patient-profiles, trả hồ sơ của tài
// khoản đang đăng nhập, FE không gửi id người dùng); trong test dùng kho giả.

const LATENCY_MS = process.env.NODE_ENV === "test" ? 0 : 300;

/** Bản thân đứng đầu, sau đó theo tên (so sánh theo tiếng Việt). */
function selfFirstThenByName(a: LinkedProfile, b: LinkedProfile): number {
  const selfOrder =
    Number(b.relationship === "SELF") - Number(a.relationship === "SELF");
  return selfOrder || a.fullName.localeCompare(b.fullName, "vi");
}

/** Một dòng của GET /api/v1/me/patient-profiles (BE ProfileItem). */
interface ProfileItemResponse {
  patientProfileId: string;
  fullName: string;
  dateOfBirth: string;
  gender: string;
  relationship: CareRelationship;
  /** CONFIRMED hoặc đang chờ xác thực (OTP / quầy). */
  linkStatus: string;
}

/** GET /api/v1/me/patient-profiles → `{ items }`. Chỉ lấy hồ sơ đã xác nhận; BE không trả mã BN. */
async function listLinkedProfilesLive(
  signal?: AbortSignal,
): Promise<LinkedProfileList> {
  const response = await apiRequest<{ items: ProfileItemResponse[] }>(
    "/api/v1/me/patient-profiles",
    { signal },
  );
  return {
    items: response.items
      .filter((item) => item.linkStatus === "CONFIRMED")
      .map((item) => ({
        id: item.patientProfileId,
        patientCode: "",
        fullName: item.fullName,
        dateOfBirth: item.dateOfBirth,
        sex: item.gender === "FEMALE" ? "FEMALE" : "MALE",
        relationship: item.relationship,
      })),
  };
}

/** GET /api/v1/me/patient-profiles → `{ items }` */
export async function listLinkedProfiles(
  signal?: AbortSignal,
): Promise<LinkedProfileList> {
  if (process.env.NODE_ENV !== "test") return listLinkedProfilesLive(signal);
  await mockDelay(LATENCY_MS, signal);
  if (isLinkedProfilesMockFailing()) mockFail(500, "MSG-04");
  return { items: getLinkedProfileRecords().sort(selfFirstThenByName) };
}
