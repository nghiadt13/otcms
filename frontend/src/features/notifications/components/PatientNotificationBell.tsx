"use client";

import Link from "next/link";
import { Bell } from "lucide-react";
import { UnreadCountBadge } from "@/components/shell/UnreadCountBadge";
import { buttonVariants } from "@/components/ui/button";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import { useUnreadNotificationCount } from "../hooks/useUnreadNotificationCount";

/**
 * Chuông ở header (trang chủ và khu bệnh nhân): mở thẳng trang thông báo, kèm số chưa đọc. Luôn
 * hiện với bệnh nhân đã đăng nhập, kể cả tài khoản mới chưa điền thông tin cá nhân (danh sách trống).
 */
export function PatientNotificationBell() {
  const unreadCount = useUnreadNotificationCount();

  return (
    <Link
      aria-label={
        unreadCount > 0 ? `Thông báo, ${unreadCount} chưa đọc` : "Thông báo"
      }
      className={cn(
        buttonVariants({ variant: "ghost", size: "icon" }),
        "relative size-11 motion-reduce:transition-none",
      )}
      href={PATIENT_ROUTES.notifications}
    >
      <Bell aria-hidden className="size-5" />
      <UnreadCountBadge count={unreadCount} className="top-1 right-0.5" />
    </Link>
  );
}
