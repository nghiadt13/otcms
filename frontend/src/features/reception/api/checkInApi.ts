import { apiRequest } from "@/lib/api/client";
import { clone, mockDelay, mockFail } from "@/lib/api/mockServer";
import type { ItemsResponse } from "@/lib/api/pagination";
import type {
  CheckInContext,
  CheckInContextQuery,
  CheckInRequest,
  CheckInResult,
  ExamFeePayment,
  ExamFeePaymentRequest,
  PriorityGroup,
  TodayAppointmentStatusFilter,
  TodayAppointmentsResponse,
} from "../types/checkIn";
import { checkInPatientMock } from "./checkInMockCheckIn";
import {
  collectExamFeeMock,
  getExamFeePaymentMock,
} from "./checkInMockPayments";
import {
  getCheckInContextMock,
  listPriorityGroupsMock,
  listTodayAppointmentsMock,
} from "./checkInMockQueries";

// Lớp API giả cho màn Tiếp nhận. Chữ ký khớp REST của BE; khi nối BE đặt USE_MOCK = false
// (mọi nhánh thật đã viết sẵn). Đầu vào được kiểm ở ranh giới trước khi gọi BE hay kho giả.

const USE_MOCK = true;

// Trong test không chờ bằng timer (timer 0ms của lớp giả sẽ lệch với đồng hồ giả); ngoài test
// giữ độ trễ 300 ms như BE thật để thấy trạng thái tải.
const latency = (signal?: AbortSignal) =>
  process.env.NODE_ENV === "test" ? Promise.resolve() : mockDelay(300, signal);

const STATUS_FILTERS: readonly TodayAppointmentStatusFilter[] = ["BOOKED", "CHECKED_IN", ""];

const isFilled = (value: string | null | undefined): value is string =>
  typeof value === "string" && value.trim().length > 0;

/** GET /api/v1/reception/today-appointments?status= (không phân trang, lọc tên/SĐT ở FE). */
export async function listTodayAppointments(
  status: TodayAppointmentStatusFilter,
  signal?: AbortSignal,
): Promise<TodayAppointmentsResponse> {
  if (!STATUS_FILTERS.includes(status)) mockFail(400, "MSG-39");
  if (!USE_MOCK) {
    // Rỗng nghĩa là tất cả: phải gửi `status=` tường minh, BE mặc định BOOKED khi thiếu tham số.
    return apiRequest<TodayAppointmentsResponse>(
      `/api/v1/reception/today-appointments?status=${status}` as const,
      { signal },
    );
  }
  await latency(signal);
  return clone(listTodayAppointmentsMock(status));
}

/** GET /api/v1/reception/check-in-context?appointmentId= | patientId= (đúng một tham số). */
export async function getCheckInContext(
  query: CheckInContextQuery,
  signal?: AbortSignal,
): Promise<CheckInContext> {
  const value = "appointmentId" in query ? query.appointmentId : query.patientId;
  if (!isFilled(value)) mockFail(400, "MSG-39");
  if (!USE_MOCK) {
    const params = new URLSearchParams(
      "appointmentId" in query
        ? { appointmentId: query.appointmentId }
        : { patientId: query.patientId },
    );
    return apiRequest<CheckInContext>(
      `/api/v1/reception/check-in-context?${params.toString()}` as const,
      { signal },
    );
  }
  await latency(signal);
  return clone(getCheckInContextMock(query));
}

/** GET /api/v1/reception/priority-groups. "Không ưu tiên" là lựa chọn của FE (null). */
export async function listPriorityGroups(
  signal?: AbortSignal,
): Promise<ItemsResponse<PriorityGroup>> {
  if (!USE_MOCK) {
    return apiRequest<ItemsResponse<PriorityGroup>>("/api/v1/reception/priority-groups", { signal });
  }
  await latency(signal);
  return clone({ items: listPriorityGroupsMock() });
}

/** POST /api/v1/reception/exam-fee-payments: 201 với CASH (SUCCESS) hoặc VNPAY (PENDING + QR). */
export async function collectExamFee(body: ExamFeePaymentRequest): Promise<ExamFeePayment> {
  if (!isFilled(body.patientId)) mockFail(400, "MSG-39");
  if (body.method !== "CASH" && body.method !== "VNPAY") mockFail(400, "MSG-39");
  if (!USE_MOCK) {
    return apiRequest<ExamFeePayment>("/api/v1/reception/exam-fee-payments", {
      method: "POST",
      body: JSON.stringify(body),
    });
  }
  await latency();
  return clone(collectExamFeeMock(body));
}

/** GET /api/v1/reception/exam-fee-payments/{id}: FE hỏi lại khi đang chờ VNPay. */
export async function getExamFeePayment(
  id: string,
  signal?: AbortSignal,
): Promise<ExamFeePayment> {
  if (!isFilled(id)) mockFail(400, "MSG-39");
  if (!USE_MOCK) {
    return apiRequest<ExamFeePayment>(
      `/api/v1/reception/exam-fee-payments/${encodeURIComponent(id)}` as const,
      { signal },
    );
  }
  await latency(signal);
  return clone(getExamFeePaymentMock(id));
}

/** POST /api/v1/reception/check-ins: 201 khi tiếp nhận xong, kèm số thứ tự và phòng. */
export async function checkInPatient(body: CheckInRequest): Promise<CheckInResult> {
  if (!isFilled(body.patientId)) mockFail(400, "MSG-39");
  if (!USE_MOCK) {
    return apiRequest<CheckInResult>("/api/v1/reception/check-ins", {
      method: "POST",
      body: JSON.stringify(body),
    });
  }
  await latency();
  return clone(checkInPatientMock(body));
}
