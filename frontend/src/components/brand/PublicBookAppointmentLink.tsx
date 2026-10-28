"use client";

import Link from "next/link";
import { useEffect, useState, type ReactNode } from "react";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { getSupabaseBrowserClient } from "@/lib/supabase/browser";

// Nút "Đặt lịch khám" ở khu công khai. Khách chưa đăng nhập đi qua đăng nhập rồi quay lại
// màn đặt lịch; người đã có phiên đi thẳng tới màn đặt lịch. Chỉ đổi đích sau khi đọc phiên
// trên trình duyệt, nên lần đầu luôn là liên kết đăng nhập (an toàn).
const LOGIN_THEN_BOOK = `/login?next=${PATIENT_ROUTES.newAppointment}`;

async function hasSignedInSession(): Promise<boolean> {
  const { data } = await getSupabaseBrowserClient().auth.getSession();
  return data.session !== null;
}

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
    hasSignedInSession()
      .then((signedIn) => {
        if (active && signedIn) setHref(PATIENT_ROUTES.newAppointment);
      })
      // Không đọc được phiên (thiếu cấu hình, lỗi trình duyệt): giữ liên kết đăng nhập và ghi log.
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
