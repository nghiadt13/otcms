"use client";

import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import {
  PersonalAreaShell,
  type PersonalAreaPerson,
} from "@/components/patient/PersonalAreaShell";
import { useMyAccount } from "@/features/my-account/hooks/useMyAccount";
import { SEX_LABELS } from "@/features/patient-profiles/components/profileOptions";
import { useOwnPatientProfile } from "@/features/patient-profiles/hooks/useOwnPatientProfile";
import { isoToDateOfBirthText } from "@/features/patient-profiles/schema/dateOfBirthText";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";

// Các màn gốc của menu "khu cá nhân" (cột trái của `PersonalAreaShell`). Đặt khung ở layout để
// bấm qua lại giữa các mục vẫn giữ nguyên menu trái, nội dung mới chỉ thay ở cột phải. Màn con
// (chi tiết, form đặt lịch, thanh toán...) vẫn dùng khung một cột như trước.
const AREA_PATHS: ReadonlySet<string> = new Set([
  PATIENT_ROUTES.account,
  PATIENT_ROUTES.profiles,
  PATIENT_ROUTES.appointments,
  PATIENT_ROUTES.results,
  PATIENT_ROUTES.prescriptions,
  PATIENT_ROUTES.payments,
  PATIENT_ROUTES.notifications,
]);

// Ghép hai nguồn ở đây vì feature không import nhau: tài khoản (ảnh, email) từ `my-account`,
// họ tên, giới tính, ngày sinh từ hồ sơ "Bản thân" của `patient-profiles`.
export function PortalAreaFrame({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  return AREA_PATHS.has(pathname) ? (
    <AreaShellWithPerson boxed={pathname !== PATIENT_ROUTES.account}>
      {children}
    </AreaShellWithPerson>
  ) : (
    children
  );
}

// `boxed`: màn danh sách tự không có thẻ nên được bọc một thẻ trắng ở cột phải; Thông tin cá nhân
// đã tự chia thẻ (nhiều thẻ xếp dọc) nên không bọc thêm.
function AreaShellWithPerson({
  boxed,
  children,
}: {
  boxed: boolean;
  children: ReactNode;
}) {
  const { account } = useMyAccount();
  const own = useOwnPatientProfile();

  // Tài khoản nhân viên không có khu này; màn con tự báo, không vẽ menu bệnh nhân.
  if (account?.kind === "STAFF") return <>{children}</>;

  const detail = own.state.status === "ready" ? own.state.detail : null;
  const person: PersonalAreaPerson = account
    ? {
        name: detail?.fullName ?? account.displayName,
        avatarUrl: account.avatarUrl,
        subtitle: detail
          ? `${SEX_LABELS[detail.sex]}, ${isoToDateOfBirthText(detail.dateOfBirth)}`
          : account.email,
      }
    : null;

  return (
    <PersonalAreaShell person={person}>
      {boxed ? (
        <div className="rounded-xl border bg-card p-5 sm:p-8">{children}</div>
      ) : (
        children
      )}
    </PersonalAreaShell>
  );
}
