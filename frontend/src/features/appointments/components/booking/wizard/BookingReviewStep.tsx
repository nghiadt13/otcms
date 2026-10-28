"use client";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { longStartLabel } from "../../../schema/bookingTime";
import type { WizardStepKey } from "../../../schema/bookingWizard";

// Bước 4: gom lại mọi lựa chọn của ba bước trước để người dùng đối chiếu một lần. Mỗi dòng có
// "Sửa" đưa về đúng bước chứa lựa chọn đó. Giá là giá BE trả, FE không tính lại.

function ReviewRow({
  label,
  value,
  editStep,
  onGoTo,
}: {
  label: string;
  value: string;
  editStep: WizardStepKey;
  onGoTo: (step: WizardStepKey) => void;
}) {
  return (
    <div className="grid grid-cols-[1fr_auto] items-start gap-x-4 gap-y-1 px-4 py-3 sm:grid-cols-[9rem_1fr_auto]">
      <dt className="col-span-2 text-sm text-muted-foreground sm:col-span-1 sm:pt-0.5">
        {label}
      </dt>
      <dd className="min-w-0 break-words text-base font-medium">{value}</dd>
      <Button
        type="button"
        variant="link"
        className="h-auto min-h-11 px-0 text-base sm:min-h-0"
        aria-label={`Sửa ${label.toLowerCase()}`}
        onClick={() => onGoTo(editStep)}
      >
        Sửa
      </Button>
    </div>
  );
}

/** Khớp giới hạn của backend (`purpose` tối đa 1002 ký tự). */
export const PURPOSE_MAX_LENGTH = 1000;

export function BookingReviewStep({
  accountEmail,
  patientLabel,
  serviceName,
  price,
  startAt,
  doctorName,
  purpose,
  onPurposeChange,
  onGoTo,
}: {
  /** null khi chưa đọc được tài khoản: bỏ dòng này. */
  accountEmail: string | null;
  patientLabel: string;
  serviceName: string;
  price: number;
  startAt: string | null;
  /** null = phòng khám sắp xếp. */
  doctorName: string | null;
  purpose: string;
  onPurposeChange: (value: string) => void;
  onGoTo: (step: WizardStepKey) => void;
}) {
  return (
    <div className="grid gap-5">
      <dl className="divide-y overflow-hidden rounded-xl border">
        {accountEmail ? (
          <ReviewRow
            label="Tài khoản"
            value={accountEmail}
            editStep="account"
            onGoTo={onGoTo}
          />
        ) : null}
        <ReviewRow
          label="Người khám"
          value={patientLabel}
          editStep="patient"
          onGoTo={onGoTo}
        />
        <ReviewRow
          label="Dịch vụ"
          value={serviceName}
          editStep="schedule"
          onGoTo={onGoTo}
        />
        <ReviewRow
          label="Thời gian"
          value={startAt ? longStartLabel(startAt) : "Chưa chọn giờ"}
          editStep="schedule"
          onGoTo={onGoTo}
        />
        <ReviewRow
          label="Bác sĩ"
          value={doctorName ?? "Phòng khám sắp xếp bác sĩ"}
          editStep="schedule"
          onGoTo={onGoTo}
        />
        <div className="flex items-baseline justify-between gap-4 bg-secondary/60 px-4 py-4">
          <dt className="text-base font-medium">Phí khám</dt>
          <dd className="text-xl font-semibold tabular-nums">
            {formatCurrencyVnd(price)}
          </dd>
        </div>
      </dl>
      <div className="grid gap-2">
        <Label htmlFor="booking-purpose" className="text-base font-medium">
          Lý do khám
        </Label>
        <Textarea
          id="booking-purpose"
          value={purpose}
          maxLength={PURPOSE_MAX_LENGTH}
          rows={3}
          required
          placeholder="Ví dụ: đau gối phải 2 tuần, đau khi lên xuống cầu thang"
          className="min-h-24 text-base"
          aria-describedby="booking-purpose-count"
          onChange={(event) => onPurposeChange(event.target.value)}
        />
        <p
          id="booking-purpose-count"
          className="text-right text-sm tabular-nums text-muted-foreground"
        >
          {purpose.length}/{PURPOSE_MAX_LENGTH}
        </p>
      </div>
      <p className="text-base text-muted-foreground">
        Thanh toán trực tuyến sau khi đặt hoặc tại quầy khi đến.
      </p>
    </div>
  );
}
