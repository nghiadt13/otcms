"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { ProfileScopeSelect } from "@/components/patient/ProfileScopeSelect";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { isBillingDateRangeValid } from "../../schema/billingDateRangeRules";
import { PaymentBackLink } from "./PaymentBackLink";
import { PaymentHistoryList } from "./PaymentHistoryList";
import { historyHref } from "./patientBillingPresentation";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";

const RANGE_ERROR_ID = "payment-history-range-error";

/**
 * Lịch sử thanh toán (UC56): lọc từ ngày – đến ngày, người khám, rồi danh sách. Khoảng ngày sai
 * bị chặn ngay ở form (MSG-54) và không gọi API; bộ lọc đang áp dụng nằm trên URL.
 */
export function MyPaymentHistory({
  from,
  to,
  profileId,
}: {
  from?: string;
  to?: string;
  profileId?: string;
}) {
  const router = useRouter();
  const profiles = useLinkedProfiles();
  const [draft, setDraft] = useState({ from: from ?? "", to: to ?? "" });
  const [submitted, setSubmitted] = useState(false);

  const urlRangeValid = isBillingDateRangeValid(from, to);
  const draftRangeValid = isBillingDateRangeValid(
    draft.from || undefined,
    draft.to || undefined,
  );
  const showRangeError = !urlRangeValid || (submitted && !draftRangeValid);

  function applyFilter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitted(true);
    if (!draftRangeValid) return;
    router.replace(historyHref({ from: draft.from, to: draft.to, profileId }));
  }

  return (
    <div className="space-y-8">
      <PaymentBackLink href={PATIENT_ROUTES.payments} label="Thanh toán" />
      <PortalPageHeader
        description="Các khoản đã thanh toán thành công. Chọn một dòng để xem phiếu thu."
        title="Lịch sử thanh toán"
      />

      <ProfileScopeSelect
        onChange={(next) =>
          router.replace(historyHref({ from, to, profileId: next }))
        }
        profiles={profiles.profiles}
        value={profileId}
      />

      <form
        className="grid gap-4 sm:grid-cols-[1fr_1fr_auto] sm:items-end"
        onSubmit={applyFilter}
      >
        <div className="grid gap-2">
          <Label
            className="text-base font-medium"
            htmlFor="payment-history-from"
          >
            Từ ngày
          </Label>
          <Input
            aria-describedby={showRangeError ? RANGE_ERROR_ID : undefined}
            aria-invalid={showRangeError || undefined}
            className="h-12 md:text-base"
            id="payment-history-from"
            onChange={(event) =>
              setDraft((current) => ({ ...current, from: event.target.value }))
            }
            type="date"
            value={draft.from}
          />
        </div>
        <div className="grid gap-2">
          <Label className="text-base font-medium" htmlFor="payment-history-to">
            Đến ngày
          </Label>
          <Input
            aria-describedby={showRangeError ? RANGE_ERROR_ID : undefined}
            aria-invalid={showRangeError || undefined}
            className="h-12 md:text-base"
            id="payment-history-to"
            onChange={(event) =>
              setDraft((current) => ({ ...current, to: event.target.value }))
            }
            type="date"
            value={draft.to}
          />
        </div>
        <button className={publicButtonClass("secondary")} type="submit">
          Áp dụng
        </button>
      </form>
      {showRangeError ? (
        <p
          className="text-sm text-destructive"
          id={RANGE_ERROR_ID}
          role="alert"
        >
          {formatAppMessage("MSG-54")}
        </p>
      ) : null}

      {urlRangeValid ? (
        <PaymentHistoryList
          // Đổi bộ lọc trên URL thì dựng lại danh sách để tải từ trang đầu.
          filters={{
            from: from ?? "",
            to: to ?? "",
            patientId: profileId ?? "",
          }}
          key={`${from ?? ""}|${to ?? ""}|${profileId ?? ""}`}
        />
      ) : null}
    </div>
  );
}
