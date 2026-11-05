// Backend giả cho test màn Tài khoản: thay `apiRequest` ở ranh giới HTTP và trả ĐÚNG dạng JSON
// của BE thật, để test chạy qua phần chuyển dữ liệu trong accountApi.ts.
//  - Bệnh nhân (/admin/accounts): PatientAccountAdminResponse.
//  - Nhân viên (/admin/staff-accounts, KAN-27): StaffAccountDtos.Item/Created/Reissued.
import { ApiError, type MsgCode } from "@/lib/api/apiError";

export const NON_EXISTENT_ACCOUNT_ID = "00000000-0000-4000-8000-000000000000";

type Status = "ACTIVE" | "INACTIVE";
type PatientRow = {
  id: string;
  accountType: "PATIENT";
  username: null;
  email: string;
  fullName: string;
  status: Status;
  createdAt: string;
  allowedActions: string[];
  staffProfile: null;
};
type StaffRow = {
  accountId: string;
  username: string;
  role: string;
  status: Status;
  mustChangePassword: boolean;
  createdAt: string;
  staffProfile: { id: string; displayName: string } | null;
};

const PATIENT_COUNT = 45;
let patients: PatientRow[] = [];
let staff: StaffRow[] = [];

const uuid = (n: number) =>
  `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`;
const fail = (
  status: number,
  code: MsgCode,
  fieldErrors?: { field: string; code: MsgCode }[],
) => {
  throw new ApiError(status, { code, fieldErrors });
};

export function resetFakeAccountBackend() {
  patients = Array.from({ length: PATIENT_COUNT }, (_, i) => ({
    id: uuid(100 + i),
    accountType: "PATIENT" as const,
    username: null,
    email: i === 0 ? "lan.tran@mail.vn" : `benhnhan${i}@mail.vn`,
    fullName: i === 0 ? "Trần Lan" : `Bệnh nhân ${i}`,
    status: (i % 7 === 3 ? "INACTIVE" : "ACTIVE") as Status,
    createdAt: new Date(
      Date.UTC(2026, 9, 30 - (i % 28), 2, 0, i),
    ).toISOString(),
    allowedActions: [i % 7 === 3 ? "activate" : "deactivate"],
    staffProfile: null,
  }));
  const member = (
    n: number,
    username: string,
    role: string,
    status: Status,
    name: string | null,
  ): StaffRow => ({
    accountId: uuid(n),
    username,
    role,
    status,
    mustChangePassword: false,
    createdAt: new Date(Date.UTC(2026, 9, 1, 2, 0, n)).toISOString(),
    staffProfile: name ? { id: uuid(n + 900), displayName: name } : null,
  });
  staff = [
    member(1, "letan.an", "RECEPTIONIST", "ACTIVE", "Nguyễn Thị An"),
    member(2, "trung", "ORTHOPEDIC_DOCTOR", "ACTIVE", "Trần Văn Trung"),
    member(3, "kttd.cuong", "IMAGING_TECHNICIAN", "INACTIVE", null),
    member(4, "ktxn.dung", "LAB_TECHNICIAN", "ACTIVE", "Phạm Dũng"),
  ];
}
resetFakeAccountBackend();

/** Cho test trạng thái trống. */
export function clearFakeAccounts() {
  patients = [];
  staff = [];
}

function page<T>(items: T[], url: URL) {
  const size = Number(url.searchParams.get("size") ?? 20);
  const index = Number(url.searchParams.get("page") ?? 0);
  return {
    items: items.slice(index * size, (index + 1) * size),
    page: index,
    size,
    totalItems: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
  };
}

function checkQuery(url: URL) {
  if ((url.searchParams.get("q") ?? "").length > 100) fail(400, "MSG-54");
}
const matches = (url: URL, status: Status, fields: (string | null)[]) => {
  const wanted = url.searchParams.get("status");
  const q = (url.searchParams.get("q") ?? "").trim().toLowerCase();
  return (
    (!wanted || wanted === status) &&
    (!q || fields.some((f) => f?.toLowerCase().includes(q)))
  );
};
const newestFirst = <T extends { createdAt: string }>(rows: T[]) =>
  [...rows].sort((a, b) => b.createdAt.localeCompare(a.createdAt));

function toggle(status: Status, target: Status) {
  if (status === target) fail(409, "MSG-42");
  return target;
}

function patientRoute(method: string, rest: string[], url: URL) {
  if (rest.length === 0 && method === "GET") {
    checkQuery(url);
    return page(
      newestFirst(patients).filter((p) =>
        matches(url, p.status, [p.email, p.fullName]),
      ),
      url,
    );
  }
  const row = patients.find((p) => p.id === rest[0]) ?? fail(404, "MSG-14");
  if (rest.length === 1 && method === "GET") return row;
  if (rest[1] === "activation" || rest[1] === "deactivation") {
    row.status = toggle(
      row.status,
      rest[1] === "activation" ? "ACTIVE" : "INACTIVE",
    );
    row.allowedActions = [row.status === "ACTIVE" ? "deactivate" : "activate"];
    return row;
  }
  return fail(404, "MSG-14");
}

function staffRoute(
  method: string,
  rest: string[],
  url: URL,
  body: Record<string, string>,
) {
  if (rest.length === 0 && method === "GET") {
    checkQuery(url);
    const role = url.searchParams.get("role");
    return page(
      newestFirst(staff).filter(
        (s) =>
          (!role || s.role === role) &&
          matches(url, s.status, [
            s.username,
            s.staffProfile?.displayName ?? null,
          ]),
      ),
      url,
    );
  }
  if (rest.length === 0 && method === "POST") {
    if (body.username === "khong.quyen") fail(403, "MSG-14");
    if (body.username === "loi.tao") fail(500, "MSG-16");
    if (staff.some((s) => s.username === body.username))
      fail(409, "MSG-13", [{ field: "username", code: "MSG-13" }]);
    const row: StaffRow = {
      accountId: uuid(500 + staff.length),
      username: body.username,
      role: body.role,
      status: "ACTIVE",
      mustChangePassword: true,
      createdAt: new Date().toISOString(),
      staffProfile: null,
    };
    staff.push(row);
    // Created không có mật khẩu và không có createdAt/staffProfile.
    return {
      accountId: row.accountId,
      username: row.username,
      role: row.role,
      status: row.status,
      mustChangePassword: true,
    };
  }
  const row = staff.find((s) => s.accountId === rest[0]) ?? fail(404, "MSG-14");
  if (rest.length === 1 && method === "GET") return row;
  if (rest[1] === "activation" || rest[1] === "deactivation") {
    row.status = toggle(
      row.status,
      rest[1] === "activation" ? "ACTIVE" : "INACTIVE",
    );
    return row;
  }
  if (rest[1] === "password-reissues")
    return {
      accountId: row.accountId,
      temporaryPassword: `Tmp${Math.random().toString(36).slice(2, 11)}`,
      mustChangePassword: true,
    };
  return fail(404, "MSG-14");
}

export async function fakeAccountApiRequest<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const url = new URL(path, "http://backend.test");
  const [, , , , group, ...rest] = url.pathname.split("/"); // "", api, v1, admin, <group>, ...
  const method = init.method ?? "GET";
  const body =
    typeof init.body === "string"
      ? (JSON.parse(init.body) as Record<string, string>)
      : {};
  const result =
    group === "accounts"
      ? patientRoute(method, rest, url)
      : group === "staff-accounts"
        ? staffRoute(method, rest, url, body)
        : fail(404, "MSG-14");
  return structuredClone(result) as T;
}
