"use client";

import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatDateTime } from "@/lib/format/formatDate";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { usePaymentRecordList } from "@/hooks/usePaymentRecordList";
import { paymentChannelLabel } from "@/lib/domain/billingLabels";
import { PaymentErrorBlock } from "./PaymentErrorBlock";
import {
  LIST_FRAME_CLASS,
  LIST_LINK_CLASS,
} from "./patientBillingPresentation";

/**
 * Các giao dịch đã thanh toán thành công của một người khám (GET /api/v1/payments). Mỗi dòng mở
 * phiếu thu. Khoảng ngày và người khám lấy từ URL; khoảng sai do màn cha chặn trước.
 */
export function PaymentHistoryList({
  from,
  to,
  patientId,
}: {
  from?: string;
  to?: string;
  patientId?: string;
}) {
  const history = usePaymentRecordList({ from, to, patientId });

  if (history.error)
    return (
      <PaymentErrorBlock message={history.error} onRetry={history.reload} />
    );
  if (history.loading)
    return (
      <p className="text-base text-muted-foreground" role="status">
        Đang tải lịch sử thanh toán…
      </p>
    );
  if (history.items.length === 0)
    return (
      <p className="text-base text-muted-foreground">
        Chưa có khoản thanh toán nào trong khoảng đã chọn.
      </p>
    );

  return (
    <div className="space-y-4">
      <ul className={LIST_FRAME_CLASS}>
        {history.items.map((payment) => (
          <li key={payment.id}>
            <Link
              className={LIST_LINK_CLASS}
              href={PATIENT_ROUTES.paymentDetail(payment.id)}
            >
              <span className="min-w-0 space-y-1">
                <span className="block font-medium tabular-nums">
                  {payment.invoiceNumber}
                </span>
                <span className="block text-sm text-muted-foreground">
                  {formatDateTime(payment.confirmedAt)} ·{" "}
                  {paymentChannelLabel(payment.channel)}
                </span>
              </span>
              <span className="flex shrink-0 items-center gap-2 tabular-nums">
                {formatCurrencyVnd(payment.amount)}
                <ChevronRight aria-hidden className="size-5" />
              </span>
            </Link>
          </li>
        ))}
      </ul>
      {history.totalPages > 1 ? (
        <nav
          aria-label="Phân trang lịch sử"
          className="flex items-center justify-between gap-3"
        >
          <button
            className={publicButtonClass("secondary")}
            disabled={history.page === 0}
            onClick={() => history.setPage(history.page - 1)}
            type="button"
          >
            Trang trước
          </button>
          <span className="text-sm text-muted-foreground">
            Trang {history.page + 1}/{history.totalPages}
          </span>
          <button
            className={publicButtonClass("secondary")}
            disabled={history.page + 1 >= history.totalPages}
            onClick={() => history.setPage(history.page + 1)}
            type="button"
          >
            Trang sau
          </button>
        </nav>
      ) : null}
    </div>
  );
}
