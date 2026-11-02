import { isApiError } from "@/lib/api/apiError";

/** 404/403 hoặc 409 MSG-32: lịch không còn dùng được, phải đóng và tải lại. */
export function isStaleAppointmentError(error: unknown): boolean {
  return (
    isApiError(error) &&
    (error.status === 404 ||
      error.status === 403 ||
      (error.status === 409 && error.code === "MSG-32"))
  );
}
