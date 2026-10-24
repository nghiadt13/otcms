import { apiRequest } from "@/lib/api/client";
import type { ApiFieldError } from "@/lib/api/apiError";
import { clone, mockDelay, mockFail } from "@/lib/api/mockServer";
import { toQueryString } from "@/lib/api/pagination";
import { registerMockPatient } from "@/lib/mock/sharedPatientDirectoryMock";
import {
  normalizeFullName,
  type PatientFieldErrors,
} from "../schema/patientFieldRules";
import {
  findLookupFieldErrors,
  findWalkInFieldErrors,
} from "../schema/patientRequestRules";
import type {
  CreateWalkInPatientRequest,
  PatientLookupQuery,
  PatientLookupResponse,
  PatientMatch,
} from "../types/patient";
import { createInitialPatients, type MockPatient } from "./patientMockData";

// Lớp API cho /api/v1/patients (khu Lễ tân). Chữ ký khớp REST của BE. Khi nối BE đặt
// USE_MOCK = false: nhánh apiRequest đã viết sẵn. Lỗi giả đúng hình ProblemDetail + MSG.
const USE_MOCK = true;

let store: MockPatient[] = createInitialPatients();
let sequence = 0;

/** Chỉ dùng trong test: đặt lại kho giả về dữ liệu ban đầu. */
export function resetPatientMock(): void {
  store = createInitialPatients();
  sequence = 0;
}

const latency = (signal?: AbortSignal) =>
  mockDelay(
    process.env.NODE_ENV === "test" ? 0 : 300 + Math.random() * 200,
    signal,
  );

/** Hồ sơ đưa ra màn hình: không có SĐT, địa chỉ, email, CCCD. */
function toMatch(patient: MockPatient): PatientMatch {
  return {
    id: patient.id,
    patientCode: patient.patientCode,
    fullName: patient.fullName,
    dateOfBirth: patient.dateOfBirth,
    sex: patient.sex,
    hasAccount: patient.hasAccount,
  };
}

/** So họ tên: không phân biệt hoa thường, gọn khoảng trắng. */
function sameName(a: string, b: string): boolean {
  const key = (value: string) =>
    normalizeFullName(value).normalize("NFC").toLowerCase();
  return key(a) === key(b);
}

function samePhoneAndBirth(phone: string, dateOfBirth: string): MockPatient[] {
  return store.filter(
    (patient) =>
      patient.phone === phone && patient.dateOfBirth === dateOfBirth,
  );
}

/** Một hồ sơ → SINGLE; nhiều → MULTIPLE không kèm danh sách; không có → NONE. */
function outcome(candidates: MockPatient[]): PatientLookupResponse {
  if (candidates.length === 0) return { result: "NONE", matches: [] };
  if (candidates.length === 1)
    return { result: "SINGLE", matches: candidates.map(toMatch) };
  return { result: "MULTIPLE", matches: [] };
}

/** BR-14: SĐT + ngày sinh; chỉ khi nhiều hồ sơ khớp mới cần họ tên để chọn đúng người. */
function resolveLookup(query: PatientLookupQuery): PatientLookupResponse {
  if ("patientCode" in query)
    return outcome(store.filter((p) => p.patientCode === query.patientCode));
  const candidates = samePhoneAndBirth(query.phone, query.dateOfBirth);
  const { fullName } = query;
  if (candidates.length <= 1 || fullName === undefined)
    return outcome(candidates);
  return outcome(candidates.filter((p) => sameName(p.fullName, fullName)));
}

/** Trùng: SĐT + ngày sinh khớp một hồ sơ (bất kể tên), hoặc khớp nhiều và trùng họ tên. */
function isDuplicate(body: CreateWalkInPatientRequest): boolean {
  const candidates = samePhoneAndBirth(body.phone, body.dateOfBirth);
  if (candidates.length === 0) return false;
  if (candidates.length === 1) return true;
  return candidates.some((p) => sameName(p.fullName, body.fullName));
}

/** Mã bệnh nhân tiếp theo: số lớn nhất trong kho + 1, giữ dạng BN-000000. */
function nextPatientCode(): string {
  const highest = store.reduce(
    (max, p) => Math.max(max, Number(p.patientCode.slice(3))),
    0,
  );
  return `BN-${String(highest + 1).padStart(6, "0")}`;
}

function toApiFieldErrors(errors: PatientFieldErrors): ApiFieldError[] {
  return Object.keys(errors).map((field) => ({ field, code: "MSG-17" as const }));
}

function lookupParams(query: PatientLookupQuery) {
  return "patientCode" in query
    ? { patientCode: query.patientCode }
    : {
        phone: query.phone,
        dateOfBirth: query.dateOfBirth,
        fullName: query.fullName,
      };
}

/**
 * GET /api/v1/patients/lookup: `patientCode`, hoặc `phone` + `dateOfBirth` (+ `fullName` khi
 * lần trước trả MULTIPLE). Sai định dạng → 400 MSG-17 kèm `fieldErrors`.
 */
export async function lookupPatients(
  query: PatientLookupQuery,
  signal?: AbortSignal,
): Promise<PatientLookupResponse> {
  if (!USE_MOCK)
    return apiRequest<PatientLookupResponse>(
      `/api/v1/patients/lookup${toQueryString(lookupParams(query))}`,
      { signal },
    );
  await latency(signal);
  const errors = findLookupFieldErrors(query);
  if (Object.keys(errors).length > 0)
    mockFail(400, "MSG-17", undefined, toApiFieldErrors(errors));
  return clone(resolveLookup(query));
}

/**
 * POST /api/v1/patients (UC10): tạo hồ sơ vãng lai, không tài khoản. 201 → hồ sơ mới.
 * Trùng hồ sơ đã có → 409 MSG-18; sai dữ liệu → 400 MSG-17 kèm `fieldErrors`.
 */
export async function createWalkInPatient(
  body: CreateWalkInPatientRequest,
  signal?: AbortSignal,
): Promise<PatientMatch> {
  if (!USE_MOCK)
    return apiRequest<PatientMatch>("/api/v1/patients", {
      method: "POST",
      body: JSON.stringify(body),
      signal,
    });
  await latency(signal);
  const errors = findWalkInFieldErrors(body);
  if (Object.keys(errors).length > 0)
    mockFail(400, "MSG-17", undefined, toApiFieldErrors(errors));
  if (isDuplicate(body)) mockFail(409, "MSG-18");
  const created: MockPatient = {
    id: `pat-new-${++sequence}`,
    patientCode: nextPatientCode(),
    fullName: normalizeFullName(body.fullName),
    dateOfBirth: body.dateOfBirth,
    sex: body.sex,
    hasAccount: false,
    phone: body.phone,
    address: body.address.trim(),
    citizenId: body.citizenId,
    email: body.email ?? null,
  };
  store.push(created);
  // Đăng ký vào kho giả dùng chung để màn Tiếp nhận tra được mã pat-new-N (BE thật không cần bước này).
  registerMockPatient({
    id: created.id,
    patientCode: created.patientCode,
    fullName: created.fullName,
    dateOfBirth: created.dateOfBirth,
    sex: created.sex,
    phone: created.phone,
  });
  return clone(toMatch(created));
}
