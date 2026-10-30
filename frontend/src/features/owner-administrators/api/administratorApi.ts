import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";

export type Administrator = {
  accountId: string;
  username: string;
  email: string | null;
  accountType: "ADMIN";
  status: string;
  createdAt: string;
  updatedAt: string;
};
export type AdministratorPage = {
  content: Administrator[];
  totalElements: number;
  totalPages: number;
};
const base = "/api/v1/owner/administrator-accounts" as const;
export type IssuedAdministratorPassword = {
  accountId: string;
  temporaryPassword: string;
  mustChangePassword: boolean;
};
export const createAdministrator = (username: string) =>
  apiRequest<IssuedAdministratorPassword & { username: string }>(base, {
    method: "POST",
    body: JSON.stringify({ username }),
  });
export const reissueAdministratorPassword = (id: string) =>
  apiRequest<IssuedAdministratorPassword>(
    `${base}/${encodeURIComponent(id)}/password-reissues`,
    { method: "POST" },
  );
export function listAdministrators(
  query: string,
  status: string,
  page: number,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({ query, page: String(page), size: "10" });
  if (status) params.set("status", status);
  return apiRequest<AdministratorPage>(`${base}?${params}`, { signal });
}
export const getAdministrator = (id: string, signal?: AbortSignal) =>
  apiRequest<Administrator>(`${base}/${encodeURIComponent(id)}`, { signal });
export const changeAdministratorStatus = (
  id: string,
  action: "activate" | "deactivate",
) =>
  apiRequest<Administrator>(`${base}/${encodeURIComponent(id)}/${action}`, {
    method: "POST",
  });
export function administratorError(error: unknown, context?: "search") {
  if (error instanceof ApiError) {
    if (error.errorCode === "USERNAME_ALREADY_EXISTS")
      return "Tên đăng nhập này đã được sử dụng. Vui lòng chọn tên đăng nhập khác.";
    if (error.status === 401)
      return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
    if (error.status === 403 || error.status === 404)
      return "Bạn không có quyền thực hiện thao tác này hoặc tài khoản không còn khả dụng.";
    if (error.status === 409)
      return "Không thể thực hiện thao tác này với trạng thái hiện tại.";
    if (error.status === 400)
      return context === "search"
        ? "Tiêu chí tìm kiếm không hợp lệ. Vui lòng kiểm tra lại."
        : "Thông tin không hợp lệ. Vui lòng kiểm tra và nhập lại.";
  }
  return "Không thể hoàn tất yêu cầu. Hãy tải lại trạng thái trước khi thử lại.";
}
export const statusLabel = (status: string) =>
  ({
    ACTIVE: "Đang hoạt động",
    DISABLED: "Đã vô hiệu hóa",
    PENDING_VERIFICATION: "Chờ xác minh",
    TEMPORARILY_LOCKED: "Tạm khóa",
    ARCHIVED: "Đã lưu trữ",
  })[status] ?? status;
