"use client";

import { CalendarPlus } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { OtcmsLogo } from "@/components/brand/OtcmsLogo";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { ROLE_LABELS } from "@/lib/domain/clinicRoles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import { useCurrentPatientAccount } from "@/hooks/useCurrentPatientAccount";
import {
  PATIENT_ACCOUNT_LINKS,
  PATIENT_NAV_ITEMS,
  groupPatientNavItems,
} from "../patientNavigation";
import { PatientAccountMenu } from "./PatientAccountMenu";
import { PatientPortalNav } from "./PatientPortalNav";

// Thanh đầu khu bệnh nhân: logo về trang chủ, menu nhóm, ô tài khoản và nút "Đặt lịch
// khám". Màn hẹp: nút đặt lịch xuống hàng riêng, rộng hết chiều ngang (cao 48px). Ẩn nút
// đặt lịch ở chính trang đặt lịch. Chuông thông báo (`notificationSlot`, do lớp ghép truyền vào)
// đặt giữa menu và tài khoản. Người đang đăng nhập lấy từ phiên giả, chỉ để hiển thị.
// Mỗi màn chỉ một nút default: trang chủ và chi tiết lượt khám có nút chính riêng (ví dụ "Xem
// tiến độ" hoặc "Thanh toán các chỉ định"), nên nút đặt lịch ở đầu trang là outline ở đó.
export function PatientPortalHeader({
  notificationSlot,
}: {
  notificationSlot?: ReactNode;
}) {
  const pathname = usePathname();
  const { account } = useCurrentPatientAccount();
  const showBookButton = pathname !== PATIENT_ROUTES.newAppointment;
  // Trang có nút chính riêng (trang chủ, chi tiết lượt khám, form viết đánh giá, thông báo):
  // nút đặt lịch đổi sang outline để mỗi màn chỉ có một nút default.
  const pageHasOwnPrimary =
    pathname === PATIENT_ROUTES.portal ||
    pathname.startsWith(`${PATIENT_ROUTES.visits}/`) ||
    pathname.startsWith(`${PATIENT_ROUTES.reviews}/new`) ||
    pathname === PATIENT_ROUTES.notifications;

  return (
    <header className="border-b bg-background">
      <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-x-3 gap-y-3 px-4 py-3 sm:h-16 sm:flex-nowrap sm:py-0 md:px-6">
        <OtcmsLogo tone="onLight" />

        <div className="ml-auto flex items-center gap-1">
          <PatientPortalNav groups={groupPatientNavItems(PATIENT_NAV_ITEMS)} />
          {notificationSlot}
          <PatientAccountMenu
            displayName={account?.displayName || "Tài khoản"}
            email={account?.email}
            links={PATIENT_ACCOUNT_LINKS}
            roleLabel={ROLE_LABELS.PATIENT}
          />
        </div>

        {showBookButton ? (
          <Link
            className={cn(
              publicButtonClass(pageHasOwnPrimary ? "secondary" : "primary"),
              "order-last w-full sm:order-none sm:w-auto",
            )}
            href={PATIENT_ROUTES.newAppointment}
          >
            <CalendarPlus aria-hidden className="max-sm:hidden" />
            Đặt lịch khám
          </Link>
        ) : null}
      </div>
    </header>
  );
}

