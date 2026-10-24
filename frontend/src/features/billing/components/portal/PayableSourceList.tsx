import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { PortalEmptyState } from "@/components/patient/PortalEmptyState";
import { Skeleton } from "@/components/ui/skeleton";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { messageFromError } from "@/lib/messages/appMessages";
import type { PayableSourceSummary } from "../../types/patientBilling";
import { PaymentErrorBlock } from "./PaymentErrorBlock";
import {
  LIST_FRAME_CLASS,
  LIST_LINK_CLASS,
  payableSourceTitle,
  sourceDetailHref,
} from "./patientBillingPresentation";

/** Dòng tóm tắt: lịch hẹn chỉ có phí khám; lượt khám ghi số khoản chờ thanh toán. */
function summaryLine(source: PayableSourceSummary): string {
  const amount = formatCurrencyVnd(source.pendingAmount);
  return source.kind === "appointment"
    ? `Phí khám · ${amount}`
    : `${source.pendingCount} khoản chờ thanh toán · ${amount}`;
}

/** Danh sách nguồn cần thanh toán: đủ bốn trạng thái, cả dòng là link tới chi tiết nguồn. */
export function PayableSourceList({
  sources,
  loading,
  error,
  onRetry,
}: {
  sources: PayableSourceSummary[] | null;
  loading: boolean;
  error: Error | null;
  onRetry: () => void;
}) {
  if (loading) {
    return (
      <div aria-busy="true" className={`${LIST_FRAME_CLASS} p-0`}>
        {[0, 1].map((row) => (
          <div className="px-4 py-4" key={row}>
            <Skeleton className="h-5 w-3/4" />
            <Skeleton className="mt-2 h-4 w-1/2" />
          </div>
        ))}
      </div>
    );
  }
  if (error) return <PaymentErrorBlock message={messageFromError(error)} onRetry={onRetry} />;
  if (!sources || sources.length === 0) {
    return (
      <PortalEmptyState>
        Bạn không có khoản nào cần thanh toán. Phí khám và chỉ định sẽ hiện ở đây khi cần trả tiền.
      </PortalEmptyState>
    );
  }
  return (
    <ul className={LIST_FRAME_CLASS}>
      {sources.map((source) => (
        <li key={`${source.kind}:${source.id}`}>
          <Link className={LIST_LINK_CLASS} href={sourceDetailHref(source.kind, source.id)}>
            <span className="min-w-0">
              <span className="block font-medium">{payableSourceTitle(source)}</span>
              <span className="block text-sm text-muted-foreground tabular-nums">
                {summaryLine(source)}
              </span>
            </span>
            <ChevronRight aria-hidden className="size-5 shrink-0 text-muted-foreground" />
          </Link>
        </li>
      ))}
    </ul>
  );
}
