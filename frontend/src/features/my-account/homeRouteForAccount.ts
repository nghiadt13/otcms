import { ADMIN_ROUTES } from "@/lib/routes/adminRoutes";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { STAFF_ROUTES } from "@/lib/routes/staffRoutes";
import type { MyAccount } from "./types/myAccount";

// Khu làm việc đúng vai trò của tài khoản, lối vào chung sau đăng nhập (thay trang /workspace).
// Hàm thuần: bệnh nhân vào cổng bệnh nhân; quản trị và chủ phòng khám vào quản trị; còn lại
// là nhân viên, vào khu lễ tân. Quyền thật vẫn do backend kiểm từng thao tác.
export function homeRouteForAccount(account: MyAccount): string {
  if (account.kind === "PATIENT") return PATIENT_ROUTES.portal;
  if (account.role === "ADMIN" || account.role === "CLINIC_OWNER") {
    return ADMIN_ROUTES.dashboard;
  }
  return STAFF_ROUTES.home;
}
