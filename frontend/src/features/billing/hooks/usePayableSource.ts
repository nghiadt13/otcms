"use client";

import { getPayableSource } from "../api/patientBillingApi";
import type { PaymentSourceKind } from "../types/patientBilling";
import { useBillingLoad } from "./useBillingLoad";

/** Chi tiết một nguồn: các khoản, trạng thái và tổng chờ thanh toán (chỉ đọc). */
export function usePayableSource(kind: PaymentSourceKind, id: string) {
  const { data, loading, error, reload } = useBillingLoad(
    (signal) => getPayableSource(kind, id, signal),
    `${kind}:${id}`,
  );
  return { source: data, loading, error, reload };
}
