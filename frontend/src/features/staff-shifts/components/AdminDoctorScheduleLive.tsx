"use client";

import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Plus } from "lucide-react";
import { toast } from "sonner";
import { PageHeader } from "@/components/PageHeader";
import { AdminFormField as Field } from "@/components/admin/AdminFormField";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import { RemoteDataState } from "@/components/admin/RemoteDataState";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { TablePagination } from "@/components/admin/TablePagination";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useAdminRemoteData } from "@/hooks/useAdminRemoteData";
import { adminApiMessage } from "@/lib/api/adminApiMessage";
import type { DoctorInterval, MutationImpact } from "@/lib/domain/adminDoctor";
import {
  addDays,
  formatDateTime,
  formatTime,
  todayIsoDate,
} from "@/lib/format/formatDate";
import {
  assessDoctorInterval,
  deleteDoctorInterval,
  listDoctorSchedule,
  listScheduleDoctors,
  saveDoctorInterval,
  vietnamDateTime,
  type ScheduleKind,
} from "../api/doctorScheduleApi";

const loadDoctors = (signal: AbortSignal) => listScheduleDoctors(signal);
function impactMessage(impact: MutationImpact) {
  return `Chưa thể lưu: ${impact.affectedBookingCount} lịch hẹn bị ảnh hưởng${impact.activeCareBlocks ? ", có lượt khám đang được chăm sóc" : ""}. Hãy điều phối các lịch liên quan trước.`;
}

