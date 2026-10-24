"use client";

import Link from "next/link";
import { useState } from "react";
import { PortalNoProfileState } from "@/components/patient/PortalEmptyState";
import { Skeleton } from "@/components/ui/skeleton";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import type { BookedAppointment } from "../../types/booking";
import { BookingFlow } from "../booking/BookingFlow";
import { PatientBookingResult } from "./PatientBookingResult";
import { PatientLoadError } from "./PatientLoadError";

// Đặt lịch khu bệnh nhân (UC20, UC21, AF1). Lõi luồng là BookingFlow dùng chung (variant patient);
// trang chỉ lấy hồ sơ đã gắn, chặn khi chưa có hồ sơ và đổi sang kết quả sau khi đặt xong.
// `initialPatientId` (từ `?profileId=` của route) chỉ chọn sẵn người khám, BookingFlow kiểm lại.

export function PatientBookingPage({
  initialPatientId,
}: {
  initialPatientId?: string;
}) {
  const { profiles, loading, error, reload } = useLinkedProfiles();
  const [booked, setBooked] = useState<BookedAppointment | null>(null);

  if (loading) return <BookingSkeleton />;
  if (error)
    return (
      <PatientLoadError
        message={`Không tải được hồ sơ bệnh nhân. ${error}`}
        onRetry={reload}
      />
    );
  if (profiles.length === 0) return <PortalNoProfileState />;
  if (booked) return <PatientBookingResult appointment={booked} />;

  return (
    <div className="space-y-8">
      <p className="text-base">
        Chưa thấy người cần đặt?{" "}
        <Link
          className="inline-flex min-h-11 items-center font-medium text-primary underline-offset-4 hover:underline"
          href={PATIENT_ROUTES.newProfile}
        >
          Thêm hồ sơ bệnh nhân
        </Link>
      </p>
      <BookingFlow
        initialPatientId={initialPatientId}
        onBooked={setBooked}
        profiles={profiles}
        variant="patient"
      />
    </div>
  );
}

function BookingSkeleton() {
  return (
    <div aria-busy="true" className="space-y-3" role="status">
      <span className="sr-only">Đang tải hồ sơ bệnh nhân</span>
      <Skeleton className="h-16 w-full" />
      <Skeleton className="h-16 w-full" />
      <Skeleton className="h-16 w-full" />
    </div>
  );
}
