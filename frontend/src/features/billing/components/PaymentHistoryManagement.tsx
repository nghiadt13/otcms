"use client";

import { useState } from "react";
import { TablePagination } from "@/components/admin/TablePagination";
import { PageHeader } from "@/components/PageHeader";
import { usePaymentHistory } from "../hooks/usePaymentHistory";
import { PaymentHistoryDetailSheet } from "./PaymentHistoryDetailSheet";
import { PaymentHistoryFilters } from "./PaymentHistoryFilters";
import { PaymentHistoryTable } from "./PaymentHistoryTable";

/** Màn Lịch sử thanh toán: chỉ xem, không có nút chính. */
export function PaymentHistoryManagement() {
  const history = usePaymentHistory();
  const [selected, setSelected] = useState<{
    id: string;
    receiptNumber: string;
  } | null>(null);
  const rangeInvalid = history.rangeError !== null;

  return (
    <>
      <PageHeader
        title="Lịch sử thanh toán"
        description="Các khoản thanh toán đã hoàn tất, theo thời điểm thu. Chỉ để xem."
      />
      <PaymentHistoryFilters
        from={history.range.from}
        to={history.range.to}
        rangeError={history.rangeError}
        onRangeChange={history.setDateRange}
        searchText={history.searchText}
        onSearchChange={history.setSearchText}
        method={history.filters.method}
        onMethodChange={history.setMethod}
      />
      <PaymentHistoryTable
        items={history.items}
        summary={history.summary}
        loading={history.loading}
        error={history.error}
        rangeInvalid={rangeInvalid}
        hasFilter={history.hasFilter}
        onRetry={history.retryable ? history.reload : undefined}
        onView={(id) => {
          const row = history.items.find((item) => item.id === id);
          if (row) setSelected({ id, receiptNumber: row.receiptNumber });
        }}
      />
      {!rangeInvalid && !history.error && !history.loading ? (
        <TablePagination
          page={history.page}
          totalPages={history.totalPages}
          totalItems={history.totalItems}
          onPageChange={history.setPage}
        />
      ) : null}
      {selected ? (
        <PaymentHistoryDetailSheet
          paymentId={selected.id}
          receiptNumber={selected.receiptNumber}
          onClose={() => setSelected(null)}
        />
      ) : null}
    </>
  );
}
