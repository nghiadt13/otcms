"use client";

import { useState, type ReactNode } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { useCurrentAccount } from "@/hooks/useCurrentAccount";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import Link from "next/link";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import type { BookedAppointment } from "../../types/booking";
import { BookingFlow } from "../booking/BookingFlow";
import { BookingSignInPrompt } from "../booking/wizard/BookingSignInPrompt";
import { PatientBookingResult } from "./PatientBookingResult";
import { PatientLoadError } from "./PatientLoadError";
import { PendingPaymentNotice } from "./PendingPaymentNotice";

type PaymentRenderer = (args: {
  appointment: BookedAppointment;
  onFinish: (result: { paid: boolean }) => void;
}) => ReactNode;

// Đặt lịch khu bệnh nhân (UC20, UC21, AF1). Lõi luồng là BookingFlow dùng chung (variant patient);
// trang lấy tài khoản đang đăng nhập làm người khám và đổi sang kết quả sau khi đặt xong.

export function PatientBookingPage({
  renderPayment,
}: {
  renderPayment?: PaymentRenderer;
}) {
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
  return <SignedInBooking renderPayment={renderPayment} />;
}

function SignedInBooking({
  renderPayment,
}: {
  renderPayment?: PaymentRenderer;
}) {
  const [booked, setBooked] = useState<BookedAppointment | null>(null);
  const linked = useLinkedProfiles();
  if (booked) return <PatientBookingResult appointment={booked} />;
  if (linked.loading) return <BookingSkeleton />;
  if (linked.error)
    return <PatientLoadError message={linked.error} onRetry={linked.reload} />;
  if (linked.profiles.length === 0)
    return (
      <div className="space-y-4 rounded-lg border bg-card p-5">
        <p>Hãy tạo hoặc gắn hồ sơ bệnh nhân trước khi đặt lịch.</p>
        <Link
          className={publicButtonClass("primary")}
          href={PATIENT_ROUTES.newProfile}
        >
          Thêm thành viên
        </Link>
      </div>
    );

  return (
    <BookingFlow
      onBooked={setBooked}
      renderPayment={renderPayment}
      notice={renderPayment ? <PendingPaymentNotice /> : undefined}
      patients={linked.profiles.map((profile) => ({
        id: profile.id,
        fullName: profile.fullName,
        dateOfBirth: profile.dateOfBirth,
        relationship: profile.relationship,
      }))}
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
