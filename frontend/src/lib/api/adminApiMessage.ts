import { APP_MESSAGES } from "@/lib/messages/appMessages";
import { isApiError } from "./apiError";

const messages: Record<string, string> = {
  DOCTOR_SHIFT_OVERLAP:
    "Ca trực trùng với một ca khác của bác sĩ. Vui lòng chọn lại thời gian.",
  DOCTOR_TIME_OFF_OVERLAP:
    "Khoảng nghỉ trùng với một khoảng nghỉ khác. Hãy chọn lại thời gian.",
  DOCTOR_RESOURCE_IN_PAST:
    "Ca trực hoặc khoảng nghỉ đã bắt đầu. Chỉ được thay đổi lịch trong tương lai.",
  DOCTOR_ACCOUNT_ALREADY_PROFILED:
    "Tài khoản này đã có hồ sơ bác sĩ. Hãy tải lại danh sách.",
  DOCTOR_ACCOUNT_UNAVAILABLE:
    "Tài khoản bác sĩ không còn hợp lệ. Hãy chọn lại tài khoản.",
  DOCTOR_PROFILE_NOT_FOUND:
    "Hồ sơ bác sĩ không còn tồn tại. Hãy tải lại danh sách.",
  DOCTOR_SHIFT_NOT_FOUND: "Ca trực không còn tồn tại. Hãy tải lại lịch.",
  DOCTOR_TIME_OFF_NOT_FOUND: "Khoảng nghỉ không còn tồn tại. Hãy tải lại lịch.",
  DOCTOR_RESOURCE_REQUEST_INVALID:
    "Thời gian không hợp lệ. Giờ kết thúc phải sau giờ bắt đầu.",
  RESOURCE_MUTATION_BLOCKED:
    "Chưa thể thay đổi vì còn lịch hẹn hoặc lượt khám bị ảnh hưởng. Hãy điều phối các lịch này trước rồi thử lại.",
  SERVICE_NOT_FOUND:
    "Dịch vụ không còn tồn tại hoặc đã ngừng cung cấp. Hãy tải lại danh sách.",
  SERVICE_REQUEST_INVALID:
    "Thông tin dịch vụ chưa hợp lệ. Kiểm tra giá và thời lượng.",
  DEPENDENCY_UNAVAILABLE:
    "Chưa kiểm tra được các lịch liên quan. Vui lòng thử lại sau.",
};

export function adminApiMessage(error: unknown): string {
  if (isApiError(error)) {
    if (error.code && APP_MESSAGES[error.code]) return APP_MESSAGES[error.code];
    const map: Record<string, string> = {
      VALIDATION_FAILED: "MSG-66",
      SERVICE_REQUEST_INVALID: "MSG-66",
      SERVICE_NOT_FOUND: "MSG-50",
      SERVICE_NAME_ALREADY_EXISTS: "MSG-66",
      SERVICE_NAME_DUPLICATE: "MSG-66",
      DOCTOR_ASSIGNMENT_ALREADY_EXISTS: "MSG-66",
      DOCTOR_ASSIGNMENT_NOT_FOUND: "MSG-50",
      ACCESS_DENIED: "MSG-14",
      RESOURCE_MUTATION_BLOCKED: "MSG-36",
    };
    const msg = error.errorCode && map[error.errorCode];
    if (msg) return APP_MESSAGES[msg];
    if (error.status === 401)
      return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
    if (error.status === 403)
      return "Bạn cần đăng nhập bằng tài khoản Quản trị viên để thực hiện thao tác này.";
    if (error.errorCode && messages[error.errorCode])
      return messages[error.errorCode];
    if (error.status === 404) return APP_MESSAGES["MSG-50"];
    if (error.status === 409)
      return "Dữ liệu đã thay đổi hoặc đang được sử dụng. Hãy tải lại và kiểm tra các lịch liên quan.";
    if (error.status === 400)
      return "Thông tin chưa hợp lệ. Vui lòng kiểm tra các trường đã nhập.";
  }
  if (
    error instanceof Error &&
    error.message === "Bạn cần đăng nhập để tiếp tục."
  )
    return error.message;
  return "Không kết nối được dịch vụ. Kiểm tra backend và thử lại.";
}
