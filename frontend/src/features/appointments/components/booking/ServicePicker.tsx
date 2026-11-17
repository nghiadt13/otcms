"use client";

import { useId, useState } from "react";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Skeleton } from "@/components/ui/skeleton";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import type { BookableService, BookingVariant } from "../../types/booking";
import { BookingLoadError } from "./BookingLoadError";
import { bookingSize } from "./bookingSizes";

// Chọn dịch vụ Khám. Chỉ hiển thị danh sách BE trả về (BE đã lọc loại Khám), không lọc lại ở FE.

export function ServicePicker({
  services,
  loading,
  failed,
  value,
  onChange,
  onRetry,
  variant,
  label,
}: {
  services: readonly BookableService[];
  loading: boolean;
  failed: boolean;
  value: string | null;
  onChange: (serviceId: string) => void;
  onRetry: () => void;
  variant: BookingVariant;
  /** Tên nhóm cho trình đọc màn hình. */
  label: string;
}) {
  const sizes = bookingSize(variant);
  const groupId = useId();
  const [expandedId, setExpandedId] = useState<string | null>(null);

  if (loading)
    return (
      <div className="grid gap-2" aria-hidden>
        <Skeleton className="h-14 w-full rounded-lg" />
        <Skeleton className="h-14 w-full rounded-lg" />
      </div>
    );
  if (failed)
    return (
      <BookingLoadError
        message="Không tải được danh sách dịch vụ."
        onRetry={onRetry}
        variant={variant}
      />
    );
  if (services.length === 0)
    return (
      <p className={`${sizes.body} text-muted-foreground`}>
        Phòng khám chưa mở đặt lịch trực tuyến cho dịch vụ khám. Vui lòng gọi
        hotline.
      </p>
    );

  return (
    <RadioGroup
      aria-label={label}
      value={value ?? ""}
      onValueChange={(next: string) => onChange(next)}
      className="gap-0 divide-y rounded-lg border"
    >
      {services.map((service) => {
        const id = `${groupId}-${service.id}`;
        return (
          <div
            key={service.id}
            className={`flex items-start gap-3 ${sizes.row}`}
          >
            <RadioGroupItem id={id} value={service.id} />
            <Label htmlFor={id} className="sr-only">{service.name}</Label>
            <div className="grid min-w-0 flex-1 gap-1">
              <button type="button" className={`${sizes.strong} text-left`} aria-expanded={expandedId === service.id}
                aria-controls={`${id}-details`}
                onClick={() => { if (value !== service.id) onChange(service.id); setExpandedId((current) => current === service.id ? null : service.id); }}>
                {service.name}
              </button>
              {expandedId === service.id ? (
                <div id={`${id}-details`} className="grid gap-1">
                  <span className={`${sizes.body} tabular-nums`}>{formatCurrencyVnd(service.price)}</span>
                  {service.bookingDetails ? <span className={`${sizes.detail} whitespace-pre-line`}>{service.bookingDetails}</span> : null}
                </div>
              ) : null}
            </div>
          </div>
        );
      })}
    </RadioGroup>
  );
}
