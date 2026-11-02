"use client";

import { DetailList } from "@/components/admin/DetailList";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatDate, formatDateTime } from "@/lib/format/formatDate";
import { useUnpaidItemDetail } from "../hooks/useUnpaidItems";
import {
  UNPAID_ITEM_KIND_LABELS,
  UNPAID_ITEM_REFERENCE_LABELS,
  type UnpaidItemDetail,
} from "../types/unpaidItem";
import { BillingDetailSheetFrame } from "./BillingDetailSheetFrame";

type UnpaidItemDetailSheetProps = {
  itemId: string;
  /** Tên khoản của dòng đã chọn, để có tiêu đề ngay khi chi tiết còn đang tải. */
  itemName: string;
  onClose: () => void;
};

function detailItems(item: UnpaidItemDetail) {
  return [
    {
      label: "Bệnh nhân",
      value: (
        <>
          {item.patient.fullName}
          {item.patient.phone ? (
            <span className="block text-xs text-muted-foreground tabular-nums">
              {item.patient.phone}
            </span>
          ) : null}
        </>
      ),
    },
    {
      label: "Lượt khám / Lịch hẹn",
      value: (
        <span className="tabular-nums">
          {item.reference.code} · {formatDate(item.reference.date)}
          <span className="block text-xs text-muted-foreground">
            {UNPAID_ITEM_REFERENCE_LABELS[item.reference.type]}
          </span>
        </span>
      ),
    },
    { label: "Khoản", value: item.itemName },
    {
      label: "Giá",
      value: (
        <span className="tabular-nums">{formatCurrencyVnd(item.priceVnd)}</span>
      ),
    },
    {
      label: "Thời gian chỉ định",
      value: (
        <span className="tabular-nums">{formatDateTime(item.orderedAt)}</span>
      ),
    },
    // Phí khám không do bác sĩ chỉ định.
    { label: "Bác sĩ chỉ định", value: item.orderedByDoctorName ?? "—" },
  ];
}

export function UnpaidItemDetailSheet({
  itemId,
  itemName,
  onClose,
}: UnpaidItemDetailSheetProps) {
  const { detail, loading, error, retryable, reload } =
    useUnpaidItemDetail(itemId);
  return (
    <BillingDetailSheetFrame
      title={itemName}
      description={
        detail
          ? UNPAID_ITEM_KIND_LABELS[detail.kind]
          : "Khoản đang chờ thanh toán, chỉ để xem."
      }
      loading={loading}
      error={error}
      onRetry={retryable ? reload : undefined}
      onClose={onClose}
    >
      {detail ? <DetailList items={detailItems(detail)} /> : null}
    </BillingDetailSheetFrame>
  );
}
