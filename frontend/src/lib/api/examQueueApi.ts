import { apiRequest } from "@/lib/api/client";
import type { ExamQueueStatus } from "@/lib/domain/clinicVisit";

// Hợp đồng hàng chờ khám (plan 261111-0208-visit-flow-r3, mục 3: A1, A2, A3, A6, A9).
// Lỗi nghiệp vụ đi qua ApiError: màn hình hiển thị bằng messageFromError (mã MSG R3).

/** Nguồn của lượt: lịch hẹn đã check-in, hoặc walk-in lập tại quầy. */
export type ExamQueueSource = "SCHEDULED" | "WALK_IN";

/**
 * Một lượt trong hàng chờ khám (A1, A6). `queueNumber` là null khi lượt còn `PENDING_PAYMENT`
 * (walk-in chưa thu phí, chưa cấp số). Các mốc thời gian là ISO-8601, null khi chưa xảy ra.
 */
export interface ExamQueueItem {
  visitId: string;
  queueNumber: number | null;
  examStatus: ExamQueueStatus;
  source: ExamQueueSource;
  appointmentId: string | null;
  patientProfileId: string;
  patientName: string;
  doctorProfileId: string;
  doctorName: string;
  roomId: string | null;
  roomLabel: string | null;
  serviceId: string | null;
  queuedAt: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

/** Trạng thái trong danh sách của bác sĩ (A2): không có chờ thu phí và đã huỷ. */
export type DoctorExamQueueStatus = Exclude<
  ExamQueueStatus,
  "PENDING_PAYMENT" | "CANCELLED"
>;

/** Dòng trong danh sách của bác sĩ (A2): thêm ngày sinh và giới tính của bệnh nhân. */
export interface DoctorExamQueueItem extends Omit<ExamQueueItem, "examStatus"> {
  examStatus: DoctorExamQueueStatus;
  /** YYYY-MM-DD. */
  dateOfBirth: string | null;
  gender: "MALE" | "FEMALE" | "OTHER" | null;
}

/** Bác sĩ có ca làm việc bao phủ thời điểm hiện tại (A3). */
export interface OnShiftDoctor {
  doctorProfileId: string;
  doctorName: string;
  roomId: string | null;
  roomLabel: string | null;
  /** Số lượt đang `WAITING` của bác sĩ. */
  waitingCount: number;
}

/** Kết quả khi bác sĩ kết thúc lượt walk-in (A9). */
export interface CompleteWalkInVisitResult {
  visitId: string;
  examStatus: "COMPLETED";
  closedAt: string;
}

/** Hàng chờ khám của lễ tân (A1). `date` dạng YYYY-MM-DD; bỏ trống thì BE dùng hôm nay. */
export function listReceptionExamQueue(
  date?: string,
): Promise<{ items: ExamQueueItem[] }> {
  const query = date ? `?${new URLSearchParams({ date })}` : "";
  return apiRequest<{ items: ExamQueueItem[] }>(`/api/v1/exam-queue${query}`);
}

/** Lượt của chính bác sĩ đăng nhập: hôm nay và các lượt chưa xong của ngày trước (A2). */
export function listDoctorExamQueue(): Promise<{
  items: DoctorExamQueueItem[];
}> {
  return apiRequest<{ items: DoctorExamQueueItem[] }>(
    "/api/v1/doctor/exam-queue",
  );
}

/** Bác sĩ đang trong ca, kèm phòng và số lượt chờ (A3). Danh sách rỗng thì không tạo được walk-in. */
export function listOnShiftDoctors(): Promise<{ items: OnShiftDoctor[] }> {
  return apiRequest<{ items: OnShiftDoctor[] }>(
    "/api/v1/reception/on-shift-doctors",
  );
}

/** Một dòng hàng chờ theo mã lượt khám (A6). BE trả 404 khi lượt không có trong hàng chờ. */
export function getExamQueueEntry(visitId: string): Promise<ExamQueueItem> {
  return apiRequest<ExamQueueItem>(
    `/api/v1/visits/${encodeURIComponent(visitId)}/exam-queue-entry`,
  );
}

/** Bác sĩ kết thúc lượt walk-in (A9). Lịch hẹn kết thúc qua PATCH appointments/{id}/status. */
export function completeWalkInVisit(
  visitId: string,
): Promise<CompleteWalkInVisitResult> {
  return apiRequest<CompleteWalkInVisitResult>(
    `/api/v1/visits/${encodeURIComponent(visitId)}/completion`,
    { method: "POST" },
  );
}
