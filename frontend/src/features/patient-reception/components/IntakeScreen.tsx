"use client";

import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { UserRoundPlus } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { PRIORITIES, type Priority } from "../types";
import {
  normalizeText,
  withinReturnWindow,
  agePriority,
  receptionPriority,
} from "../schema/receptionRules";
import { useReception } from "./ReceptionProvider";
import {
  DataTable,
  dateText,
  FormActions,
  FormModal,
  InfoRow,
  money,
  PatientName,
  RouteLink,
  SelectField,
  Status,
  TextField,
} from "./ReceptionUi";

export function CheckInDialog({
  patientId: initialPatient,
  appointmentId: initialAppointment,
  onClose,
}: {
  patientId?: string;
  appointmentId?: string;
  onClose: () => void;
}) {
  const { data: s, pending, run } = useReception();
  const fixedAppointment = s!.appointments.find(
    (a) => a.id === initialAppointment,
  );
  const [selectedPatient, setPatientId] = useState("");
  const patientId =
    initialPatient ?? fixedAppointment?.patientId ?? selectedPatient;
  const fixedPatient = !!initialPatient || !!initialAppointment;
  const [selectedPriority, setPriority] = useState<Priority>("normal");
  const [present, setPresent] = useState(false);
  const day = s!.clock.slice(0, 10);
  const appointments = s!.appointments.filter(
    (a) => a.patientId === patientId && a.date === day && a.status === "booked",
  );
  const returns = s!.visits.filter(
    (v) =>
      v.patientId === patientId &&
      v.status === "awaiting" &&
      withinReturnWindow(v.firstCheckIn, day),
  );
  const [selectedMode, setMode] = useState(
    initialAppointment ?? (returns[0]?.id || appointments[0]?.id || "walkin"),
  );
  const mode = initialAppointment ?? selectedMode;
  const returnVisit = returns.find((v) => v.id === mode);
  const appointment = appointments.find((a) => a.id === mode);
  const contextId = appointment?.id ?? `walkin-${patientId}-${day}`;
  const fee = s!.charges.find(
    (c) =>
      c.patientId === patientId &&
      c.contextId === contextId &&
      c.status !== "cancelled",
  );
  const patient = s!.patients.find((p) => p.id === patientId);
  const automaticPriority = agePriority(patient?.dob ?? "", day);
  const priority = receptionPriority(patient?.dob ?? "", day, selectedPriority);
  const invalidContext =
    (fixedPatient && !patient) || (!!initialAppointment && !appointment);
  const canCheckIn =
    !invalidContext &&
    !!patient &&
    present &&
    (!!returnVisit || fee?.status === "paid") &&
    (mode === "walkin" || !!appointment || !!returnVisit);
  return (
    <FormModal
      title="Tiếp nhận bệnh nhân"
      description="Xác nhận đúng người, đã có mặt và đủ điều kiện vào hàng đợi khám."
      onClose={onClose}
      wide
    >
      <form
        className="space-y-5"
        onSubmit={async (e) => {
          e.preventDefault();
          if (!canCheckIn || pending) return;
          const next = await run({
            type: "check-in",
            patientId,
            appointmentId: appointment?.id,
            returnVisitId: returnVisit?.id,
            present,
            priority,
          });
          if (next) onClose();
        }}
      >
        {fixedPatient ? (
          <dl className="rounded-lg border bg-muted/30 px-4">
            <InfoRow label="Bệnh nhân">
              {patient ? (
                <>
                  <span className="block">{patient.name}</span>
                  <span className="mt-1 block text-xs font-normal text-muted-foreground">
                    {patient.id} · {dateText(patient.dob)} · {patient.phone}
                  </span>
                </>
              ) : (
                "Không tìm thấy hồ sơ bệnh nhân"
              )}
            </InfoRow>
            {initialAppointment && (
              <InfoRow label="Lịch hẹn">
                {initialAppointment}
                {fixedAppointment &&
                  ` · ${fixedAppointment.time} · ${dateText(fixedAppointment.date)}`}
              </InfoRow>
            )}
          </dl>
        ) : (
          <SelectField
            label="Bệnh nhân *"
            value={patientId}
            onChange={(value) => {
              setPatientId(value);
              setPresent(false);
              setPriority("normal");
              setMode(
                s!.visits.find(
                  (v) =>
                    v.patientId === value &&
                    v.status === "awaiting" &&
                    withinReturnWindow(v.firstCheckIn, day),
                )?.id ??
                  s!.appointments.find(
                    (a) =>
                      a.patientId === value &&
                      a.date === day &&
                      a.status === "booked",
                  )?.id ??
                  "walkin",
              );
            }}
            disabled={pending}
            options={[
              { value: "", label: "Chọn bệnh nhân đã đối chiếu danh tính" },
              ...s!.patients.map((p) => ({
                value: p.id,
                label: `${p.id} · ${p.name} · ${p.phone}`,
              })),
            ]}
          />
        )}
        {invalidContext && (
          <p role="alert" className="text-destructive">
            {formatAppMessage("MSG-37")}
          </p>
        )}
        {patient && !invalidContext && (
          <>
            {!initialAppointment && (
              <SelectField
                label="Hình thức tiếp nhận"
                value={mode}
                onChange={setMode}
                disabled={pending}
                options={[
                  ...returns.map((v) => ({
                    value: v.id,
                    label: `Tiếp tục ${v.id} · không thu thêm phí khám`,
                  })),
                  ...appointments.map((a) => ({
                    value: a.id,
                    label: `${a.id} · lịch ${a.time} hôm nay`,
                  })),
                  {
                    value: "walkin",
                    label: "Khám trực tiếp · không tạo lịch hẹn",
                  },
                ]}
              />
            )}
            <dl>
              {!fixedPatient && (
                <InfoRow label="Định danh">
                  {patient.name} · {patient.dob.split("-").reverse().join("/")}{" "}
                  · {patient.phone}
                </InfoRow>
              )}
              <InfoRow label="Phí khám">
                {returnVisit ? (
                  "0 ₫ · tiếp tục lượt khám cũ trong 7 ngày"
                ) : fee ? (
                  <span className="flex items-center gap-3">
                    {money(fee.amount)}
                    <Status value={fee.status} />
                  </span>
                ) : (
                  "Chưa lập khoản phí khám"
                )}
              </InfoRow>
              {appointment && (
                <InfoRow label="Bác sĩ">
                  {s!.doctors.find((d) => d.id === appointment.doctorId)?.name}
                </InfoRow>
              )}
            </dl>
            {!returnVisit && !fee && mode === "walkin" && (
              <Button
                type="button"
                variant="outline"
                disabled={pending}
                onClick={() =>
                  void run({
                    type: "prepare-walk-in",
                    patientId,
                    serviceId: "kham",
                  })
                }
              >
                Lập phí khám trực tiếp ·{" "}
                {money(s!.services.find((v) => v.id === "kham")!.price)}
              </Button>
            )}
            {!returnVisit && fee?.status === "pending" && (
              <div className="flex flex-wrap items-center gap-3">
                <p className="text-muted-foreground">
                  Bệnh nhân cần thanh toán trước khi check-in.
                </p>
                <RouteLink
                  href={`/reception/payments?patientId=${patientId}&contextId=${contextId}`}
                >
                  Mở thu phí
                </RouteLink>
              </div>
            )}
            {returnVisit && (
              <p className="text-muted-foreground">
                Giữ nguyên lượt {returnVisit.id} và ngày tiếp nhận đầu tiên. Chỉ
                thu các dịch vụ chưa thanh toán, nếu có.
              </p>
            )}
            <SelectField
              label="Nhóm ưu tiên"
              value={priority}
              onChange={(v) => setPriority(v as Priority)}
              disabled={pending}
              options={Object.entries(PRIORITIES)
                .filter(
                  ([value]) => value !== "pregnant" || patient?.sex === "Nữ",
                )
                .filter(
                  ([value]) =>
                    !["normal", "child", "elderly"].includes(value) ||
                    value === automaticPriority,
                )
                .map(([value, label]) => ({ value, label }))}
            />
            <p className="text-xs text-muted-foreground">
              Nhóm theo tuổi được xác định tự động từ ngày sinh tại ngày tiếp
              nhận: {PRIORITIES[automaticPriority]}. Hãy đối chiếu ngày sinh;
              nếu sai, sửa hồ sơ trước khi tiếp nhận. Các diện ưu tiên khác cần
              xác minh thực tế; cấp cứu phải có đánh giá của nhân viên y tế.
            </p>
            <label className="flex items-start gap-3">
              <Checkbox
                checked={present}
                onCheckedChange={setPresent}
                disabled={pending}
              />
              <span>
                Đã đối chiếu đúng bệnh nhân và xác nhận bệnh nhân có mặt tại
                phòng khám.
              </span>
            </label>
          </>
        )}
        <FormActions
          onClose={onClose}
          label="Xác nhận tiếp nhận"
          disabled={!canCheckIn}
        />
      </form>
    </FormModal>
  );
}
export function IntakeScreen() {
  const { data: s } = useReception();
  const params = useSearchParams();
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("appointments");
  const [checkIn, setCheckIn] = useState<{
    patientId?: string;
    appointmentId?: string;
  } | null>(
    params.get("patientId") || params.get("appointmentId")
      ? {
          patientId: params.get("patientId") ?? undefined,
          appointmentId: params.get("appointmentId") ?? undefined,
        }
      : null,
  );
  const day = s!.clock.slice(0, 10);
  const appointments = s!.appointments.filter((a) => a.date === day);
  const queues = s!.queues.filter(
    (q) =>
      q.date === day && ["waiting", "serving", "absent"].includes(q.status),
  );
  const matches = (patientId: string) => {
    const p = s!.patients.find((p) => p.id === patientId)!;
    return normalizeText(`${p.name} ${p.id} ${p.phone}`).includes(
      normalizeText(search),
    );
  };
  const rows =
    filter === "appointments"
      ? appointments
          .filter((a) => matches(a.patientId))
          .map((a) => {
            const p = s!.patients.find((p) => p.id === a.patientId)!;
            const d = s!.doctors.find((d) => d.id === a.doctorId)!;
            const charge = s!.charges.find((c) => c.contextId === a.id);
            return {
              id: a.id,
              cells: [
                <span key="time" className="font-medium tabular-nums">
                  {a.time}
                </span>,
                <PatientName
                  key="patient"
                  name={p.name}
                  code={p.id}
                  secondary={p.phone}
                />,
                <div key="doctor">
                  {d.name}
                  {d.absent && (
                    <p className="mt-1 text-xs text-destructive">
                      {a.unavailabilityDiscussedAt
                        ? "Bác sĩ vắng ca · đã trao đổi với bệnh nhân"
                        : "Bác sĩ vắng ca · cần bố trí thay thế"}
                    </p>
                  )}
                </div>,
                <Status key="fee" value={charge?.status ?? "pending"} />,
                <Status key="status" value={a.status} />,
                a.status === "booked" ? (
                  <Button
                    key="checkin"
                    variant="outline"
                    onClick={() =>
                      setCheckIn({ patientId: p.id, appointmentId: a.id })
                    }
                  >
                    Tiếp nhận
                  </Button>
                ) : (
                  "—"
                ),
              ],
            };
          })
      : queues
          .filter((q) =>
            matches(s!.visits.find((v) => v.id === q.visitId)!.patientId),
          )
          .map((q) => {
            const v = s!.visits.find((v) => v.id === q.visitId)!,
              p = s!.patients.find((p) => p.id === v.patientId)!;
            return {
              id: q.id,
              cells: [
                <span key="number" className="font-semibold tabular-nums">
                  {q.number}
                </span>,
                <PatientName key="patient" name={p.name} code={p.id} />,
                s!.rooms.find((r) => r.id === q.roomId)!.name,
                PRIORITIES[q.priority],
                <Status
                  key="status"
                  value={
                    q.calledAt && q.status === "waiting" ? "called" : q.status
                  }
                />,
                <RouteLink
                  key="open"
                  href={`/reception/queues?roomId=${q.roomId}`}
                >
                  Mở hàng đợi
                </RouteLink>,
              ],
            };
          });
  return (
    <>
      <PageHeader
        title="Tiếp nhận bệnh nhân"
        description="Theo dõi lịch đến khám và đưa bệnh nhân vào đúng hàng đợi."
        actions={
          <Button onClick={() => setCheckIn({})}>
            <UserRoundPlus />
            Tiếp nhận bệnh nhân
          </Button>
        }
      />
      <div className="mb-6 grid grid-cols-2 divide-x divide-y rounded-lg border bg-card md:grid-cols-4 md:divide-y-0">
        {[
          { title: "Lịch hẹn hôm nay", count: appointments.length },
          {
            title: "Chưa tiếp nhận",
            count: appointments.filter((a) => a.status === "booked").length,
          },
          {
            title: "Đang chờ trong phòng",
            count: queues.filter((q) => q.status === "waiting").length,
          },
          {
            title: "Cần đổi bác sĩ",
            count: appointments.filter(
              (a) =>
                a.status === "booked" &&
                s!.doctors.find((d) => d.id === a.doctorId)?.absent,
            ).length,
          },
        ].map((item) => (
          <div key={item.title} className="p-4">
            <p className="text-xs text-muted-foreground">{item.title}</p>
            <p className="mt-2 text-2xl font-semibold tabular-nums">
              {item.count}
            </p>
          </div>
        ))}
      </div>
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <div
          className="flex gap-2"
          role="group"
          aria-label="Danh sách tiếp nhận"
        >
          <Button
            variant={filter === "appointments" ? "secondary" : "ghost"}
            aria-pressed={filter === "appointments"}
            onClick={() => setFilter("appointments")}
          >
            Lịch hẹn hôm nay
          </Button>
          <Button
            variant={filter === "queue" ? "secondary" : "ghost"}
            aria-pressed={filter === "queue"}
            onClick={() => setFilter("queue")}
          >
            Đã tiếp nhận
          </Button>
        </div>
        <div className="w-full sm:w-72">
          <TextField
            label="Tìm trong danh sách"
            value={search}
            onChange={setSearch}
          />
        </div>
      </div>
      <DataTable
        key={`${filter}-${search}`}
        label="Tiếp nhận bệnh nhân"
        headers={
          filter === "appointments"
            ? [
                "Giờ hẹn",
                "Bệnh nhân",
                "Bác sĩ",
                "Phí khám",
                "Lịch hẹn",
                "Thao tác",
              ]
            : [
                "Số thứ tự",
                "Bệnh nhân",
                "Phòng",
                "Ưu tiên",
                "Trạng thái",
                "Thao tác",
              ]
        }
        rows={rows}
      />
      <p className="mt-5 text-xs text-muted-foreground">
        Bệnh nhân mới chưa có hồ sơ?{" "}
        <RouteLink href="/reception/patients">Tra cứu hoặc tạo hồ sơ</RouteLink>
      </p>
      {checkIn && (
        <CheckInDialog {...checkIn} onClose={() => setCheckIn(null)} />
      )}
    </>
  );
}
