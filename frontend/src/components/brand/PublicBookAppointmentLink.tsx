"use client";

import Link from "next/link";
import { useEffect, useState, type ReactNode } from "react";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { getSession } from "@/lib/auth/authSession";

const LOGIN_THEN_BOOK = `/login?next=${PATIENT_ROUTES.newAppointment}`;

// Nút "Đặt lịch khám" ở khu công khai: luôn dẫn tới màn đặt lịch. Khách chưa đăng nhập dừng ở
// bước 1 của màn đó (đăng nhập hoặc đăng ký rồi quay lại), nên không cần rẽ nhánh theo phiên ở đây.
export function PublicBookAppointmentLink({
  className,
  children,
}: {
  className?: string;
  children: ReactNode;
}) {
  const [href, setHref] = useState(LOGIN_THEN_BOOK);

  useEffect(() => {
    let active = true;
    getSession()
      .then((session) => {
        if (active && session) setHref(PATIENT_ROUTES.newAppointment);
      })
      .catch((error: unknown) => {
        console.error("Không đọc được phiên đăng nhập để đặt lịch", error);
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <Link className={className} href={href}>
      {children}
    </Link>
  );
}
