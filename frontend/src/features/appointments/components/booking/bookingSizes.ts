import type { BookingVariant } from "../../types/booking";

// Cỡ chữ và chạm của luồng đặt lịch. `patient` = khu bệnh nhân (chữ như màn xác thực, ô và nút
// h-12, vùng chạm ≥ 44px). `staff` = khu Lễ tân (công cụ, dày thông tin). Chỉ đổi cỡ, không đổi logic.

export const BOOKING_SIZES = {
  patient: {
    title: "text-lg font-semibold",
    body: "text-base",
    strong: "text-base font-medium",
    detail: "text-sm text-muted-foreground",
    control: "h-12 text-base",
    cell: "w-16 min-h-16",
    row: "min-h-12 px-4 py-3",
    grid: "grid-cols-3 sm:grid-cols-4 md:grid-cols-6",
  },
  staff: {
    title: "text-sm font-semibold",
    body: "text-sm",
    strong: "text-sm font-medium",
    detail: "text-xs text-muted-foreground",
    control: "h-9 text-sm",
    cell: "w-14 min-h-14",
    row: "min-h-9 px-3 py-2",
    grid: "grid-cols-4 sm:grid-cols-6",
  },
} as const satisfies Record<BookingVariant, Record<string, string>>;

export const bookingSize = (variant: BookingVariant) => BOOKING_SIZES[variant];
