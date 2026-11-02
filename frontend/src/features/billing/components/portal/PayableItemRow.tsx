import Link from "next/link";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Checkbox } from "@/components/ui/checkbox";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import type { PaymentItem } from "../../types/patientBilling";
import {
  INLINE_LINK_CLASS,
  PAYABLE_ITEM_STATUS_LABELS,
  PAYABLE_ITEM_STATUS_TONE,
} from "./patientBillingPresentation";

/**
 * Một khoản trong nguồn. Chỉ khoản chờ thanh toán được tick; khoản đã trả hoặc đã hủy hiện
 * checkbox bị khóa để người dùng thấy vì sao không chọn được.
 */
export function PayableItemRow({
  item,
  checked,
  onToggle,
}: {
  item: PaymentItem;
  checked: boolean;
  onToggle: (itemId: string) => void;
}) {
  const payable = item.status === "PENDING_PAYMENT";
  const inputId = `payable-item-${item.id}`;
  return (
    <li className="space-y-1 px-4 py-3">
      <div className="flex items-start gap-3">
        <span className="flex size-11 shrink-0 items-center justify-center">
          <Checkbox
            checked={payable && checked}
            className="size-6"
            disabled={!payable}
            id={inputId}
            onCheckedChange={() => onToggle(item.id)}
          />
        </span>
        <div className="min-w-0 flex-1 space-y-1 pt-2.5">
          <label
            className={cn(
              "block text-base font-medium",
              payable ? "cursor-pointer" : "text-muted-foreground",
            )}
            htmlFor={inputId}
          >
            {item.label}
          </label>
          <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-base">
            <span className="tabular-nums">
              {formatCurrencyVnd(item.amount)}
            </span>
            <StatusBadge tone={PAYABLE_ITEM_STATUS_TONE[item.status]}>
              {PAYABLE_ITEM_STATUS_LABELS[item.status]}
            </StatusBadge>
          </p>
          {item.cancellationReason ? (
            <p className="text-sm text-muted-foreground">
              {item.cancellationReason}
            </p>
          ) : null}
        </div>
      </div>
      {item.receipt ? (
        <div className="pl-14">
          <Link
            className={INLINE_LINK_CLASS}
            href={PATIENT_ROUTES.paymentDetail(item.receipt.paymentId)}
          >
            Phiếu thu {item.receipt.receiptNo}
          </Link>
        </div>
      ) : null}
    </li>
  );
}
