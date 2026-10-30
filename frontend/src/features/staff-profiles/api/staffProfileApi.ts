import type { PageQuery, PageResponse } from "@/lib/api/pagination";
import { toQueryString } from "@/lib/api/pagination";
import { apiRequest } from "@/lib/api/client";
import { apiBaseUrl } from "@/lib/api/apiBaseUrl";
import { ApiError } from "@/lib/api/apiError";
import type { StaffRole } from "@/lib/domain/clinicRoles";
import { clone, mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import { validateStaffProfileInput } from "../schema/staffProfileFieldRules";
import type {
  EligibleStaffAccount,
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

// Test dùng mock để giữ nguyên ranh giới API; môi trường chạy thật gọi backend.

const DELAY_MS = 300;
const USE_MOCK = process.env.NODE_ENV === "test";

function profileError(error: unknown): never {
  if (!(error instanceof ApiError)) throw error;
  const code = error.errorCode === "DOCTOR_HAS_FUTURE_APPOINTMENTS"
    ? "MSG-36"
    : error.status === 404 || error.status === 409 ? "MSG-23"
      : error.status === 400 ? "MSG-22" : "MSG-04";
  throw new ApiError(error.status, { code, fieldErrors: error.fieldErrors });
}

function withPhotoUrl<T extends { photoUrl: string | null }>(record: T): T {
  return { ...record, photoUrl: record.photoUrl?.startsWith("/")
    ? `${apiBaseUrl}${record.photoUrl}` : record.photoUrl };
}

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
    acceptingNewBookings: record.doctor
      ? (record.acceptingNewBookings ?? false)
      : null,
    inActiveDirectory: record.doctor
      ? (record.inActiveDirectory ?? true)
      : null,
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
    acceptingNewBookings: record.doctor
      ? (record.acceptingNewBookings ?? false)
      : null,
    inActiveDirectory: record.doctor
      ? (record.inActiveDirectory ?? true)
      : null,
    allowedActions: allowedActions(record),
    doctor: record.doctor ? clone(record.doctor) : null,
    publicUrl: isLive ? `/doctors/${record.id}` : null,
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
  if (!USE_MOCK) {
    try {
      const params = toQueryString({ q: query.q, role: query.role, status: query.status,
        public: query.isPublic, page: query.page, size: query.size });
      const result = await apiRequest<PageResponse<StaffProfileListItem>>(
        `/api/v1/admin/staff-profiles${params}`, { signal });
      return { ...result, items: result.items.map(withPhotoUrl) };
    } catch (error) { profileError(error); }
  }
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
  if (!USE_MOCK) {
    try {
      return withPhotoUrl(await apiRequest<StaffProfileDetail>(
        `/api/v1/admin/staff-profiles/${encodeURIComponent(id)}`, { signal }));
    } catch (error) { profileError(error); }
  }
  await mockDelay(DELAY_MS / 2, signal);
  return toDetail(findRecord(id));
}

export async function setDoctorBookingAvailability(
  detail: StaffProfileDetail,
  acceptingNewBookings: boolean,
): Promise<StaffProfileDetail> {
  if (!detail.doctor) throw new Error("Chỉ bác sĩ mới có thể nhận đặt lịch.");
  if (!USE_MOCK) {
    try {
      await apiRequest(`/api/v1/admin/doctors/${encodeURIComponent(detail.id)}/directory-state`, {
        method: "PATCH",
        body: JSON.stringify({
          publiclyVisible: detail.doctor.isPublic,
          acceptingNewBookings,
          inActiveDirectory: detail.inActiveDirectory ?? true,
        }),
      });
      return getStaffProfile(detail.id);
    } catch (error) {
      profileError(error);
    }
  }
  await mockDelay(DELAY_MS);
  const record = findRecord(detail.id);
  record.acceptingNewBookings = acceptingNewBookings;
  return toDetail(record);
}

export async function listEligibleStaffAccounts(
  q?: string,
  signal?: AbortSignal,
): Promise<EligibleStaffAccount[]> {
  if (!USE_MOCK) {
    try {
      const list = await apiRequest<Array<{ id: string; username: string; fullName: string | null;
        role: "DOCTOR" | "RECEPTIONIST" | "LAB_TECHNICIAN" | "IMAGING_TECHNICIAN" }>>(
        "/api/v1/admin/staff-profiles/eligible-accounts", { signal });
      const term = q?.trim().toLowerCase() ?? "";
      return list.map((a) => ({ accountId: a.id, username: a.username,
        fullName: a.fullName ?? a.username,
        role: (a.role === "DOCTOR" ? "ORTHOPEDIC_DOCTOR" : a.role) as StaffRole }))
        .filter((a) => !term || a.username.toLowerCase().includes(term)
          || a.fullName.toLowerCase().includes(term));
    } catch (error) { profileError(error); }
  }
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
  if (!USE_MOCK) {
    try {
      return withPhotoUrl(await apiRequest<StaffProfileDetail>("/api/v1/admin/staff-profiles",
        { method: "POST", body: buildStaffProfileFormData(input, photo) }));
    } catch (error) { profileError(error); }
  }
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
    acceptingNewBookings: false,
    inActiveDirectory: true,
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
  if (!USE_MOCK) {
    try {
      return withPhotoUrl(await apiRequest<StaffProfileDetail>(
        `/api/v1/admin/staff-profiles/${encodeURIComponent(id)}`,
        { method: "PUT", body: buildStaffProfileFormData(input, photo) }));
    } catch (error) { profileError(error); }
  }
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
  if (!USE_MOCK) {
    try {
      await apiRequest<void>(`/api/v1/admin/staff-profiles/${encodeURIComponent(id)}`,
        { method: "DELETE" });
      return;
    } catch (error) { profileError(error); }
  }
  await mockDelay(DELAY_MS);
  const record = findRecord(id);
  if (record.status !== "ACTIVE") mockFail(409, "MSG-23");
  if (record.username === USERNAME_WITH_FUTURE_APPOINTMENTS)
    mockFail(409, "MSG-36");
  record.status = "REMOVED";
}
