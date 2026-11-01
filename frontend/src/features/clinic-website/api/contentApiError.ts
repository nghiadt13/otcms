import { ApiError, type MsgCode } from "@/lib/api/apiError";
export function rethrowContentError(error: unknown): never {
  if (
    error instanceof ApiError &&
    !error.code &&
    /^MSG-\d+$/.test(error.errorCode ?? "")
  ) {
    throw new ApiError(error.status, {
      code: error.errorCode as MsgCode,
      errorCode: error.errorCode,
      fieldErrors: error.fieldErrors,
    });
  }
  throw error;
}
