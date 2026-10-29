"use client";

import { useState } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { useCurrentAccount } from "@/hooks/useCurrentAccount";
import type { CurrentAccount } from "@/lib/auth/currentAccountApi";
import type { BookedAppointment } from "../../types/booking";
import { BookingFlow } from "../booking/BookingFlow";
import { BookingSignInPrompt } from "../booking/wizard/BookingSignInPrompt";
import { PatientBookingResult } from "./PatientBookingResult";
import { PatientLoadError } from "./PatientLoadError";

// Đặt lịch khu bệnh nhân (UC20, UC21, AF1). Lõi luồng là BookingFlow dùng chung (variant patient);
// trang lấy tài khoản đang đăng nhập làm người khám và đổi sang kết quả sau khi đặt xong.

export function PatientBookingPage() {
  const account = useCurrentAccount();
  // Chưa đăng nhập: dừng ở bước 1 (đăng nhập / đăng ký), chưa gọi API nào cần phiên.
  if (account.status === "anonymous") return <BookingSignInPrompt />;
  if (account.status === "loading") return <BookingSkeleton />;
  if (account.status === "error")
    return (
      <PatientLoadError
        message="Không tải được thông tin tài khoản."
        onRetry={account.reload}
      />
    );
  return <SignedInBooking account={account.account} />;
}

// Đã đăng nhập: luôn vào thẳng bước 2. Người khám là chính tài khoản (họ tên từ `GET /api/v1/me`);
// backend tự dùng hồ sơ của tài khoản khi `patientProfileId` bỏ trống. Chưa có API hồ sơ người thân.
function SignedInBooking({ account }: { account: CurrentAccount }) {
  const [booked, setBooked] = useState<BookedAppointment | null>(null);
  if (booked) return <PatientBookingResult appointment={booked} />;

  return (
    <BookingFlow
      onBooked={setBooked}
      patients={[
        {
          id: account.id,
          fullName: account.displayName,
          dateOfBirth: null,
          relationship: null,
        },
      ]}
      variant="patient"
    />
  );
}

function BookingSkeleton() {
  return (
    <div aria-busy="true" className="space-y-3" role="status">
      <span className="sr-only">Đang tải thông tin tài khoản</span>
      <Skeleton className="h-16 w-full" />
      <Skeleton className="h-16 w-full" />
    </div>
  );
}
