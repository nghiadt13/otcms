import { mockFail } from "@/lib/api/mockServer";
import type { ItemsResponse } from "@/lib/api/pagination";
import {
  getLinkedProfileRecords,
  replaceLinkedProfilesMock,
} from "@/lib/patient-scope/linkedProfilesMockStore";
import type { LinkedProfile } from "@/lib/patient-scope/types";
import {
  throwFieldErrors,
  linkedEntryOrFail,
  isLinkedToAccount,
  mockLatency,
  toLinkedProfile,
} from "./patientProfileMockHelpers";
import { validateProfileFields } from "../schema/patientProfileFieldRules";
import { valuesFromRequest } from "../schema/patientProfileRequestMapping";
import type {
  LinkProfileRequest,
  SuggestedProfile,
} from "../types/patientProfile";
import {
  MOCK_ACCOUNT_EMAIL,
  MOCK_ACCOUNT_ID,
  type ClinicPatientRecord,
} from "./patientProfileMockData";
import {
  findClinicRecord,
  isSuggestionListFailing,
  listClinicRecords,
  saveClinicRecord,
} from "./patientProfileMockStore";

// LỚP API GIẢ gợi ý và gắn/bỏ gắn hồ sơ theo email đã xác minh (BR-17). Chữ ký = REST thật.

/** Hồ sơ có email trùng email đã xác minh của tài khoản. Không kể đã gắn hay chưa. */
function hasVerifiedEmail(record: ClinicPatientRecord): boolean {
  return (
    record.email !== null &&
    record.email.toLowerCase() === MOCK_ACCOUNT_EMAIL.toLowerCase()
  );
}

/** Chỉ lộ họ tên, năm sinh, mã bệnh nhân, giới tính: không lộ ngày sinh đủ, SĐT, email. */
function toSuggestion(record: ClinicPatientRecord): SuggestedProfile {
  return {
    id: record.id,
    patientCode: record.patientCode,
    fullName: record.fullName,
    birthYear: Number(record.dateOfBirth.slice(0, 4)),
    sex: record.sex,
  };
}

/** GET /api/v1/me/patient-profiles/suggestions */
export async function listSuggestedProfiles(
  signal?: AbortSignal,
): Promise<ItemsResponse<SuggestedProfile>> {
  await mockLatency(signal);
  if (isSuggestionListFailing()) mockFail(500, "MSG-04");
  const items = listClinicRecords()
    .filter(
      (record) => hasVerifiedEmail(record) && !isLinkedToAccount(record.id),
    )
    .sort((a, b) => a.fullName.localeCompare(b.fullName, "vi"))
    .map(toSuggestion);
  return { items };
}

/**
 * POST /api/v1/me/patient-profiles/links. Hồ sơ không còn là gợi ý (không có email khớp,
 * không tồn tại) → 404 MSG-41, để không lộ hồ sơ của người khác. Đã gắn → 409 MSG-21.
 * Bản thân: tài khoản đã có Bản thân, hoặc hồ sơ là Bản thân của tài khoản khác → 409 MSG-21.
 */
export async function linkSuggestedProfile(
  body: LinkProfileRequest,
): Promise<LinkedProfile> {
  await mockLatency();
  throwFieldErrors(
    validateProfileFields(
      valuesFromRequest({ relationship: body.relationship }),
      ["relationship"],
    ),
  );
  const record = findClinicRecord(body.patientId);
  if (!record || !hasVerifiedEmail(record)) mockFail(404, "MSG-41");

  const linked = getLinkedProfileRecords();
  if (linked.some((item) => item.id === record.id)) mockFail(409, "MSG-21");
  if (body.relationship === "SELF") {
    if (linked.some((item) => item.relationship === "SELF"))
      mockFail(409, "MSG-21");
    if (record.selfAccountId !== null && record.selfAccountId !== MOCK_ACCOUNT_ID)
      mockFail(409, "MSG-21");
    saveClinicRecord({ ...record, selfAccountId: MOCK_ACCOUNT_ID });
  }

  const entry = toLinkedProfile(record, body.relationship);
  replaceLinkedProfilesMock([...linked, entry]);
  return entry;
}

/**
 * DELETE /api/v1/me/patient-profiles/{id}/link. Bỏ quyền xem và đặt lịch; hồ sơ và lịch sử
 * khám giữ nguyên (BR-16). Bỏ Bản thân thì hồ sơ không còn là Bản thân của tài khoản này.
 */
export async function unlinkPatientProfile(profileId: string): Promise<void> {
  await mockLatency();
  const linked = linkedEntryOrFail(profileId);
  const record = findClinicRecord(profileId);
  if (record && record.selfAccountId === MOCK_ACCOUNT_ID)
    saveClinicRecord({ ...record, selfAccountId: null });
  replaceLinkedProfilesMock(
    getLinkedProfileRecords().filter((item) => item.id !== linked.id),
  );
}
