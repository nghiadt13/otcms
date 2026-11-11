import { apiRequest } from "@/lib/api/client";
import type { BillingInvoice } from "@/lib/api/billingApi";

// Tiếp nhận walk-in tại quầy (plan 261111-0208 mục 3: A4, A5). Lỗi nghiệp vụ đi qua ApiError;
// màn hình hiển thị bằng messageFromError (mã MSG R3), không viết chuỗi lỗi ở đây.

/**
 * Kết quả tạo lượt walk-in (A4). `PENDING_PAYMENT`: chưa có số, cần thu phí tại quầy.
 * `WAITING`: đã cấp số ngay (dịch vụ giá 0).
 */
export type ReceptionWalkIn = {
  visitId: string;
  patientProfileId: string;
  priorAppointmentId: string | null;
  arrivedAt: string;
  examStatus: "PENDING_PAYMENT" | "WAITING";
  queueNumber: number | null;
  doctorName: string;
  roomLabel: string | null;
  feeAmount: number;
};

/** Dịch vụ khám còn hiệu lực, lễ tân chọn cho walk-in. */
export type ExaminationService = { id: string; name: string; price: number };

/** Tạo lượt walk-in: bác sĩ phải đang trong ca và dịch vụ phải loại khám (A4). */
export function createReceptionWalkIn(input: {
  patientProfileId: string;
  priorAppointmentId?: string;
  doctorProfileId: string;
  serviceId: string;
  idempotencyKey: string;
}) {
  return apiRequest<ReceptionWalkIn>("/api/v1/visits/walk-ins", {
    method: "POST",
    headers: { "Idempotency-Key": input.idempotencyKey },
    body: JSON.stringify({
      patientProfileId: input.patientProfileId,
      priorAppointmentId: input.priorAppointmentId,
      doctorProfileId: input.doctorProfileId,
      serviceId: input.serviceId,
    }),
  });
}

/**
 * Lập hóa đơn nguồn lượt khám cho walk-in đang `PENDING_PAYMENT`: đúng một dòng là dịch vụ khám (A5).
 * `idempotencyKey` giữ nguyên khi thử lại cùng một lần thu phí, tránh tạo hai hóa đơn.
 */
export function createWalkInVisitInvoice(
  visitId: string,
  serviceId: string,
  idempotencyKey: string,
) {
  return apiRequest<BillingInvoice>(
    `/api/v1/visits/${encodeURIComponent(visitId)}/invoices`,
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify({ items: [{ serviceId, quantity: 1 }] }),
    },
  );
}

/** Dịch vụ loại khám còn hoạt động, lấy từ danh mục nhân viên (cùng nguồn bác sĩ dùng). */
export async function listExaminationServices(
  signal?: AbortSignal,
): Promise<ExaminationService[]> {
  const { items } = await apiRequest<{
    items: {
      id: string;
      name: string;
      type: string;
      price: number;
      active: boolean;
    }[];
  }>("/api/v1/staff/clinic-services", { signal });
  return items
    .filter((item) => item.type === "EXAMINATION" && item.active)
    .map(({ id, name, price }) => ({ id, name, price }));
}
