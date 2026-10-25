"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { CalendarPlus } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import {
  availableDoctors,
  addDays,
  normalizeText,
} from "../schema/receptionRules";
import type { Appointment } from "../types";
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

const slots = Array.from({ length: 30 }, (_, i) => {
  const n = i < 15 ? 420 + i * 20 : 780 + (i - 15) * 20;
  return `${String(Math.floor(n / 60)).padStart(2, "0")}:${String(n % 60).padStart(2, "0")}`;
}).filter((t) => t < "17:00");
function BookingForm({
  initialPatient,
  onClose,
}: {
  initialPatient?: string;
  onClose: () => void;
}) {
  const { data: s, pending, run } = useReception();
  const [patientId, setPatientId] = useState(initialPatient ?? "");
  const [date, setDate] = useState(s!.clock.slice(0, 10));
  const [serviceId, setService] = useState("kham");
  const [time, setTime] = useState("");
  const [doctorId, setDoctor] = useState("");
  const doctors = availableDoctors(s!, date, time, serviceId);
  const service = s!.services.find((v) => v.id === serviceId)!;
  return (
    <FormModal
      title="Đặt lịch khám"
      description="Chọn người khám và giờ còn trống. Có thể để phòng khám sắp xếp bác sĩ."
      onClose={onClose}
      wide
    >
      <form
        className="space-y-5"
        onSubmit={async (e) => {
          e.preventDefault();
          if (
            await run({
              type: "book",
              patientId,
              serviceId,
              date,
              time,
              doctorId,
            })
          )
            onClose();
        }}
      >
        <SelectField
          label="Bệnh nhân *"
          value={patientId}
          onChange={setPatientId}
          disabled={pending}
          options={[
            { value: "", label: "Chọn hồ sơ bệnh nhân" },
            ...s!.patients.map((p) => ({
              value: p.id,
              label: `${p.name} · ${p.id} · ${p.dob}`,
            })),
          ]}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField
            label="Dịch vụ khám"
            value={serviceId}
            onChange={(v) => {
              setService(v);
              setTime("");
              setDoctor("");
            }}
            disabled={pending}
            options={s!.services
              .filter((v) => v.category === "exam" && v.active)
              .map((v) => ({ value: v.id, label: v.name }))}
          />
          <TextField
            label="Ngày khám *"
            value={date}
            type="date"
            min={s!.clock.slice(0, 10)}
            max={addDays(s!.clock.slice(0, 10), 13)}
            onChange={(v) => {
              setDate(v);
              setTime("");
              setDoctor("");
            }}
            disabled={pending}
          />
        </div>
        <fieldset className="space-y-3">
          <legend className="mb-2 font-medium">Giờ khám *</legend>
          <div className="grid grid-cols-4 gap-2 sm:grid-cols-6">
            {slots.map((slot) => {
              const available =
                `${date}T${slot}` >= s!.clock.slice(0, 16) &&
                availableDoctors(s!, date, slot, serviceId).length > 0;
              return (
                <Button
                  key={slot}
                  type="button"
                  variant={time === slot ? "secondary" : "outline"}
                  disabled={!available || pending}
                  aria-pressed={time === slot}
                  aria-label={`${slot}${available ? " còn chỗ" : " không còn chỗ"}`}
                  onClick={() => {
                    setTime(slot);
                    setDoctor("");
                  }}
                >
                  {slot}
                </Button>
              );
            })}
          </div>
          {date &&
            !slots.some(
              (t) =>
                `${date}T${t}` >= s!.clock.slice(0, 16) &&
                availableDoctors(s!, date, t, serviceId).length > 0,
            ) && (
              <p className="text-muted-foreground">
                Không có giờ trống trong ngày này. Hãy chọn ngày khác.
              </p>
            )}
        </fieldset>
        <SelectField
          label="Bác sĩ (không bắt buộc)"
          value={doctorId}
          onChange={setDoctor}
          disabled={!time || pending}
          options={[
            { value: "", label: "Phòng khám sắp xếp bác sĩ" },
            ...doctors.map((d) => ({ value: d.id, label: d.name })),
          ]}
        />
        <p className="rounded-lg border bg-muted/30 p-3">
          Phí khám: <strong>{money(service.price)}</strong>. Chưa cần thanh toán
          để đặt lịch. Giờ đã đặt không thể sửa; chỉ bệnh nhân được hủy lịch
          trước khi tiếp nhận.
        </p>
        <FormActions
          onClose={onClose}
          label="Xác nhận đặt lịch"
          disabled={!patientId || !time || !doctors.length}
        />
      </form>
    </FormModal>
  );
}
function Availability({ onClose }: { onClose: () => void }) {
  const { data: s } = useReception();
  const [date, setDate] = useState(s!.clock.slice(0, 10));
  const [doctor, setDoctor] = useState("");
  const rows = slots
    .map((time) => ({
      time,
      doctors: availableDoctors(s!, date, time, "kham").filter(
        (d) => !doctor || d.id === doctor,
      ),
    }))
    .filter(
      (slot) =>
        slot.doctors.length && `${date}T${slot.time}` >= s!.clock.slice(0, 16),
    );
  return (
    <FormModal
      title="Giờ trống của bác sĩ"
      description="Khung 20 phút, trong ca làm việc, loại trừ các lịch đã được giữ chỗ."
      onClose={onClose}
      wide
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField
          label="Ngày khám"
          type="date"
          value={date}
          onChange={setDate}
          min={s!.clock.slice(0, 10)}
        />
        <SelectField
          label="Bác sĩ"
          value={doctor}
          onChange={setDoctor}
          options={[
            { value: "", label: "Tất cả bác sĩ" },
            ...s!.doctors.map((d) => ({ value: d.id, label: d.name })),
          ]}
        />
      </div>
      <DataTable
        label="Giờ khám còn trống"
        headers={["Giờ", "Bác sĩ có thể nhận lịch"]}
        rows={rows.map((row) => ({
          id: row.time,
          cells: [row.time, row.doctors.map((d) => d.name).join(", ")],
        }))}
        empty="Không còn giờ phù hợp. Chọn ngày hoặc bác sĩ khác."
      />
    </FormModal>
  );
}
function AppointmentDetails({
  appointmentId,
  onClose,
}: {
  appointmentId: string;
  onClose: () => void;
}) {
  const { data: s, run, pending } = useReception();
  const a = s!.appointments.find((x) => x.id === appointmentId)!;
  const p = s!.patients.find((x) => x.id === a.patientId)!;
  const d = s!.doctors.find((x) => x.id === a.doctorId)!;
  const [doctorId, setDoctorId] = useState("");
  const alternatives = availableDoctors(
    s!,
    a.date,
    a.time,
    a.serviceId,
    a.id,
  ).filter((v) => v.id !== d.id);
  return (
    <FormModal
      title={`Lịch hẹn ${a.id}`}
      description="Lễ tân chỉ thay bác sĩ khi vắng ca; giữ nguyên bệnh nhân, dịch vụ và giờ hẹn."
      onClose={onClose}
    >
      <dl>
        <InfoRow label="Bệnh nhân">
          {p.name} · {p.id}
        </InfoRow>
        <InfoRow label="Ngày / giờ">
          {dateText(a.date)} · {a.time}
        </InfoRow>
        <InfoRow label="Bác sĩ">
          {d.name}
          {d.absent && <span className="block text-destructive">Vắng ca</span>}
        </InfoRow>
        <InfoRow label="Trạng thái">
          <Status value={a.status} />
        </InfoRow>
      </dl>
      {a.notice && (
        <p role="status" className="text-muted-foreground">
          {a.notice}
        </p>
      )}
      {a.status === "booked" &&
        d.absent &&
        (alternatives.length ? (
          <form
            className="space-y-4"
            onSubmit={async (e) => {
              e.preventDefault();
              if (await run({ type: "reassign", id: a.id, doctorId }))
                onClose();
            }}
          >
            <SelectField
              label="Bác sĩ thay thế cùng giờ"
              value={doctorId}
              onChange={setDoctorId}
              disabled={pending}
              options={[
                { value: "", label: "Chọn bác sĩ" },
                ...alternatives.map((v) => ({ value: v.id, label: v.name })),
              ]}
            />
            <FormActions
              onClose={onClose}
              label="Xác nhận đổi bác sĩ"
              disabled={!doctorId}
            />
          </form>
        ) : (
          <div className="space-y-3">
            <p>
              Không có bác sĩ thay thế cùng giờ. Lịch vẫn giữ trạng thái đã đặt;
              bệnh nhân quyết định việc hủy.
            </p>
            <Button
              disabled={pending || !!a.notice}
              onClick={() => void run({ type: "notify-unavailable", id: a.id })}
            >
              Thông báo bác sĩ vắng
            </Button>
          </div>
        ))}
      <div className="flex flex-wrap gap-2">
        <RouteLink
          href={`/reception/payments?patientId=${p.id}&contextId=${a.id}`}
        >
          Xem khoản thu
        </RouteLink>
        {a.status === "booked" && a.date === s!.clock.slice(0, 10) && (
          <RouteLink href={`/reception?patientId=${p.id}`}>Tiếp nhận</RouteLink>
        )}
      </div>
    </FormModal>
  );
}
export function AppointmentScreen() {
  const { data: s } = useReception();
  const params = useSearchParams();
  const patientId = params.get("patientId") ?? "";
  const [date, setDate] = useState(patientId ? "" : s!.clock.slice(0, 10));
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [book, setBook] = useState(false);
  const [availability, setAvailability] = useState(false);
  const [detail, setDetail] = useState<Appointment | null>(null);
  const list = s!.appointments
    .filter(
      (a) =>
        (!patientId || a.patientId === patientId) &&
        (!date || a.date === date) &&
        (!status || a.status === status) &&
        normalizeText(
          `${a.id} ${s!.patients.find((p) => p.id === a.patientId)!.name}`,
        ).includes(normalizeText(search)),
    )
    .sort((a, b) => `${a.date}${a.time}`.localeCompare(`${b.date}${b.time}`));
  return (
    <>
      <PageHeader
        title="Lịch hẹn"
        description="Đặt lịch và xử lý bác sĩ vắng; giờ đã đặt được giữ nguyên."
        actions={
          <>
            <Button variant="outline" onClick={() => setAvailability(true)}>
              Giờ bác sĩ
            </Button>
            <Button onClick={() => setBook(true)}>
              <CalendarPlus />
              Đặt lịch
            </Button>
          </>
        }
      />
      <div className="mb-4 grid gap-3 sm:grid-cols-3">
        <TextField
          label="Ngày hẹn (để trống để xem tất cả)"
          type="date"
          value={date}
          onChange={setDate}
        />
        <TextField
          label="Tên bệnh nhân / mã lịch"
          value={search}
          onChange={setSearch}
        />
        <SelectField
          label="Trạng thái"
          value={status}
          onChange={setStatus}
          options={[
            { value: "", label: "Tất cả" },
            { value: "booked", label: "Đã đặt lịch" },
            { value: "checked-in", label: "Đã tiếp nhận" },
            { value: "cancelled", label: "Đã hủy" },
            { value: "no-show", label: "Không đến" },
          ]}
        />
      </div>
      {patientId && (
        <p className="mb-4 text-muted-foreground">
          Đang xem lịch của{" "}
          {s!.patients.find((p) => p.id === patientId)?.name ?? patientId}.{" "}
          <RouteLink href="/reception/appointments">
            Xem tất cả bệnh nhân
          </RouteLink>
        </p>
      )}
      <DataTable
        key={`${date}-${search}-${status}-${patientId}`}
        label="Lịch hẹn"
        headers={[
          "Lịch khám",
          "Bệnh nhân",
          "Bác sĩ",
          "Dịch vụ",
          "Trạng thái",
          "Thao tác",
        ]}
        rows={list.map((a) => {
          const p = s!.patients.find((p) => p.id === a.patientId)!,
            d = s!.doctors.find((d) => d.id === a.doctorId)!;
          return {
            id: a.id,
            cells: [
              <div key="date">
                <p className="font-medium">
                  {a.time} · {dateText(a.date)}
                </p>
                <p className="mt-1 text-xs text-muted-foreground">{a.id}</p>
              </div>,
              <PatientName key="patient" name={p.name} code={p.id} />,
              <div key="doctor">
                {d.name}
                {d.absent && a.status === "booked" && (
                  <p className="text-xs text-destructive">
                    Cần sắp xếp lại bác sĩ
                  </p>
                )}
              </div>,
              s!.services.find((v) => v.id === a.serviceId)!.name,
              <Status key="status" value={a.status} />,
              <Button
                key="detail"
                variant="outline"
                onClick={() => setDetail(a)}
              >
                Chi tiết
              </Button>,
            ],
          };
        })}
      />
      {book && (
        <BookingForm
          initialPatient={patientId}
          onClose={() => setBook(false)}
        />
      )}
      {availability && <Availability onClose={() => setAvailability(false)} />}
      {detail && (
        <AppointmentDetails
          appointmentId={detail.id}
          onClose={() => setDetail(null)}
        />
      )}
    </>
  );
}
