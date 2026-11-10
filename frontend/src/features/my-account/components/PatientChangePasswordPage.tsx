"use client";

import { useRouter } from "next/navigation";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { ChangePasswordForm } from "./ChangePasswordForm";

// Đổi mật khẩu của bệnh nhân (UC05, BR-06): nhập mật khẩu hiện tại rồi đặt mật khẩu mới.
// Hủy thì về màn Tài khoản. Đổi xong: BE đã thu hồi phiên nên về /login để đăng nhập lại bằng mật
// khẩu mới (không về màn Tài khoản vì phiên cũ không còn dùng được). Dùng replace để nút Back
// không quay lại form đã xóa ô nhập.
export function PatientChangePasswordPage() {
  const router = useRouter();
  const backToAccount = () => router.replace(PATIENT_ROUTES.account);

  return (
    <>
      <PortalPageHeader
        title="Đổi mật khẩu"
        description="Nhập mật khẩu hiện tại, rồi đặt mật khẩu mới."
      />
      <ChangePasswordForm
        large
        mode="voluntary"
        onCancel={backToAccount}
        onDone={({ sessionEnded }) =>
          sessionEnded ? router.replace("/login") : backToAccount()
        }
      />
    </>
  );
}
