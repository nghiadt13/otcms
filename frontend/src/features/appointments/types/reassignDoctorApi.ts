import type { ItemsResponse } from "@/lib/api/pagination";
import type { DoctorRef, EligibleDoctor } from "./appointment";

// Hợp đồng dùng chung của Dialog "Chuyển bác sĩ" (UC22/UC23). Admin truyền bộ endpoint /api/v1/admin/...,
// khu Lễ tân truyền bộ /api/v1/appointments/... Dialog không biết mình đang ở khu nào.

/** Phần tối thiểu của một lịch mà Dialog đọc. Mỗi khu có kiểu lịch riêng nhưng cùng các trường này. */
export interface ReassignTarget {
  id: string;
  /** ISO +07:00 */
  startAt: string;
  doctor: DoctorRef | null;
  /** BE quyết định; FE chỉ vẽ nút có trong mảng. */
  allowedActions: readonly string[];
}

export interface EligibleDoctorsApi {
  /** Bác sĩ có ca cùng giờ với lịch (danh sách rỗng = luồng AF1). */
  listEligibleDoctors(
    appointmentId: string,
    signal?: AbortSignal,
  ): Promise<ItemsResponse<EligibleDoctor>>;
}

export interface ReassignDoctorApi<
  T extends ReassignTarget,
> extends EligibleDoctorsApi {
  /** Chuyển sang bác sĩ đã chọn, giờ không đổi. Trả bản ghi mới từ BE. */
  reassign(appointmentId: string, doctorId: string): Promise<T>;
  /** Báo cho bệnh nhân khi không có bác sĩ thay (AF1). Lịch vẫn Đã đặt. */
  notifyNoReplacement(appointmentId: string): Promise<T>;
}
