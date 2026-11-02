import { isApiError, toFieldErrorMap } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import {
  isProfileFieldName,
  type ProfileFieldErrors,
} from "../schema/patientProfileFieldRules";

// Hai lỗi nghiệp vụ dưới đây chưa có mã MSG trong bảng (R3 không định nghĩa). BE trả tên lỗi
// trong `detail` của ProblemDetail, cùng HTTP 409. Màn kiểm tra qua hàm dưới đây, không so
// chuỗi rải rác. Xem câu hỏi mở trong báo cáo.

export const DUPLICATE_PROFILE_DETAIL = "DUPLICATE_PROFILE";
export const STALE_HISTORY_DETAIL = "STALE";

/**
 * Lỗi 400 của BE gắn vào đúng ô (MSG-17 theo trường). Trả undefined khi không có trường nào
 * ánh xạ được, để màn báo chung bằng toast.
 */
export function fieldErrorsFromResponse(
  error: unknown,
): ProfileFieldErrors | undefined {
  if (!isApiError(error) || error.status !== 400) return undefined;
  const mapped: ProfileFieldErrors = {};
  for (const [field, code] of Object.entries(
    toFieldErrorMap(error.fieldErrors),
  )) {
    if (isProfileFieldName(field)) mapped[field] = formatAppMessage(code);
  }
  return Object.keys(mapped).length > 0 ? mapped : undefined;
}

/** Tạo hồ sơ trùng SĐT + ngày sinh với hồ sơ đã có (409). */
export function isDuplicateProfileError(error: unknown): boolean {
  return (
    isApiError(error) &&
    error.status === 409 &&
    error.message === DUPLICATE_PROFILE_DETAIL
  );
}

/** Tiền sử đã bị người khác sửa sau khi màn tải (409 STALE). */
export function isStaleHistoryError(error: unknown): boolean {
  return (
    isApiError(error) &&
    error.status === 409 &&
    error.message === STALE_HISTORY_DETAIL
  );
}

/** 403/404 của một hồ sơ hoặc tiền sử: màn chuyển sang trang không tìm thấy của khu. */
export function isHiddenRecordError(error: unknown): boolean {
  return isApiError(error) && (error.status === 403 || error.status === 404);
}
