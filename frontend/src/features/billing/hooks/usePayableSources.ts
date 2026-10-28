"use client";

import { listPayableSources } from "../api/patientBillingApi";
import { useBillingLoad } from "./useBillingLoad";

/**
 * Nguồn còn khoản chờ thanh toán (chỉ đọc). `patientId` lọc theo người khám đang chọn; rỗng
 * là tất cả hồ sơ của tài khoản. Thứ tự do BE quyết định, FE không sắp lại.
 */
export function usePayableSources(patientId?: string) {
  const { data, loading, error, reload } = useBillingLoad(
    (signal) =>
      listPayableSources({ patientId }, signal).then(
        (response) => response.items,
      ),
    patientId ?? "",
  );
  return { sources: data, loading, error, reload };
}
