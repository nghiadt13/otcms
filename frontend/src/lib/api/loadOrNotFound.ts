import { notFound } from "next/navigation";
import { isApiError } from "./apiError";

/**
 * Chạy một lời gọi API công khai cho trang chi tiết. 404 chuyển thành trang không tìm thấy
 * của khu (`not-found.tsx`); lỗi khác ném tiếp để `error.tsx` xử lý, không nuốt lỗi.
 */
export async function loadOrNotFound<T>(load: () => Promise<T>): Promise<T> {
  try {
    return await load();
  } catch (error) {
    if (isApiError(error) && error.status === 404) notFound();
    throw error;
  }
}