export function AdminDoctorScheduleLive() {
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 30_000);
    return () => clearInterval(timer);
  }, []);
  const params = useSearchParams();
  const doctors = useAdminRemoteData(loadDoctors);
  const [selected, setSelected] = useState(params.get("doctorId") ?? "");
  const doctorId =
    selected ||
    doctors.data?.find((d) => d.inActiveDirectory)?.doctorProfileId ||
    doctors.data?.[0]?.doctorProfileId ||
    "";
  const doctor = doctors.data?.find((d) => d.doctorProfileId === doctorId);
  const [from, setFrom] = useState(todayIsoDate);
  const [to, setTo] = useState(() => addDays(todayIsoDate(), 6));
  const [kind, setKind] = useState<ScheduleKind>("shifts");
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<DoctorInterval | "new" | null>(null);
  const [confirm, setConfirm] = useState<DoctorInterval | null>(null);
  const [error, setError] = useState("");
  const validRange = !!from && !!to && from <= to;
  const load = useCallback(
    (signal: AbortSignal) =>
      doctorId && validRange
        ? listDoctorSchedule(doctorId, kind, from, to, signal)
        : Promise.resolve([]),
    [doctorId, kind, from, to, validRange],
  );
  const remote = useAdminRemoteData(load);
  const rows = remote.data ?? [];
  const totalPages = Math.max(1, Math.ceil(rows.length / 10));
  const currentPage = Math.min(page, totalPages - 1);
  const refresh = () => {
    setEditing(null);
    remote.refresh();
  };
  const noun = kind === "shifts" ? "ca trực" : "khoảng nghỉ";
  return (
    <>
      <PageHeader
        title="Ca trực và ngày nghỉ bác sĩ"
        description="Xem lịch theo giờ Việt Nam; kiểm tra ảnh hưởng lịch hẹn trước khi thay đổi."
        actions={
          <Button
            disabled={!doctor?.inActiveDirectory || doctors.loading}
            onClick={() => setEditing("new")}
          >
            <Plus />
            {kind === "shifts" ? "Thêm ca trực" : "Thêm khoảng nghỉ"}
          </Button>
        }
      />
      <RemoteDataState
        loading={doctors.loading}
        error={doctors.error}
        retry={doctors.refresh}
      />
      {doctors.data && (
        <>
          <div className="mb-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <Field id="schedule-doctor" label="Bác sĩ">
              <NativeSelect
                id="schedule-doctor"
                className="w-full"
                value={doctorId}
                onChange={(e) => {
                  setSelected(e.target.value);
                  setPage(0);
                }}
              >
                {!doctors.data.length && (
                  <option value="">Chưa có bác sĩ</option>
                )}
                {doctors.data.map((d) => (
                  <option key={d.doctorProfileId} value={d.doctorProfileId}>
                    {d.fullName}
                    {!d.inActiveDirectory ? " (đã gỡ)" : ""}
                  </option>
                ))}
              </NativeSelect>
            </Field>
            <Field id="schedule-kind" label="Loại lịch">
              <NativeSelect
                id="schedule-kind"
                className="w-full"
                value={kind}
                onChange={(e) => {
                  setKind(e.target.value as ScheduleKind);
                  setPage(0);
                }}
              >
                <option value="shifts">Ca trực</option>
                <option value="time-offs">Khoảng nghỉ / vắng mặt</option>
              </NativeSelect>
            </Field>
            <Field id="schedule-from" label="Từ ngày">
              <Input
                id="schedule-from"
                type="date"
                value={from}
                onChange={(e) => {
                  setFrom(e.target.value);
                  setPage(0);
                }}
              />
            </Field>
            <Field id="schedule-to" label="Đến hết ngày">
              <Input
                id="schedule-to"
                type="date"
                min={from}
                value={to}
                onChange={(e) => {
                  setTo(e.target.value);
                  setPage(0);
                }}
              />
            </Field>
          </div>
          {!validRange ? (
            <p role="alert" className="text-sm text-destructive">
              Chọn đủ ngày bắt đầu và ngày kết thúc; ngày kết thúc không được
              trước ngày bắt đầu.
            </p>
          ) : (
            <>
              <RemoteDataState
                loading={remote.loading}
                error={remote.error}
                retry={remote.refresh}
              />
              {remote.data && (
                <>
                  <div className="rounded-lg border bg-card">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>Bắt đầu</TableHead>
                          <TableHead>Kết thúc</TableHead>
                          <TableHead>Trạng thái</TableHead>
                          <TableHead className="text-right">Thao tác</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {rows
                          .slice(currentPage * 10, currentPage * 10 + 10)
                          .map((r) => {
                            const future = new Date(r.startsAt).getTime() > now;
                            const ended = new Date(r.endsAt).getTime() <= now;
                            return (
                              <TableRow key={r.id}>
                                <TableCell>
                                  {formatDateTime(r.startsAt)}
                                </TableCell>
                                <TableCell>
                                  {formatDateTime(r.endsAt)}
                                </TableCell>
                                <TableCell>
                                  <StatusBadge
                                    tone={future ? "info" : "neutral"}
                                  >
                                    {future
                                      ? "Sắp tới"
                                      : ended
                                        ? "Đã kết thúc"
                                        : "Đang diễn ra"}
                                  </StatusBadge>
                                </TableCell>
                                <TableCell>
                                  <div className="flex justify-end gap-2">
                                    {future && doctor?.inActiveDirectory ? (
                                      <>
                                        <Button
                                          size="sm"
                                          variant="outline"
                                          onClick={() => setEditing(r)}
                                        >
                                          Sửa
                                        </Button>
                                        <Button
                                          size="sm"
                                          variant="ghost"
                                          onClick={() => {
                                            setError("");
                                            setConfirm(r);
                                          }}
                                        >
                                          Xóa
                                        </Button>
                                      </>
                                    ) : (
                                      <span className="text-xs text-muted-foreground">
                                        Chỉ xem
                                      </span>
                                    )}
                                  </div>
                                </TableCell>
                              </TableRow>
                            );
                          })}
                        {!rows.length && (
                          <TableRow>
                            <TableCell
                              colSpan={4}
                              className="h-24 text-center text-muted-foreground"
                            >
                              Chưa có {noun} trong khoảng ngày đã chọn.
                            </TableCell>
                          </TableRow>
                        )}
                      </TableBody>
                    </Table>
                  </div>
                  <TablePagination
                    page={currentPage}
                    totalPages={totalPages}
                    totalItems={rows.length}
                    onPageChange={setPage}
                  />
                </>
              )}
            </>
          )}
          <p className="mt-4 text-sm text-muted-foreground">
            Khoảng nghỉ làm bác sĩ không nhận lịch trong thời gian đó. Những
            lịch hẹn đã có cần được xử lý trước khi ghi nhận nghỉ.
          </p>
        </>
      )}
      {editing && doctor && (
        <IntervalEditor
          key={`${kind}-${typeof editing === "string" ? editing : editing.id}`}
          doctorId={doctorId}
          doctorName={doctor.fullName}
          kind={kind}
          interval={editing === "new" ? undefined : editing}
          close={() => setEditing(null)}
          saved={refresh}
        />
      )}
      <ConfirmActionDialog
        open={!!confirm}
        onOpenChange={(open) => {
          if (!open) setConfirm(null);
        }}
        title={`Xóa ${noun}?`}
        description={
          <>
            {doctor?.fullName}:{" "}
            {confirm &&
              `${formatDateTime(confirm.startsAt)} – ${formatTime(confirm.endsAt)}`}
            .{" "}
            {kind === "shifts"
              ? "Giờ nhận lịch trong ca này sẽ được tính lại. Backend sẽ chặn nếu có lịch hẹn hoặc lượt khám bị ảnh hưởng."
              : "Bác sĩ có thể nhận lịch trở lại trong thời gian này nếu có ca trực phù hợp."}
            {error && (
              <span role="alert" className="mt-2 block text-destructive">
                {error}
              </span>
            )}
          </>
        }
        confirmLabel={`Xóa ${noun}`}
        pendingLabel="Đang xóa..."
        destructive
        onConfirm={async () => {
          if (!confirm) return;
          try {
            await deleteDoctorInterval(doctorId, kind, confirm.id);
            toast.success(`Đã xóa ${noun}.`);
            remote.refresh();
          } catch (failure) {
            setError(adminApiMessage(failure));
            throw failure;
          }
        }}
      />
    </>
  );
}

