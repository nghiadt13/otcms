"use client";

import { CalendarPlus } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { OtcmsLogo } from "@/components/brand/OtcmsLogo";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { useCurrentAccount } from "@/hooks/useCurrentAccount";
import { ROLE_LABELS } from "@/lib/domain/clinicRoles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import {
  PATIENT_ACCOUNT_LINKS,
  PATIENT_NAV_ITEMS,
  groupPatientNavItems,
} from "../patientNavigation";
import { PatientAccountMenu } from "./PatientAccountMenu";
import { PatientPortalNav, type PatientSiteLink } from "./PatientPortalNav";

// Hàng menu chính khu bệnh nhân, cùng khung với SiteHeader của trang chủ (dính trên cùng, nền
// trắng, bóng nhẹ, container max-w-7xl, cao 64/80px): logo, menu nhóm bên trái; chuông và ô
// tài khoản bên phải. Từ lg nút "Đặt lịch khám" nằm ở dải SiteTopBar ngay trên (layout ghép);
// dưới lg nút nằm ở đây, màn hẹp xuống hàng riêng rộng hết chiều ngang (cao 48px). Ẩn nút
// đặt lịch ở chính trang đặt lịch. Tên người đang đăng nhập lấy từ backend (`GET /api/v1/me`).
// Mỗi màn chỉ một nút default: trang chủ và chi tiết lượt khám có nút chính riêng (ví dụ "Xem
// tiến độ" hoặc "Thanh toán các chỉ định"), nên nút đặt lịch ở đầu trang là outline ở đó.
export function PatientPortalHeader({
  notificationSlot,
  siteLinks,
}: {
  notificationSlot?: ReactNode;
  /** Link trang giới thiệu (cùng danh sách menu trang chủ), do layout truyền vào. */
  siteLinks?: PatientSiteLink[];
}) {
  const pathname = usePathname();
  const current = useCurrentAccount();
  // Tên từ backend; trong lúc chờ (hiếm: cổng đã xác nhận trước khi vẽ header) dùng nhãn chung.
  const displayName =
    current.status === "signedIn" ? current.account.displayName : "Tài khoản";
  // Khách chưa đăng nhập chỉ vào được màn đặt lịch: thay menu khu bệnh nhân bằng Đăng nhập / Đăng ký.
  const guest = current.status === "anonymous";
  const showBookButton = pathname !== PATIENT_ROUTES.newAppointment;
  // Trang có nút chính riêng (chi tiết lượt khám, form viết đánh giá, thông báo):
  // nút đặt lịch đổi sang outline để mỗi màn chỉ có một nút default.
  const pageHasOwnPrimary =
    pathname.startsWith(`${PATIENT_ROUTES.visits}/`) ||
    pathname.startsWith(`${PATIENT_ROUTES.reviews}/new`) ||
    pathname === PATIENT_ROUTES.notifications;

  return (
    <header className="sticky top-0 z-40 border-b bg-background/95 shadow-sm shadow-foreground/5 backdrop-blur">
      <div
        className={`${publicContainerClass} flex flex-wrap items-center gap-x-3 gap-y-3 py-3 sm:h-16 sm:flex-nowrap sm:py-0 lg:h-20`}
      >
        <OtcmsLogo tone="onLight" />

        {guest ? (
          <div className="ml-auto flex items-center gap-2">
            <Link className={publicButtonClass("secondary")} href="/login">
              Đăng nhập
            </Link>
            <Link className={publicButtonClass("secondary")} href="/register">
              Đăng ký
            </Link>
          </div>
        ) : (
          <>
            <div className="max-xl:ml-auto xl:ml-12 xl:h-full">
              <PatientPortalNav
                groups={groupPatientNavItems(PATIENT_NAV_ITEMS)}
                siteLinks={siteLinks}
              />
            </div>

            <div className="flex items-center gap-2 xl:ml-auto">
              {notificationSlot}
              <PatientAccountMenu
                displayName={displayName}
                links={PATIENT_ACCOUNT_LINKS}
                roleLabel={ROLE_LABELS.PATIENT}
              />
            </div>
          </>
        )}

        {showBookButton ? (
          <Link
            className={cn(
              publicButtonClass(pageHasOwnPrimary ? "secondary" : "primary"),
              "order-last w-full sm:order-none sm:w-auto lg:hidden",
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
