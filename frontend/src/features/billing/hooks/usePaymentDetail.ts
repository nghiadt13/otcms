"use client";

import { getPaymentDetail } from "../api/paymentApi";
import type { PaymentDetail } from "../types/payment";
import { useBillingDetail } from "./useBillingDetail";

/** Chi tiết một khoản thanh toán cho Sheet. `id = null` thì không tải. */
export function usePaymentDetail(id: string | null) {
  return useBillingDetail<PaymentDetail>(id, getPaymentDetail);
}
