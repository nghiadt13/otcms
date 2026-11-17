"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import type { BookedAppointment, PaymentRenderer } from "../../types/booking";

/**
 * Bước 5 (tùy chọn) của wizard đặt lịch: lịch đã xác nhận, phí khám còn chờ trả. Bệnh nhân chọn
 * trả ngay (màn PayOS được ghép ở app) hoặc trả sau. Không giữ giờ: rời bước không hủy lịch.
 */
export function PaymentOfferStep({
  appointment,
  renderPayment,
  onFinish,
  onLater,
}: {
  appointment: BookedAppointment;
  renderPayment: PaymentRenderer;
  /** Đã thanh toán xong. */
  onFinish: (result: { paid: boolean }) => void;
  /** Trả sau: lịch giữ nguyên, phí trả trước giờ check-in. */
  onLater: () => void;
}) {
  const [payNow, setPayNow] = useState(false);
  if (payNow) return <>{renderPayment({ appointment, onFinish })}</>;

  return (
    <section
      aria-label="Thanh toán phí khám"
      className="space-y-4 rounded-xl border bg-card p-5"
    >
      <div className="space-y-2">
        <h3 className="text-lg font-semibold">Thanh toán phí khám</h3>
        <p className="text-base text-muted-foreground">
          Lịch đã được xác nhận. Bạn có thể thanh toán ngay bằng PayOS, hoặc trả
          sau trước giờ khám tại quầy lễ tân hoặc trong khu bệnh nhân.
        </p>
      </div>
      <div className="flex flex-wrap gap-3">
        <Button onClick={() => setPayNow(true)}>Thanh toán ngay</Button>
        <Button variant="outline" onClick={onLater}>
          Trả sau
        </Button>
      </div>
    </section>
  );
}
