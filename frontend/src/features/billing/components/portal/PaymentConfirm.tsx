"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError, isApiError } from "@/lib/api/apiError";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import { notifyError } from "@/lib/messages/notify";
import { cn } from "@/lib/utils";
import { createOnlinePayment } from "../../api/patientBillingApi";
import { usePayableSource } from "../../hooks/usePayableSource";
import type { PaymentItem, PaymentSourceKind } from "../../types/patientBilling";
import { PaymentBackLink } from "./PaymentBackLink";
import { PaymentErrorBlock } from "./PaymentErrorBlock";
import {
  NO_REFUND_NOTICE,
  pickPayableItems,
  sourceDetailHref,
} from "./patientBillingPresentation";

/**
 * Xác nhận trước khi sang VNPay (UC55 bước 2–3). Chỉ chuyển trang khi người dùng đã đồng ý, và
 * chỉ khi BE tính đúng tổng đã hiện. Kết quả thanh toán do trang kết quả đọc từ BE.
 */
export function PaymentConfirm({
  kind,
  id,
  itemIds,
}: {
  kind: PaymentSourceKind;
  id: string;
  itemIds: string[];
}) {
  const router = useRouter();
  const { source, loading, error, reload } = usePayableSource(kind, id);
  const [agreed, setAgreed] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const detailHref = sourceDetailHref(kind, id);

  // Mọi khoản được chọn phải là khoản chờ thanh toán của đúng nguồn này; thiếu một khoản thì chặn.
  const eligibleItems: PaymentItem[] | null = source
    ? pickPayableItems(source.items, itemIds)
    : null;
  const previewTotal = eligibleItems
    ? eligibleItems.reduce((sum, item) => sum + item.amount, 0)
    : 0;

  async function submit() {
    if (!source || !eligibleItems || submitting) return;
    setSubmitting(true);
    try {
      const response = await createOnlinePayment({ sourceKind: kind, sourceId: id, itemIds });
      // Tổng BE khác tổng đã hiện: không chuyển sang cổng, quay về chi tiết để người dùng thấy lại.
      if (response.total !== previewTotal) {
        setSubmitting(false);
        notifyError(new ApiError(409, { code: "MSG-49" }));
        router.replace(detailHref);
        return;
      }
      window.location.assign(response.redirectUrl);
    } catch (err) {
      setSubmitting(false);
      notifyError(err);
      // 409: khoản đã trả hoặc đang thanh toán ở nơi khác, về chi tiết để tải lại.
      if (isApiError(err) && err.status === 409) router.replace(detailHref);
    }
  }

  return (
    <div className="space-y-8">
      <PaymentBackLink href={detailHref} label="Chọn khoản" />
      <PortalPageHeader title="Xác nhận thanh toán" />

      {loading ? (
        <div aria-busy="true" className="space-y-3">
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      ) : error ? (
        <PaymentErrorBlock message={messageFromError(error)} onRetry={reload} />
      ) : !source || !eligibleItems ? (
        <div className="space-y-4 rounded-lg border bg-card px-4 py-4">
          <p className="text-base text-destructive" role="alert">
            {itemIds.length === 0
              ? "Bạn chưa chọn khoản nào để thanh toán."
              : formatAppMessage("MSG-49")}
          </p>
          <Link className={publicButtonClass("primary")} href={detailHref}>
            Chọn lại khoản
          </Link>
        </div>
      ) : (
        <div className="space-y-8">
          <dl className="space-y-4 rounded-lg border bg-card px-4 py-4 text-base">
            <div className="space-y-1">
              <dt className="text-sm text-muted-foreground">Người được khám</dt>
              <dd className="font-medium">
                {source.patient.fullName} ({CARE_RELATIONSHIP_LABELS[source.patient.relationship]})
              </dd>
            </div>
            <div className="space-y-2">
              <dt className="text-sm text-muted-foreground">Khoản</dt>
              <dd>
                <ul className="space-y-2">
                  {eligibleItems.map((item) => (
                    <li className="flex items-start justify-between gap-4" key={item.id}>
                      <span>{item.label}</span>
                      <span className="shrink-0 tabular-nums">{formatCurrencyVnd(item.amount)}</span>
                    </li>
                  ))}
                </ul>
              </dd>
            </div>
            <div className="flex items-center justify-between gap-4 border-t pt-4">
              <dt className="font-semibold">Tổng</dt>
              <dd className="text-lg font-semibold tabular-nums">{formatCurrencyVnd(previewTotal)}</dd>
            </div>
          </dl>

          <p className="rounded-lg bg-warning-soft px-4 py-4 text-base text-warning">
            {NO_REFUND_NOTICE}
          </p>

          <div className="flex items-start gap-3">
            <span className="flex size-11 shrink-0 items-center justify-center">
              <Checkbox
                checked={agreed}
                className="size-6"
                disabled={submitting}
                id="payment-agree"
                onCheckedChange={(checked) => setAgreed(checked)}
              />
            </span>
            <Label className="pt-2.5 text-base font-normal" htmlFor="payment-agree">
              Tôi đã đọc và đồng ý.
            </Label>
          </div>

          <div className="flex flex-col gap-3 sm:flex-row">
            <button
              aria-busy={submitting}
              className={cn(publicButtonClass("primary"), "sm:w-auto")}
              disabled={!agreed || submitting}
              onClick={submit}
              type="button"
            >
              {submitting ? "Đang chuyển sang VNPay..." : "Chuyển sang VNPay"}
            </button>
            <Link className={publicButtonClass("secondary")} href={detailHref}>
              Quay lại
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}
