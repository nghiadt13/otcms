"use client";

import { PatientNotificationsScreen } from "@/features/notifications/components/PatientNotificationsScreen";
import { resolvePatientRecordHref } from "../notificationRecordHref";

// Lớp ghép khu bệnh nhân: đưa map bản ghi → màn của khu vào màn Thông báo.
export function PatientNotificationsClient() {
  return (
    <PatientNotificationsScreen resolveRecordHref={resolvePatientRecordHref} />
  );
}
