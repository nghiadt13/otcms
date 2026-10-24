import { ApiError } from "@/lib/api/apiError";

// Lỗi ApiError của lời gọi bị từ chối, dùng chung cho các file test của lớp API giả.

export async function rejectionOf(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    if (error instanceof ApiError) return error;
    throw error;
  }
  throw new Error("Lẽ ra lời gọi phải bị từ chối.");
}

