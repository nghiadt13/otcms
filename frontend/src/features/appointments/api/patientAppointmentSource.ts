import * as live from "./patientAppointmentLiveApi";
import * as liveHold from "./pendingHoldLiveApi";
import * as mock from "./patientAppointmentApi";

// Nguồn dữ liệu lịch hẹn khu bệnh nhân: backend thật khi chạy app; kho giả chỉ trong test (các test
// hiện có dựa vào kho giả dùng chung với khu Lễ tân).
const isTest = process.env.NODE_ENV === "test";
const source = isTest ? mock : live;

export const listMyAppointments = source.listMyAppointments;
export const getMyAppointment = source.getMyAppointment;
export const cancelMyAppointment = source.cancelMyAppointment;

/** Hủy giữ chỗ thanh toán của tài khoản (rời trang = hủy thanh toán). Idempotent. */
export const cancelPendingHold: () => Promise<
  liveHold.PendingHoldCancelResult
> = isTest ? mock.cancelPendingHold : liveHold.cancelPendingHold;

/** Trạng thái BE của một lịch (thô); lịch không còn thì ném ApiError 404. */
export const getAppointmentStatus: (
  appointmentId: string,
  signal?: AbortSignal,
) => Promise<string> = isTest
  ? async (appointmentId) => {
      // Kho giả dùng trạng thái màn hình (BOOKED); BE thật trả CONFIRMED.
      const { status } = await mock.getMyAppointment(appointmentId);
      return status === "BOOKED" ? "CONFIRMED" : status;
    }
  : liveHold.getAppointmentStatus;
