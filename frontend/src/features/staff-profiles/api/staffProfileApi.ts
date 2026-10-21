import type { PageQuery, PageResponse } from "@/lib/api/pagination";
import { clone, mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import { validateStaffProfileInput } from "../schema/staffProfileFieldRules";
import type {
  EligibleStaffAccount,
  FeaturedDoctor,
  StaffProfileAction,
  StaffProfileDetail,
  StaffProfileFilters,
  StaffProfileInput,
  StaffProfileListItem,
} from "../types/staffProfile";
import { buildStaffProfileFormData } from "./staffProfileFormData";
import {
  mockStore,
  USERNAME_PREFIX_SERVICE_ERROR,
  USERNAME_WITH_FUTURE_APPOINTMENTS,
  type MockStaffRecord,
} from "./staffProfileMockData";

// LỚP API GIẢ cho /api/v1/admin/staff-profiles. Khi nối BE thật, giữ chữ ký hàm và
// thay thân bằng apiRequest (tạo/sửa gửi multipart qua buildStaffProfileFormData).

const DELAY_MS = 300;

function allowedActions(record: MockStaffRecord): StaffProfileAction[] {
  if (record.status !== "ACTIVE") return [];
  return record.doctor
    ? ["edit", "remove", "togglePublic"]
    : ["edit", "remove"];
}

function toListItem(record: MockStaffRecord): StaffProfileListItem {
  return {
    id: record.id,
    accountId: record.accountId,
    username: record.username,
    role: record.role,
    displayName: record.displayName,
    photoUrl: record.photoUrl,
    workContact: record.workContact,
    status: record.status,
    isPublic: record.doctor ? record.doctor.isPublic : null,
    allowedActions: allowedActions(record),
  };
}

function toDetail(record: MockStaffRecord): StaffProfileDetail {
  const isLive = record.doctor?.isPublic && record.status === "ACTIVE";
  return {
    id: record.id,
    accountId: record.accountId,
    username: record.username,
    role: record.role,
    displayName: record.displayName,
    photoUrl: record.photoUrl,
    workContact: record.workContact,
    status: record.status,
    allowedActions: allowedActions(record),
    doctor: record.doctor ? clone(record.doctor) : null,
    publicUrl: isLive ? `/bac-si/${record.id}` : null,
  };
}

function findRecord(id: string): MockStaffRecord {
  const record = mockStore.profiles.find((item) => item.id === id);
  if (!record) mockFail(404, "MSG-23");
  return record;
}

function failOnFieldErrors(
  input: StaffProfileInput,
  role: MockStaffRecord["role"],
) {
  const errors = validateStaffProfileInput(
    { ...input, doctor: input.doctor },
    role,
  );
  const fieldErrors = Object.entries(errors).map(([field, code]) => ({
    field,
    code: code!,
  }));
  if (fieldErrors.length > 0) mockFail(400, "MSG-22", undefined, fieldErrors);
}

/** Mock đọc part `data` đúng như BE: Blob JSON. */
async function readInput(form: FormData): Promise<StaffProfileInput> {
  const part = form.get("data");
  if (!(part instanceof Blob)) mockFail(400, "MSG-22");
  return JSON.parse(await part.text()) as StaffProfileInput;
}

export async function listStaffProfiles(
  query: StaffProfileFilters & PageQuery,
  signal?: AbortSignal,
): Promise<PageResponse<StaffProfileListItem>> {
  await mockDelay(DELAY_MS, signal);
  const q = query.q.trim().toLowerCase();
  const items = mockStore.profiles
    .filter(
      (p) =>
        !q ||
        p.displayName.toLowerCase().includes(q) ||
        p.username.toLowerCase().includes(q),
    )
    .filter((p) => !query.role || p.role === query.role)
    .filter((p) => !query.status || p.status === query.status)
    // `public` chỉ khớp bác sĩ: nhân viên khác bị loại khi có bộ lọc này.
    .filter(
      (p) =>
        !query.isPublic ||
        (p.doctor !== null &&
          p.doctor.isPublic === (query.isPublic === "true")),
    )
    .sort((a, b) => a.displayName.localeCompare(b.displayName, "vi"))
    .map(toListItem);
  return paginate(items, query);
}

export async function getStaffProfile(
  id: string,
  signal?: AbortSignal,
): Promise<StaffProfileDetail> {
  await mockDelay(DELAY_MS / 2, signal);
  return toDetail(findRecord(id));
}

export async function listEligibleStaffAccounts(
  q?: string,
  signal?: AbortSignal,
): Promise<EligibleStaffAccount[]> {
  await mockDelay(DELAY_MS / 2, signal);
  const text = (q ?? "").trim().toLowerCase();
  return clone(
    mockStore.accounts.filter(
      (a) =>
        !text ||
        a.fullName.toLowerCase().includes(text) ||
        a.username.toLowerCase().includes(text),
    ),
  );
}

export async function createStaffProfile(
  input: StaffProfileInput,
  photo?: File,
): Promise<StaffProfileDetail> {
  // Dựng multipart như khi gọi BE thật, rồi đọc lại part `data`.
  const body = await readInput(buildStaffProfileFormData(input, photo));
  await mockDelay(DELAY_MS);
  const account = mockStore.accounts.find(
    (a) => a.accountId === body.accountId,
  );
  if (!account) mockFail(409, "MSG-23");
  failOnFieldErrors(body, account.role);
  if (account.username.startsWith(USERNAME_PREFIX_SERVICE_ERROR))
    mockFail(500, "MSG-04");
  const record: MockStaffRecord = {
    id: crypto.randomUUID(),
    accountId: account.accountId,
    username: account.username,
    role: account.role,
    displayName: body.displayName.trim(),
    photoUrl: null, // mock không lưu file ảnh
    workContact: body.workContact.trim(),
    status: "ACTIVE",
    doctor: account.role === "ORTHOPEDIC_DOCTOR" ? (body.doctor ?? null) : null,
  };
  mockStore.profiles.push(record);
  mockStore.accounts = mockStore.accounts.filter(
    (a) => a.accountId !== account.accountId,
  );
  return toDetail(record);
}

export async function updateStaffProfile(
  id: string,
  input: Omit<StaffProfileInput, "accountId">,
  photo?: File,
): Promise<StaffProfileDetail> {
  const body = await readInput(buildStaffProfileFormData(input, photo));
  await mockDelay(DELAY_MS);
  const record = findRecord(id);
  if (record.status !== "ACTIVE") mockFail(409, "MSG-23");
  failOnFieldErrors(body, record.role);
  if (record.username.startsWith(USERNAME_PREFIX_SERVICE_ERROR))
    mockFail(500, "MSG-04");
  record.displayName = body.displayName.trim();
  record.workContact = body.workContact.trim();
  if (record.role === "ORTHOPEDIC_DOCTOR" && body.doctor)
    record.doctor = body.doctor;
  return toDetail(record);
}

export async function removeStaffProfile(id: string): Promise<void> {
  await mockDelay(DELAY_MS);
  const record = findRecord(id);
  if (record.status !== "ACTIVE") mockFail(409, "MSG-23");
  if (record.username === USERNAME_WITH_FUTURE_APPOINTMENTS)
    mockFail(409, "MSG-36");
  record.status = "REMOVED";
}

/** Trang chủ công khai: chỉ bác sĩ Active và đã bật công khai. */
export async function listFeaturedDoctors(
  limit = 4,
): Promise<FeaturedDoctor[]> {
  await mockDelay(80);
  return mockStore.profiles
    .filter((p) => p.status === "ACTIVE" && p.doctor?.isPublic)
    .slice(0, limit)
    .map((p) => ({
      id: p.id,
      displayName: p.displayName,
      photoUrl: p.photoUrl,
      specialties: [...(p.doctor?.specialties ?? [])],
      qualifications: p.doctor?.qualifications ?? "",
    }));
}
