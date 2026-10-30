import Link from "next/link";
import type { ReactNode } from "react";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";

// Nút "Đặt lịch khám" ở khu công khai: luôn dẫn tới màn đặt lịch. Khách chưa đăng nhập dừng ở
// bước 1 của màn đó (đăng nhập hoặc đăng ký rồi quay lại), nên không cần rẽ nhánh theo phiên ở đây.
export function PublicBookAppointmentLink({
  className,
  children,
}: {
  className?: string;
  children: ReactNode;
}) {
  return (
    <Link className={className} href={PATIENT_ROUTES.newAppointment}>
      {children}
    </Link>
  );
}
