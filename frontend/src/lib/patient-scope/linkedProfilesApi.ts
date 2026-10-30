import { mockDelay, mockFail } from "@/lib/api/mockServer";
import {
  getLinkedProfileRecords,
  isLinkedProfilesMockFailing,
} from "./linkedProfilesMockStore";
import type { LinkedProfile, LinkedProfileList } from "./types";

// LỚP API GIẢ, chỉ đọc. Giữ nguyên chữ ký khi nối backend; khi đó đổi thân hàm sang
// apiRequest("/api/v1/me/patient-profiles"). BE trả hồ sơ thuộc tài khoản đang đăng nhập,
// FE không gửi id người dùng.

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
  await mockDelay(LATENCY_MS, signal);
  if (isLinkedProfilesMockFailing()) mockFail(500, "MSG-04");
  return { items: getLinkedProfileRecords().sort(selfFirstThenByName) };
}
