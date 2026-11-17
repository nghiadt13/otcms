"use client";

import { useCallback, useState } from "react";
import { usePagedList } from "@/hooks/usePagedList";
import {
  listPayments,
  type PaymentListQuery,
  type PaymentRecord,
} from "@/lib/api/paymentRecordsApi";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import { isBillingDateRangeValid } from "@/lib/domain/billingDateRangeRules";

type PaymentRecordFilters = { from: string; to: string; patientId: string };

/** Mặc định: 30 ngày gần nhất tới hôm nay (giờ Việt Nam). */
function defaultRange(now: Date) {
  const to = todayIsoDate(now);
  return { from: addDays(to, -30), to };
}

/**
 * Danh sách lịch sử thanh toán (GET /api/v1/payments). Không truyền `options`: 30 ngày gần nhất
 * (màn admin). Truyền thì dùng đúng khoảng và người khám đó, thiếu ngày nghĩa là không giới hạn
 * (màn bệnh nhân lấy từ URL). Từ > đến: không gọi mạng, báo MSG-54 (`rangeError`).
 */
export function usePaymentRecordList(
  options?: { from?: string; to?: string; patientId?: string },
) {
  const [initialFilters] = useState<PaymentRecordFilters>(() =>
    options
      ? {
          from: options.from ?? "",
          to: options.to ?? "",
          patientId: options.patientId ?? "",
        }
      : { ...defaultRange(new Date()), patientId: "" },
  );
  const fetchPage = useCallback(
    (query: PaymentListQuery & PaymentRecordFilters, signal: AbortSignal) =>
      listPayments(
        {
          patientId: query.patientId || undefined,
          from: query.from,
          to: query.to,
          page: query.page,
          size: query.size,
        },
        signal,
      ),
    [],
  );
  const list = usePagedList<PaymentRecord, PaymentRecordFilters>(
    fetchPage,
    initialFilters,
  );
  const { setFilters } = list;
  const [range, setRange] = useState({
    from: initialFilters.from,
    to: initialFilters.to,
  });
  const rangeError = isBillingDateRangeValid(range.from, range.to)
    ? null
    : "MSG-54";

  // Ô ngày bị xóa trắng: giữ giá trị trước đó, không gọi API.
  const setDateRange = useCallback(
    (next: { from: string; to: string }) => {
      if (!next.from || !next.to) return;
      setRange(next);
      if (isBillingDateRangeValid(next.from, next.to)) setFilters(next);
    },
    [setFilters],
  );

  return {
    items: list.data?.items ?? [],
    totalItems: list.data?.totalItems ?? 0,
    totalPages: list.data?.totalPages ?? 1,
    page: list.page,
    setPage: list.setPage,
    loading: list.loading,
    error: list.error,
    reload: list.reload,
    range,
    rangeError,
    setDateRange,
  };
}
