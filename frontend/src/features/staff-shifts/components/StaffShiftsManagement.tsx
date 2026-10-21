"use client";

import { Plus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { STAFF_ROLES } from "@/lib/domain/clinicRoles";
import { ADMIN_ROUTES } from "@/lib/routes/adminRoutes";
import { toQueryString } from "@/lib/api/pagination";
import { useShiftFormOptions } from "../hooks/useShiftFormOptions";
import { useShiftWeek } from "../hooks/useShiftWeek";
import type { Shift, StaffOption } from "../types/shift";
import { isPastDate } from "../utils/shiftWeekRange";
import { ShiftDeleteConfirm } from "./ShiftDeleteConfirm";
import { ShiftDetailSheet } from "./ShiftDetailSheet";
import { ShiftFilterBar } from "./ShiftFilterBar";
import { ShiftFormDialog, type ShiftFormTarget } from "./ShiftFormDialog";
import { ShiftListView } from "./ShiftListView";
import { ShiftToolbar } from "./ShiftToolbar";
import { ShiftWeekGrid } from "./ShiftWeekGrid";

/** Màn Ca làm việc: lọc, điều hướng kỳ, lưới tuần hoặc danh sách, thêm/sửa/xóa ca. */
export function StaffShiftsManagement() {
  const router = useRouter();
  const week = useShiftWeek();
  const options = useShiftFormOptions();
  const [form, setForm] = useState<{
    open: boolean;
    target: ShiftFormTarget | null;
  }>({
    open: false,
    target: null,
  });
  const [deleting, setDeleting] = useState<Shift | null>(null);
  const [viewing, setViewing] = useState<Shift | null>(null);

  const { filters, today, shifts } = week;
  const filtered = Boolean(filters.staffId || filters.role || filters.roomId);

  // Hàng lưới: nhân viên đủ điều kiện (cả người chưa có ca) + người đã có ca nhưng không còn đủ điều kiện.
  const rows = useMemo<StaffOption[]>(() => {
    const byId = new Map(options.staff.map((s) => [s.staffId, s]));
    for (const shift of shifts)
      if (!byId.has(shift.staffId))
        byId.set(shift.staffId, {
          staffId: shift.staffId,
          fullName: shift.staffName,
          role: shift.role,
        });
    const withShifts = new Set(shifts.map((s) => s.staffId));
    return [...byId.values()]
      .filter(
        (s) =>
          (!filters.staffId || s.staffId === filters.staffId) &&
          (!filters.role || s.role === filters.role) &&
          (!filters.roomId || withShifts.has(s.staffId)),
      )
      .sort(
        (a, b) =>
          STAFF_ROLES.indexOf(a.role) - STAFF_ROLES.indexOf(b.role) ||
          a.fullName.localeCompare(b.fullName, "vi"),
      );
  }, [options.staff, shifts, filters]);

  const openAdd = (staffId?: string, date?: string) =>
    setForm({ open: true, target: { staffId, date } });
  const openShift = (shift: Shift) => {
    if (shift.allowedActions.includes("edit"))
      setForm({ open: true, target: { shift } });
    else setViewing(shift);
  };
  const goToAppointments = (staffId: string, date: string) => {
    setForm({ open: false, target: null });
    setDeleting(null);
    router.push(
      `${ADMIN_ROUTES.appointments}${toQueryString({ doctorId: staffId, date })}`,
    );
  };

  const canUseGrid = week.wide && week.period === "week";
  const error = week.error;
  const retry = week.forbidden ? undefined : week.reload;
  const pastRange = isPastDate(week.range.to, today);

  return (
    <>
      <PageHeader
        title="Ca làm việc"
        description="Xếp ca cho lễ tân, bác sĩ và kỹ thuật viên. Giờ nhận lịch khám của bác sĩ lấy từ ca của bác sĩ."
        actions={
          <Button
            onClick={() =>
              openAdd(
                undefined,
                isPastDate(week.anchor, today) ? today : week.anchor,
              )
            }
          >
            <Plus data-icon="inline-start" />
            Thêm ca
          </Button>
        }
      />
      <ShiftFilterBar
        filters={filters}
        staff={options.staff}
        rooms={options.rooms}
        optionsLoading={options.loading}
        optionsError={options.error}
        onRetryOptions={options.reload}
        onChange={week.setFilters}
      />
      <ShiftToolbar
        today={today}
        anchor={week.anchor}
        period={week.period}
        view={week.view}
        range={week.range}
        canUseGrid={canUseGrid}
        onPrev={week.goPrev}
        onNext={week.goNext}
        onToday={week.goToday}
        onPeriodChange={week.setPeriod}
        onViewChange={week.setView}
      />
      {pastRange ? (
        <p className="mb-3 text-sm text-muted-foreground">
          Kỳ đã qua, chỉ xem.
        </p>
      ) : null}

      {week.view === "grid" ? (
        <ShiftWeekGrid
          days={week.range.days}
          rows={rows}
          shifts={shifts}
          today={today}
          loading={week.loading || options.loading}
          error={error ?? options.error}
          onRetry={error ? retry : options.reload}
          filtered={filtered}
          onOpenShift={openShift}
          onAddShift={openAdd}
        />
      ) : (
        <ShiftListView
          shifts={shifts}
          period={week.period}
          loading={week.loading}
          error={error}
          onRetry={retry}
          filtered={filtered}
          onOpenShift={openShift}
          onDeleteShift={setDeleting}
        />
      )}

      <ShiftFormDialog
        open={form.open}
        onOpenChange={(open) => setForm((current) => ({ ...current, open }))}
        target={form.target}
        staff={options.staff}
        rooms={options.rooms}
        optionsLoading={options.loading}
        optionsError={options.error}
        onRetryOptions={options.reload}
        today={today}
        onSaved={week.reload}
        onRequestDelete={(shift) => {
          setForm({ open: false, target: null });
          setDeleting(shift);
        }}
        onTransfer={goToAppointments}
      />
      <ShiftDeleteConfirm
        shift={deleting}
        onOpenChange={(open) => {
          if (!open) setDeleting(null);
        }}
        onDeleted={() => {
          setDeleting(null);
          week.reload();
        }}
        onTransfer={goToAppointments}
      />
      <ShiftDetailSheet
        shift={viewing}
        onOpenChange={(open) => {
          if (!open) setViewing(null);
        }}
      />
    </>
  );
}
