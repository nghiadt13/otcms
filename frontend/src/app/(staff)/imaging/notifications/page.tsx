"use client";

import { PatientNotificationsScreen } from "@/features/notifications/components/PatientNotificationsScreen";
import type { NotificationRecordHrefResolver } from "@/features/notifications/utils/notificationRecordLinks";

const resolveImagingRecordHref: NotificationRecordHrefResolver = () => null;

export default function Page() {
  return (
    <PatientNotificationsScreen resolveRecordHref={resolveImagingRecordHref} />
  );
}
