"use client";

import Link from "next/link";
import { useState } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PortalEmptyState } from "@/components/patient/PortalEmptyState";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { messageFromError } from "@/lib/messages/appMessages";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import { usePayableSource } from "../../hooks/usePayableSource";
import type { PaymentSourceKind } from "../../types/patientBilling";
import { PaymentBackLink } from "./PaymentBackLink";
import { PaymentErrorBlock } from "./PaymentErrorBlock";
import { PayableItemRow } from "./PayableItemRow";
import {
  COUNTER_PAYMENT_HINT,
  LIST_FRAME_CLASS,
  payableSourceTitle,
  paymentConfirmHref,
  sumSelectedPending,
} from "./patientBillingPresentation";

/**
 * Chi tiết một nguồn (UC53): các khoản, chọn khoản chờ thanh toán, tạm tính và đi tới xác nhận.
 * Không có nút trả một phần: chỉ trọn từng khoản được chọn (plan phase 06 §2).
 */
export function PayableSourceDetail({ kind, id }: { kind: PaymentSourceKind; id: string }) {
  const { source, loading, error, reload } = usePayableSource(kind, id);
  const [selected, setSelected] = useState<ReadonlySet<string>>(() => new Set());

  const toggle = (itemId: string) =>
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(itemId)) next.delete(itemId);
      else next.add(itemId);
      return next;
    });

  // Chỉ khoản đang chờ và còn được chọn mới vào giao dịch; giữ thứ tự của nguồn.
  const chosenIds = source
    ? source.items
        .filter((item) => item.status === "PENDING_PAYMENT" && selected.has(item.id))
        .map((item) => item.id)
    : [];
  const subtotal = source ? sumSelectedPending(source.items, selected) : 0;

  return (
    <div className="space-y-8">
      <PaymentBackLink href={PATIENT_ROUTES.payments} label="Thanh toán" />
      <PortalPageHeader
        description="Chọn các khoản cần trả. Mỗi lần thanh toán chỉ gồm khoản của một lịch hẹn hoặc một lượt khám."
        title={source ? payableSourceTitle(source) : "Khoản cần thanh toán"}
      />

      {loading ? (
        <div aria-busy="true" className="space-y-3">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      ) : error ? (
        <PaymentErrorBlock message={messageFromError(error)} onRetry={reload} />
      ) : source && source.items.length === 0 ? (
        <PortalEmptyState>Nguồn này chưa có khoản cần thanh toán.</PortalEmptyState>
      ) : source ? (
        <div className="space-y-6">
          <ul className={LIST_FRAME_CLASS}>
            {source.items.map((item) => (
              <PayableItemRow
                checked={selected.has(item.id)}
                item={item}
                key={item.id}
                onToggle={toggle}
              />
            ))}
          </ul>

          <div className="flex items-center justify-between gap-4 text-base">
            <span>Tạm tính</span>
            <span className="text-lg font-semibold tabular-nums">{formatCurrencyVnd(subtotal)}</span>
          </div>

          <div className="space-y-3">
            {chosenIds.length > 0 ? (
              <Link
                className={cn(publicButtonClass("primary"), "w-full sm:w-auto")}
                href={paymentConfirmHref(kind, id, chosenIds)}
              >
                Thanh toán qua VNPay
              </Link>
            ) : null}
            <p className="text-sm text-muted-foreground">{COUNTER_PAYMENT_HINT}</p>
          </div>
        </div>
      ) : null}
    </div>
  );
}
