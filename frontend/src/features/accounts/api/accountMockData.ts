import type {
  AccountAction,
  AccountDetail,
  AccountStatus,
  StaffProfileSummary,
} from "../types/account";
import type { StaffRole } from "@/lib/domain/clinicRoles";

// Kho dữ liệu giả trong bộ nhớ (~45 tài khoản): mỗi vai trò nhân viên >= 1, có nhân viên
// chưa có hồ sơ, có nhân viên bị vô hiệu hóa, ~35 bệnh nhân đủ hai trạng thái.
// Không lưu mật khẩu: mật khẩu khởi tạo chỉ tồn tại trong phản hồi tạo/cấp lại.

/** Hành động mà BE thật sẽ trả; mock tính lại sau mỗi lần đổi trạng thái. */
export function computeAllowedActions(
  account: Pick<AccountDetail, "accountType" | "status" | "staffProfile">,
): AccountAction[] {
  const actions: AccountAction[] = [
    account.status === "ACTIVE" ? "deactivate" : "activate",
  ];
  if (account.accountType !== "PATIENT") {
    actions.push("reissuePassword");
    if (!account.staffProfile) actions.push("createStaffProfile");
  }
  return actions;
}

function build(
  partial: Omit<AccountDetail, "allowedActions" | "email" | "username"> & {
    username?: string;
    email?: string;
  },
): AccountDetail {
  const account = {
    ...partial,
    username: partial.username ?? null,
    email: partial.email ?? null,
  };
  return { ...account, allowedActions: computeAllowedActions(account) };
}

const profile = (
  fullName: string,
  role: StaffRole,
  workContact: string | null,
): StaffProfileSummary => ({ fullName, role, workContact });

const staffSeed = (): AccountDetail[] => [
  build({
    id: "3f2a9c1e-7b4d-4e8a-9c2f-1a2b3c4d5e6f",
    accountType: "RECEPTIONIST",
    username: "letan.an",
    fullName: "Nguyễn Thị An",
    status: "ACTIVE",
    createdAt: "2026-10-01T09:00:00+07:00",
    staffProfile: profile("Nguyễn Thị An", "RECEPTIONIST", "0901 234 567"),
  }),
  build({
    id: "5b1d7e20-3c4f-4a6b-8d9e-0f1a2b3c4d5e",
    accountType: "ORTHOPEDIC_DOCTOR",
    username: "trung",
    fullName: "Lê Quang Trung",
    status: "ACTIVE",
    createdAt: "2026-09-28T08:15:00+07:00",
    staffProfile: profile("Lê Quang Trung", "ORTHOPEDIC_DOCTOR", "0912 345 678"),
  }),
  build({
    id: "9a8b7c6d-5e4f-4a3b-9c2d-1e0f9a8b7c6d",
    accountType: "IMAGING_TECHNICIAN",
    username: "kttd.cuong",
    fullName: null,
    status: "INACTIVE",
    createdAt: "2026-10-02T10:30:00+07:00",
    staffProfile: null,
  }),
  build({
    id: "2d7f4a9e-1c3b-4f6a-9e8d-7b6a5c4d3e2f",
    accountType: "LAB_TECHNICIAN",
    username: "ktxn.dung",
    fullName: null,
    status: "ACTIVE",
    createdAt: "2026-10-02T08:30:00+07:00",
    staffProfile: null,
  }),
  build({
    id: "7c6d5e4f-3a2b-4c1d-8e9f-0a1b2c3d4e5f",
    accountType: "ORTHOPEDIC_DOCTOR",
    username: "bacsi.binh",
    fullName: "Phạm Văn Bình",
    status: "INACTIVE",
    createdAt: "2026-09-20T14:00:00+07:00",
    staffProfile: profile("Phạm Văn Bình", "ORTHOPEDIC_DOCTOR", null),
  }),
  build({
    id: "1e2f3a4b-5c6d-4e7f-8a9b-0c1d2e3f4a5b",
    accountType: "RECEPTIONIST",
    username: "letan.hoa",
    fullName: "Đặng Thị Hoa",
    status: "ACTIVE",
    createdAt: "2026-09-25T09:45:00+07:00",
    staffProfile: profile("Đặng Thị Hoa", "RECEPTIONIST", "0933 111 222"),
  }),
];

const PATIENT_NAMES = [
  "Trần Thị Lan", "Nguyễn Văn Hải", "Lê Thị Mai", "Phạm Quốc Khánh", "Hoàng Thị Yến",
  "Vũ Minh Đức", "Đỗ Thị Hạnh", "Bùi Văn Nam", "Ngô Thị Thu", "Dương Văn Phúc",
  "Lý Thị Ngọc", "Trịnh Văn Long", "Phan Thị Hà", "Đinh Quang Vinh", "Mai Thị Linh",
  "Cao Văn Sơn", "Tạ Thị Hương", "Hồ Minh Tuấn", "Lương Thị Oanh", "Võ Văn Thắng",
  "Châu Thị Nga", "Đoàn Văn Kiên", "Kiều Thị Loan", "La Văn Tài", "Nghiêm Thị Thảo",
  "Quách Văn Dũng", "Tôn Thị Bích", "Thái Văn Hòa", "Âu Thị Diệp", "Lại Văn Quý",
  "Từ Thị Phượng", "Giang Văn Bảo", "Hà Thị Trang", "Ông Văn Thịnh", "Sầm Thị Nhung",
];

const slug = (name: string) =>
  name
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/gi, "d")
    .toLowerCase()
    .replace(/\s+/g, ".");

const hex = (n: number, width: number) => n.toString(16).padStart(width, "0");

const patientSeed = (): AccountDetail[] =>
  PATIENT_NAMES.map((name, index) =>
    build({
      id: `${hex(0x8d4e1b2c + index, 8)}-5f60-4a71-8b2d-${hex(index + 1, 12)}`,
      accountType: "PATIENT",
      email: index === 0 ? "lan.tran@mail.vn" : `${slug(name)}@mail.vn`,
      fullName: name,
      // Cứ 6 tài khoản thì 1 tài khoản bị vô hiệu hóa.
      status: (index % 6 === 5 ? "INACTIVE" : "ACTIVE") as AccountStatus,
      createdAt: `2026-10-${String(1 + (index % 19)).padStart(2, "0")}T14:20:00+07:00`,
      staffProfile: null,
    }),
  );

export const buildAccountSeed = (): AccountDetail[] => [
  ...staffSeed(),
  ...patientSeed(),
];

let store: AccountDetail[] = buildAccountSeed();

export const accountStore = () => store;
export const resetAccountMockStore = () => {
  store = buildAccountSeed();
};
export const NON_EXISTENT_ACCOUNT_ID = "00000000-0000-4000-8000-000000000000";
