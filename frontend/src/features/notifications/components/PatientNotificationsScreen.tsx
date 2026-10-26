"use client";

import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PortalEmptyState } from "@/components/patient/PortalEmptyState";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import type { NotificationRecordHrefResolver } from "../utils/notificationRecordLinks";
import { LoadError, NotificationListSkeleton } from "./PatientNotificationList";
import { PatientNotificationsBody } from "./PatientNotificationsBody";

/**
 * Màn Thông báo khu bệnh nhân (UC63). Thông báo gắn với tài khoản có hồ sơ đã gắn (BR-68):
 * chưa có hồ sơ thì không tải danh sách, chỉ hướng dẫn thêm hồ sơ.
 */
export function PatientNotificationsScreen({
  resolveRecordHref,
}: {
  resolveRecordHref: NotificationRecordHrefResolver;
}) {
  const linked = useLinkedProfiles();

  if (linked.loading) return <NotificationListSkeleton />;
  if (linked.error)
    return (
      <LoadError
        message={`Không tải được thông tin tài khoản. ${linked.error}`}
        onRetry={linked.reload}
      />
    );
  if (linked.profiles.length === 0)
    return (
      <PortalEmptyState
        action={
          <Link
            className={publicButtonClass("primary")}
            href={PATIENT_ROUTES.newProfile}
          >
            Thêm hồ sơ bệnh nhân
          </Link>
        }
      >
        Tài khoản của bạn chưa có hồ sơ bệnh nhân nên chưa nhận được thông báo.
        Thêm hồ sơ để nhận thông báo về lịch khám và kết quả.
      </PortalEmptyState>
    );

  return <PatientNotificationsBody resolveRecordHref={resolveRecordHref} />;
}
