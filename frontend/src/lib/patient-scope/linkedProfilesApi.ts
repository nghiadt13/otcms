import { mockDelay, mockFail } from "@/lib/api/mockServer";
import { apiRequest } from "@/lib/api/client";
import {
  getLinkedProfileRecords,
  isLinkedProfilesMockFailing,
} from "./linkedProfilesMockStore";
import type { LinkedProfile, LinkedProfileList } from "./types";

type BackendProfileItem = {
  patientProfileId: string;
  fullName: string;
  dateOfBirth: string;
  gender: LinkedProfile["sex"];
  relationship: LinkedProfile["relationship"];
  linkStatus: LinkedProfile["linkStatus"];
};

export function mapBackendProfileItems(items: BackendProfileItem[]): LinkedProfile[] {
  return items.map((item) => ({
    id: item.patientProfileId,
    patientCode: "",
    fullName: item.fullName,
    dateOfBirth: item.dateOfBirth,
    sex: item.gender,
    relationship: item.relationship,
    linkStatus: item.linkStatus,
  })).sort(selfFirstThenByName);
}

// Production đọc hồ sơ thuộc tài khoản đăng nhập từ backend; dữ liệu giả chỉ phục vụ test.

const LATENCY_MS = process.env.NODE_ENV === "test" ? 0 : 300;

/** Bản thân đứng đầu, sau đó theo tên (so sánh theo tiếng Việt). */
function selfFirstThenByName(a: LinkedProfile, b: LinkedProfile): number {
  const selfOrder =
    Number(b.relationship === "SELF") - Number(a.relationship === "SELF");
  return selfOrder || a.fullName.localeCompare(b.fullName, "vi");
}

/** GET /api/v1/me/patient-profiles → `{ items }` */
export async function listLinkedProfiles(
  signal?: AbortSignal,
): Promise<LinkedProfileList> {
  if (process.env.NODE_ENV !== "test") {
    const result = await apiRequest<{ items: BackendProfileItem[] }>(
      "/api/v1/me/patient-profiles",
      { signal },
    );
    return { items: mapBackendProfileItems(result.items) };
  }
  await mockDelay(LATENCY_MS, signal);
  if (isLinkedProfilesMockFailing()) mockFail(500, "MSG-04");
  return { items: getLinkedProfileRecords().sort(selfFirstThenByName) };
}
