"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { type ReactNode, useCallback, useEffect, useRef } from "react";
import { STAFF_ROUTES } from "@/lib/routes/staffRoutes";
import { StaffBookingBody } from "./StaffBookingBody";

type StaffBookingPanelProps = {
  /** Ô tìm bệnh nhân (PatientLookup, route ghép ở app/). Hiện phía trên khối đặt lịch. */
  lookupSlot: ReactNode;
};

/**
 * Đọc `?patientId=` trên trang đặt lịch: có mã thì mở luồng đặt cho người đó. Đổi người = xóa mã
 * khỏi URL và đưa con trỏ về ô tìm. Phím "/" (khi không đang gõ) cũng đưa con trỏ về ô SĐT.
 */
export function StaffBookingPanel({ lookupSlot }: StaffBookingPanelProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const patientId = searchParams.get("patientId")?.trim() || null;
  const lookupRef = useRef<HTMLDivElement>(null);

  const focusPhoneField = useCallback(() => {
    const root = lookupRef.current;
    const field =
      root?.querySelector<HTMLInputElement>('input[type="tel"]') ??
      root?.querySelector<HTMLInputElement>("input");
    field?.focus();
  }, []);

  const clearPatient = useCallback(() => {
    router.replace(STAFF_ROUTES.newAppointment);
    focusPhoneField();
  }, [router, focusPhoneField]);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key !== "/" || event.ctrlKey || event.metaKey || event.altKey) return;
      if (isTypingTarget(event.target)) return;
      event.preventDefault();
      focusPhoneField();
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [focusPhoneField]);

  return (
    <div className="grid gap-6">
      <div ref={lookupRef}>{lookupSlot}</div>
      {patientId ? (
        // key theo mã: đổi người thì dựng lại toàn bộ trạng thái (kể cả lịch vừa đặt).
        <StaffBookingBody
          key={patientId}
          patientId={patientId}
          onChangePatient={clearPatient}
        />
      ) : null}
    </div>
  );
}

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return (
    target.isContentEditable ||
    ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName)
  );
}
