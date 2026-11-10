"use client";

import type { NotificationRecordHrefResolver } from "../utils/notificationRecordLinks";
import { PatientNotificationsBody } from "./PatientNotificationsBody";

/**
 * Màn Thông báo khu bệnh nhân (UC63). Thông báo gắn với tài khoản: tài khoản mới chưa điền thông
 * tin cá nhân vẫn mở được màn này và thấy danh sách trống (chỉ đặt lịch mới bắt buộc hồ sơ).
 */
export function PatientNotificationsScreen({
  resolveRecordHref,
}: {
  resolveRecordHref: NotificationRecordHrefResolver;
}) {
  return <PatientNotificationsBody resolveRecordHref={resolveRecordHref} />;
}
