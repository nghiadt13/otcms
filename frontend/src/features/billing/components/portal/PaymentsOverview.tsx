"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { ProfileScopeSelect } from "@/components/patient/ProfileScopeSelect";
import { Skeleton } from "@/components/ui/skeleton";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { messageFromError } from "@/lib/messages/appMessages";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { useMyPaymentHistory } from "../../hooks/useMyPaymentHistory";
import { usePayableSources } from "../../hooks/usePayableSources";
import { PaymentErrorBlock } from "./PaymentErrorBlock";
import { PaymentRow } from "./PaymentRow";
import { PayableSourceList } from "./PayableSourceList";
import { LIST_FRAME_CLASS, historyHref, paymentsHref } from "./patientBillingPresentation";

/** Số dòng "Đã thanh toán gần đây" trên trang tổng quan. */
const RECENT_COUNT = 3;

/** Trang "Thanh toán": khoản cần trả theo nguồn, và vài thanh toán gần đây. */
export function PaymentsOverview({ profileId }: { profileId?: string }) {
  const router = useRouter();
  const profiles = useLinkedProfiles();
  const sources = usePayableSources(profileId);

  return (
    <div className="space-y-8">
      <PortalPageHeader
        actions={
          <Link className={publicButtonClass("secondary")} href={PATIENT_ROUTES.paymentHistory}>
            Lịch sử thanh toán
          </Link>
        }
        description="Khoản cần trả của bạn và người thân đã gắn hồ sơ."
        title="Thanh toán"
      />

      <ProfileScopeSelect
        onChange={(next) => router.replace(paymentsHref(next))}
        profiles={profiles.profiles}
        value={profileId}
      />

      <section aria-labelledby="payments-due-heading" className="space-y-3">
        <h2 className="text-lg font-semibold" id="payments-due-heading">
          Cần thanh toán
        </h2>
        <PayableSourceList
          error={sources.error}
          loading={sources.loading}
          onRetry={sources.reload}
          sources={sources.sources}
        />
      </section>

      {/* Khóa theo người khám để đổi hồ sơ thì tải lại danh sách (hook chỉ đọc bộ lọc lúc đầu). */}
      <RecentPayments key={profileId ?? "all"} profileId={profileId} />
    </div>
  );
}

function RecentPayments({ profileId }: { profileId?: string }) {
  const recent = useMyPaymentHistory(
    { from: "", to: "", patientId: profileId ?? "" },
    RECENT_COUNT,
  );
  const payments = recent.data?.items ?? [];

  return (
    <section aria-labelledby="payments-recent-heading" className="space-y-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-lg font-semibold" id="payments-recent-heading">
          Đã thanh toán gần đây
        </h2>
        <Link
          className="text-base font-medium text-primary underline-offset-4 hover:underline"
          href={historyHref({ profileId })}
        >
          Xem tất cả lịch sử
        </Link>
      </div>
      {recent.loading ? (
        <div aria-busy="true" className="space-y-3">
          <Skeleton className="h-14 w-full" />
          <Skeleton className="h-14 w-full" />
        </div>
      ) : recent.error ? (
        <PaymentErrorBlock message={messageFromError(recent.error)} onRetry={recent.reload} />
      ) : payments.length === 0 ? (
        <p className="rounded-lg border bg-card px-4 py-6 text-base text-muted-foreground">
          Chưa có thanh toán nào. Thanh toán thành công sẽ hiện ở đây.
        </p>
      ) : (
        <ul className={LIST_FRAME_CLASS}>
          {payments.map((payment) => (
            <PaymentRow key={payment.id} payment={payment} />
          ))}
        </ul>
      )}
    </section>
  );
}
