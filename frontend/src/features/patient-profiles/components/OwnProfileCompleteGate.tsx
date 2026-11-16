"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { useOwnPatientProfile } from "../hooks/useOwnPatientProfile";

// Cổng của trang cần đủ thông tin cá nhân (đặt lịch khám): hồ sơ "Bản thân" chưa có hoặc còn thiếu
// CCCD thì đưa tới Thông tin cá nhân kèm `next` để điền xong quay lại đúng trang (MSG-06 hiện ở đó).
// Không đọc được hồ sơ thì không chặn: màn tự báo lỗi tải, quyền thật do backend giữ.
export function OwnProfileCompleteGate({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { state } = useOwnPatientProfile();
  const incomplete =
    state.status === "missing" ||
    (state.status === "ready" && !state.detail.citizenId?.trim());

  useEffect(() => {
    if (!incomplete) return;
    const next = `${pathname}${window.location.search}`;
    router.replace(
      `${PATIENT_ROUTES.account}?next=${encodeURIComponent(next)}`,
    );
  }, [incomplete, pathname, router]);

  if (state.status === "loading" || incomplete) {
    return (
      <div
        aria-busy="true"
        className="mx-auto grid max-w-md gap-3 px-4 py-16"
        role="status"
      >
        <span className="sr-only">Đang kiểm tra thông tin cá nhân</span>
        <Skeleton className="h-6 w-2/3" />
        <Skeleton className="h-12 w-full" />
      </div>
    );
  }
  return <>{children}</>;
}
