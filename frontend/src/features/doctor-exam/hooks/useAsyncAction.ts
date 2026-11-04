"use client";

import { useCallback, useState } from "react";
import { notifySuccess } from "@/lib/messages/notify";
import { notifyDoctorError } from "../schema/doctorErrors";

/**
 * Chạy một thao tác ghi: giữ cờ `pending`, báo thành công (mã MSG hoặc câu chữ) và báo lỗi
 * bằng toast. Trả `undefined` khi lỗi để nơi gọi biết không cần làm bước sau.
 */
export function useAsyncAction() {
  const [pending, setPending] = useState(false);
  const run = useCallback(
    async <T>(
      action: () => Promise<T>,
      success?: string,
    ): Promise<T | undefined> => {
      setPending(true);
      try {
        const result = await action();
        if (success) notifySuccess(success);
        return result;
      } catch (error) {
        notifyDoctorError(error);
        return undefined;
      } finally {
        setPending(false);
      }
    },
    [],
  );
  return { pending, run };
}
