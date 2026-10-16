export type CurrentAdmin = {
  name: string;
  email: string;
  roleLabel: string;
  avatarUrl?: string;
};

// DỮ LIỆU GIẢ: chưa có endpoint "tài khoản hiện tại". Khi backend có (ví dụ
// GET /api/v1/accounts/me) thì đọc từ đó; chỉ AdminAccountMenu dùng giá trị này.
export const currentAdmin: CurrentAdmin = {
  name: "Admin OTCMS",
  email: "admin@otcms.local",
  roleLabel: "Quản trị viên",
};

/** Chữ cái đầu hiển thị khi chưa có ảnh đại diện. */
export function getInitials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}
