import type { StatusTone } from "@/components/admin/StatusBadge";
import type { AccountListItem, AccountStatus } from "./types/account";

// Nhãn hiển thị dùng chung cho bảng và Sheet. Nhãn trạng thái cố ý không dùng "Tạm khóa"
// để khỏi nhầm với khóa 15 phút do nhập sai mật khẩu (tự động, không phải thao tác Admin).

export const STATUS_LABELS: Record<AccountStatus, string> = {
  ACTIVE: "Đang hoạt động",
  INACTIVE: "Đã vô hiệu hóa",
};

export const STATUS_TONES: Record<AccountStatus, StatusTone> = {
  ACTIVE: "success",
  INACTIVE: "neutral",
};

/** Nhân viên: tên đăng nhập. Bệnh nhân: email. */
export const accountIdentifier = (
  account: Pick<AccountListItem, "username" | "email">,
): string => account.username ?? account.email ?? "—";

/** Nhân viên chưa có hồ sơ thì nói rõ; bệnh nhân chưa có tên thì để gạch ngang. */
export const accountFullName = (
  account: Pick<AccountListItem, "fullName" | "accountType">,
): string =>
  account.fullName ??
  (account.accountType === "PATIENT" ? "—" : "Chưa có hồ sơ");
