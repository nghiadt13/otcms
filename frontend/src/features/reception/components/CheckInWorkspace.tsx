"use client";

import { type ReactNode, useCallback, useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { PageHeader } from "@/components/PageHeader";
import { Sheet, SheetContent, SheetTitle } from "@/components/ui/sheet";
import { useMediaQuery } from "@/hooks/useMediaQuery";
import { STAFF_ROUTES } from "@/lib/routes/staffRoutes";
import { useTodayAppointments } from "../hooks/useTodayAppointments";
import type { CheckInTarget, TodayTab } from "../types/checkIn";
import { CheckInPanel } from "./CheckInPanel";
import { CheckInPlaceholder } from "./CheckInPanelStates";
import { TodayAppointmentList } from "./TodayAppointmentList";

// Từ lg (1024px) là bàn chia đôi: danh sách bên trái, panel cố định bên phải. Dưới lg panel mở
// trong Sheet full width. Đích đang chọn nằm ở URL (?appointmentId= hoặc ?patientId=).
const WIDE_QUERY = "(min-width: 1024px)";

function readTarget(params: URLSearchParams): CheckInTarget | null {
  const appointmentId = params.get("appointmentId");
  if (appointmentId) return { kind: "appointment", id: appointmentId };
  const patientId = params.get("patientId");
  return patientId ? { kind: "patient", id: patientId } : null;
}

/** Phím tắt "/" không được chen vào khi đang gõ trong ô nhập. */
function isTypingTarget(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement &&
    (target.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName))
  );
}

export function CheckInWorkspace({ lookupSlot }: { lookupSlot: ReactNode }) {
  const router = useRouter();
  const params = useSearchParams();
  const target = readTarget(params);
  const wide = useMediaQuery(WIDE_QUERY);
  const [tab, setTab] = useState<TodayTab>("BOOKED");
  const list = useTodayAppointments(tab);
  const lookupRef = useRef<HTMLDivElement>(null);
  const sheetOpen = !wide && target !== null;

  const clearSelection = useCallback(
    () => router.replace(STAFF_ROUTES.checkIn, { scroll: false }),
    [router],
  );

  const selectAppointment = useCallback(
    (appointmentId: string) =>
      router.replace(
        `${STAFF_ROUTES.checkIn}?${new URLSearchParams({ appointmentId }).toString()}`,
        { scroll: false },
      ),
    [router],
  );

  // Ô SĐT là ô nhập đầu tiên của PatientLookup; ưu tiên ô type="tel" nếu có.
  const focusPhoneField = useCallback(() => {
    const root = lookupRef.current;
    const field =
      root?.querySelector<HTMLInputElement>('input[type="tel"]') ??
      root?.querySelector<HTMLInputElement>("input");
    field?.focus();
  }, []);

  const handleNext = useCallback(() => {
    clearSelection();
    focusPhoneField();
  }, [clearSelection, focusPhoneField]);

  const reloadList = list.reload;

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key !== "/" || event.ctrlKey || event.metaKey || event.altKey) return;
      if (sheetOpen || isTypingTarget(event.target)) return;
      event.preventDefault();
      focusPhoneField();
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [focusPhoneField, sheetOpen]);

  const panel = target ? (
    <CheckInPanel
      key={`${target.kind}:${target.id}`}
      target={target}
      onInvalidTarget={clearSelection}
      onCheckedIn={reloadList}
      onNext={handleNext}
    />
  ) : (
    <CheckInPlaceholder />
  );

  return (
    <>
      <PageHeader
        title="Tiếp nhận bệnh nhân"
        description="Tìm bệnh nhân hoặc chọn lịch hẹn hôm nay, thu phí khám nếu chưa trả, rồi cấp số thứ tự."
      />
      <div className="grid gap-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] lg:items-start">
        <div className="min-w-0 space-y-6">
          <div ref={lookupRef}>{lookupSlot}</div>
          <TodayAppointmentList
            tab={tab}
            onTabChange={setTab}
            data={list.data}
            loading={list.loading}
            error={list.error}
            onRetry={list.reload}
            selectedAppointmentId={target?.kind === "appointment" ? target.id : null}
            onSelect={selectAppointment}
          />
        </div>
        {wide ? (
          <section
            aria-label="Tiếp nhận"
            className="min-w-0 rounded-lg border bg-card p-4 md:p-6"
          >
            {panel}
          </section>
        ) : null}
      </div>
      {wide ? null : (
        <Sheet
          open={sheetOpen}
          onOpenChange={(open) => {
            if (!open) clearSelection();
          }}
        >
          <SheetContent className="w-full overflow-y-auto data-[side=right]:w-full data-[side=right]:sm:max-w-full">
            <SheetTitle className="sr-only">Tiếp nhận bệnh nhân</SheetTitle>
            <div className="p-4">{panel}</div>
          </SheetContent>
        </Sheet>
      )}
    </>
  );
}
