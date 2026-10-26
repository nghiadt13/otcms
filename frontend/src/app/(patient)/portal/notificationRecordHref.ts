import type { NotificationRelatedRecord } from "@/features/notifications/types/notification";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";

// Lớp ghép khu bệnh nhân: loại bản ghi liên quan → màn của khu. Đặt ở app/ để feature
// notifications không biết route của khu nào (quy tắc import của frontend).

/**
 * Đường dẫn màn cho bản ghi liên quan của thông báo. Bản ghi không còn khả dụng hoặc loại lạ
 * trả null để ẩn nút Mở.
 */
export function resolvePatientRecordHref(
  record: NotificationRelatedRecord,
): string | null {
  if (!record.available) return null;
  switch (record.kind) {
    case "APPOINTMENT":
      return PATIENT_ROUTES.appointmentDetail(record.id);
    case "VISIT":
      return PATIENT_ROUTES.visitDetail(record.id);
    case "VISIT_RESULT":
      return PATIENT_ROUTES.resultDetail(record.id);
    case "PAYMENT":
      return PATIENT_ROUTES.paymentDetail(record.id);
    default:
      return null;
  }
}