function toLocal(value: string) {
  return `${todayIsoDate(new Date(value))}T${formatTime(value)}`;
}

function IntervalEditor({
  doctorId,
  doctorName,
  kind,
  interval,
  close,
  saved,
}: {
  doctorId: string;
  doctorName: string;
  kind: ScheduleKind;
  interval?: DoctorInterval;
  close: () => void;
  saved: () => void;
}) {
  const [start, setStart] = useState(
    interval
      ? toLocal(interval.startsAt)
      : `${addDays(todayIsoDate(), 1)}T08:00`,
  );
  const [end, setEnd] = useState(
    interval ? toLocal(interval.endsAt) : `${addDays(todayIsoDate(), 1)}T12:00`,
  );
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const noun = kind === "shifts" ? "ca trực" : "khoảng nghỉ";
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !pending) close();
      }}
    >
      <DialogContent
        className="max-h-[90dvh] overflow-y-auto sm:max-w-lg"
        showCloseButton={!pending}
      >
        <DialogHeader>
          <DialogTitle>
            {interval ? "Sửa" : "Thêm"} {noun}
          </DialogTitle>
          <DialogDescription>
            {doctorName}. Thời gian theo múi giờ Việt Nam (UTC+7).
          </DialogDescription>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={async (event) => {
            event.preventDefault();
            if (pending) return;
            if (!start || !end) {
              setError("Chọn đủ thời gian bắt đầu và kết thúc.");
              return;
            }
            const input = {
              startsAt: vietnamDateTime(start),
              endsAt: vietnamDateTime(end),
            };
            if (
              input.startsAt >= input.endsAt ||
              new Date(input.startsAt).getTime() <= Date.now()
            ) {
              setError(
                "Giờ bắt đầu phải ở tương lai; giờ kết thúc phải sau giờ bắt đầu.",
              );
              return;
            }
            setPending(true);
            setError("");
            try {
              const impact = await assessDoctorInterval(
                doctorId,
                kind,
                interval?.id,
                input,
              );
              if (impact.blocked) {
                setError(impactMessage(impact));
                return;
              }
              await saveDoctorInterval(doctorId, kind, interval?.id, input);
              toast.success(`Đã lưu ${noun}.`);
              saved();
            } catch (failure) {
              setError(adminApiMessage(failure));
            } finally {
              setPending(false);
            }
          }}
        >
          <fieldset disabled={pending} className="space-y-4">
            <Field id="interval-start" label="Bắt đầu *">
              <Input
                id="interval-start"
                type="datetime-local"
                required
                value={start}
                min={`${todayIsoDate()}T00:00`}
                onChange={(e) => setStart(e.target.value)}
              />
            </Field>
            <Field id="interval-end" label="Kết thúc *">
              <Input
                id="interval-end"
                type="datetime-local"
                required
                value={end}
                min={start}
                onChange={(e) => setEnd(e.target.value)}
              />
            </Field>
          </fieldset>
          <p className="text-xs text-muted-foreground">
            Hệ thống kiểm tra lịch hẹn và lượt khám trước khi lưu. Nếu bị chặn,
            hãy chuyển bác sĩ cho các lịch liên quan rồi thử lại.
          </p>
          {error && (
            <div role="alert" className="space-y-2 text-sm text-destructive">
              <p>{error}</p>
              <p className="text-muted-foreground">
                Phối hợp với lễ tân xử lý các lịch hẹn bị ảnh hưởng trước khi
                thử lưu lại.
              </p>
            </div>
          )}
          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              disabled={pending}
              onClick={close}
            >
              Hủy
            </Button>
            <Button type="submit" disabled={pending}>
              {pending ? "Đang kiểm tra và lưu..." : `Lưu ${noun}`}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
