"use client";

import Link from "next/link";
import { Bell } from "lucide-react";
import { UnreadCountBadge } from "@/components/shell/UnreadCountBadge";
import { buttonVariants } from "@/components/ui/button";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import { useUnreadNotificationCount } from "../hooks/useUnreadNotificationCount";

/**
 * Chuông ở header khu bệnh nhân: mở thẳng trang thông báo, kèm số chưa đọc. Chỉ hiện khi tài
 * khoản có hồ sơ đã gắn (BR-68); tài khoản chưa gắn hồ sơ không có thông báo.
 */
export function PatientNotificationBell() {
  const { profiles, loading } = useLinkedProfiles();
  const unreadCount = useUnreadNotificationCount();
  if (loading || profiles.length === 0) return null;

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
