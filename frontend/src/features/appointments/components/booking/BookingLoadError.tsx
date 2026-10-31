"use client";

import { Button } from "@/components/ui/button";
import type { BookingVariant } from "../../types/booking";
import { bookingSize } from "./bookingSizes";

/** Lỗi tải của một khối: nói rõ chuyện gì xảy ra và nút "Thử lại". */
export function BookingLoadError({
  message,
  onRetry,
  variant,
}: {
  message: string;
  onRetry: () => void;
  variant: BookingVariant;
}) {
  const sizes = bookingSize(variant);
  return (
    <div className="grid justify-items-start gap-3">
      <p role="alert" className={`${sizes.body} text-destructive`}>
        {message}
      </p>
      <Button
        type="button"
        variant="outline"
        className={`${sizes.control} px-4`}
        onClick={onRetry}
      >
        Thử lại
      </Button>
    </div>
  );
}
