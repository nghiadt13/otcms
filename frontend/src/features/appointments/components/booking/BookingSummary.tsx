"use client";

import { Button } from "@/components/ui/button";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { longStartLabel } from "../../schema/bookingTime";
import type { BookingVariant } from "../../types/booking";
import { bookingSize } from "./bookingSizes";

// Khối "Kiểm tra lại": tóm tắt lựa chọn, phí khám (giá BE trả, không tính ở FE) và nút đặt. Nút
// đặt là nút `default` duy nhất của luồng; bật khi đủ người, dịch vụ, giờ.

function SummaryRow({
  label,
  value,
  variant,
}: {
  label: string;
  value: string;
  variant: BookingVariant;
}) {
  const sizes = bookingSize(variant);
  return (
    <div className="grid gap-0.5 sm:grid-cols-[9rem_1fr] sm:gap-4">
      <dt className={sizes.detail}>{label}</dt>
      <dd className={`${sizes.strong} min-w-0 break-words`}>{value}</dd>
    </div>
  );
}

export function BookingSummary({
  variant,
  patientLabel,
  serviceName,
  price,
  startAt,
  doctorName,
  ready,
  submitting,
  onSubmit,
}: {
  variant: BookingVariant;
  /** Tên và quan hệ người khám, ví dụ "Nguyễn Văn An · Bản thân". */
  patientLabel: string;
  serviceName: string;
  /** VND, giá dịch vụ BE trả. */
  price: number;
  startAt: string | null;
  /** null = phòng khám sắp xếp. */
  doctorName: string | null;
  ready: boolean;
  submitting: boolean;
  onSubmit: () => void;
}) {
  const sizes = bookingSize(variant);
  return (
    <div className="grid gap-5">
      <dl className={`grid gap-3 ${sizes.body}`}>
        <SummaryRow variant={variant} label="Người khám" value={patientLabel} />
        <SummaryRow variant={variant} label="Dịch vụ" value={serviceName} />
        <SummaryRow
          variant={variant}
          label="Thời gian"
          value={startAt ? longStartLabel(startAt) : "Chưa chọn giờ"}
        />
        <SummaryRow
          variant={variant}
          label="Bác sĩ"
          value={doctorName ?? "Phòng khám sắp xếp bác sĩ"}
        />
        <SummaryRow
          variant={variant}
          label="Phí khám"
          value={formatCurrencyVnd(price)}
        />
      </dl>
      <p className={sizes.detail}>
        Thanh toán trực tuyến sau khi đặt hoặc tại quầy khi đến.
      </p>
      <Button
        type="button"
        className={`w-full ${sizes.control}`}
        disabled={!ready || submitting}
        onClick={onSubmit}
      >
        {submitting ? "Đang đặt lịch..." : "Đặt lịch khám"}
      </Button>
    </div>
  );
}
