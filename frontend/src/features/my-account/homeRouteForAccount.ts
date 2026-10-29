import type { AccountType } from "@/lib/auth/authSession";
import { ADMIN_ROUTES } from "@/lib/routes/adminRoutes";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { STAFF_ROUTES } from "@/lib/routes/staffRoutes";

// Khu làm việc đúng vai trò của tài khoản (loại tài khoản do backend trả ở `GET /api/v1/me`),
// lối vào chung sau đăng nhập (thay trang /workspace). Hàm thuần: bệnh nhân vào cổng bệnh
// nhân; quản trị và chủ phòng khám vào quản trị; còn lại là nhân viên, vào khu lễ tân. Quyền
// thật vẫn do backend kiểm từng thao tác.
export function homeRouteForAccount(accountType: AccountType): string {
  if (accountType === "PATIENT") return PATIENT_ROUTES.account;
  if (accountType === "ADMIN" || accountType === "OWNER") {
    return ADMIN_ROUTES.dashboard;
  }
  return STAFF_ROUTES.home;
}
