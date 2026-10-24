"use client";

import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { messageFromError } from "@/lib/messages/appMessages";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import { usePayableSources } from "../../hooks/usePayableSources";
import { PaymentErrorBlock } from "./PaymentErrorBlock";
import { LIST_LINK_CLASS } from "./patientBillingPresentation";

/**
 * Dòng tóm tắt khoản cần thanh toán cho trang chủ khu bệnh nhân: số khoản, tổng tiền và lối tới
 * trang Thanh toán. Không có khoản nào thì không hiện, để trang chủ gọn.
 */
export function UnpaidSummaryLine() {
  const { sources, loading, error, reload } = usePayableSources();

  if (loading) return <Skeleton aria-hidden className="h-14 w-full" />;
  if (error) return <PaymentErrorBlock message={messageFromError(error)} onRetry={reload} />;

  const count = (sources ?? []).reduce((sum, source) => sum + source.pendingCount, 0);
  const amount = (sources ?? []).reduce((sum, source) => sum + source.pendingAmount, 0);
  if (count === 0) return null;

  return (
    <Link
      className={cn(LIST_LINK_CLASS, "rounded-lg border bg-card")}
      href={PATIENT_ROUTES.payments}
    >
      <span className="min-w-0">
        <span className="block font-medium">Bạn có {count} khoản cần thanh toán</span>
        <span className="block text-sm tabular-nums text-muted-foreground">
          Tổng {formatCurrencyVnd(amount)}
        </span>
      </span>
      <ChevronRight aria-hidden className="size-5 shrink-0 text-muted-foreground" />
    </Link>
  );
}
