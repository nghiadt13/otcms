"use client";

import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { isApiError } from "@/lib/api/apiError";
import {
  listReceptionAppointments,
  type ReceptionAppointment,
} from "../api/receptionLiveApi";
import {
  clinicLocal,
  DataTable,
  dateText,
  FormModal,
  InfoRow,
  PatientName,
  RouteLink,
  SelectField,
  Status,
  TextField,
  timeText,
} from "./ReceptionUi";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const REFRESH_MS = 10_000;
const STATUS_OPTIONS = [
  { value: "", label: "Tất cả" },
  { value: "CONFIRMED", label: "Đã đặt lịch" },
  { value: "CHECKED_IN", label: "Đã tiếp nhận" },
  { value: "IN_PROGRESS", label: "Đang khám" },
  { value: "COMPLETED", label: "Đã hoàn thành" },
  { value: "CANCELLED", label: "Đã hủy" },
  { value: "NO_SHOW", label: "Không đến" },
];

/** Maps backend appointment status to the badge keys defined in ReceptionUi. */
function badgeKey(status: string) {
  return (
    {
      CONFIRMED: "booked",
      CHECKED_IN: "checked-in",
      IN_PROGRESS: "examining",
      COMPLETED: "completed",
      CANCELLED: "cancelled",
      NO_SHOW: "no-show",
    }[status] ?? status
  );
}

const canCheckIn = (a: ReceptionAppointment) =>
  ["CONFIRMED", "NO_SHOW"].includes(a.status);
const checkInHref = (a: ReceptionAppointment) =>
  `/reception?patientId=${a.patientProfileId}&appointmentId=${a.id}`;

function AppointmentDetails({
  appointment: a,
  onClose,
}: {
  appointment: ReceptionAppointment;
  onClose: () => void;
}) {
  return (
    <FormModal
      title="Chi tiết lịch hẹn"
      description="Thông tin lịch hẹn lấy trực tiếp từ hệ thống."
      onClose={onClose}
    >
      <dl>
        <InfoRow label="Bệnh nhân">{a.patientName ?? "Chưa có tên"}</InfoRow>
        <InfoRow label="Ngày / giờ">{timeText(a.startTime)}</InfoRow>
        <InfoRow label="Dịch vụ">{a.serviceName ?? "Chưa có thông tin"}</InfoRow>
        <InfoRow label="Bác sĩ">
          {a.doctorDisplayName ?? "Chưa phân bác sĩ"}
        </InfoRow>
        {a.purpose && <InfoRow label="Lý do khám">{a.purpose}</InfoRow>}
        <InfoRow label="Trạng thái">
          <Status value={badgeKey(a.status)} />
        </InfoRow>
      </dl>
      {canCheckIn(a) && (
        <div className="flex justify-end">
          <RouteLink href={checkInHref(a)}>Tiếp nhận</RouteLink>
        </div>
      )}
    </FormModal>
  );
}

export function AppointmentScreen() {
  const params = useSearchParams();
  const rawPatient = params.get("patientId") ?? "";
  // Only a real profile id can filter; legacy mock ids would be rejected by the API.
  const patientProfileId = UUID.test(rawPatient) ? rawPatient : undefined;
  const today = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh",
  }).format(new Date());
  const [date, setDate] = useState(patientProfileId ? "" : today);
  const [status, setStatus] = useState("");
  const [search, setSearch] = useState("");
  const [items, setItems] = useState<ReceptionAppointment[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState("");
  const [detail, setDetail] = useState<ReceptionAppointment | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    const load = () =>
      listReceptionAppointments(
        {
          from: date || undefined,
          to: date || undefined,
          status: status || undefined,
          patientProfileId,
        },
        controller.signal,
      )
        .then((page) => {
          setItems(page.items);
          setError("");
          setLoaded(true);
        })
        .catch((cause) => {
          if (controller.signal.aborted) return;
          setError(
            isApiError(cause) ? cause.message : "Không thể tải lịch hẹn.",
          );
          setLoaded(true);
        });
    void load();
    const timer = window.setInterval(() => void load(), REFRESH_MS);
    return () => {
      controller.abort();
      window.clearInterval(timer);
    };
  }, [date, status, patientProfileId]);

  const keyword = search.trim().toLocaleLowerCase();
  const rows = items
    .filter(
      (a) =>
        !keyword ||
        (a.patientName ?? "").toLocaleLowerCase().includes(keyword) ||
        a.id.toLowerCase().startsWith(keyword),
    )
    .sort((a, b) => a.startTime.localeCompare(b.startTime));

  return (
    <>
      <PageHeader
        title="Lịch hẹn"
        description="Xem lịch hẹn của phòng khám theo ngày và trạng thái; tiếp nhận bệnh nhân đã đến."
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
          options={STATUS_OPTIONS}
        />
      </div>
      {patientProfileId && (
        <p className="mb-4 text-muted-foreground">
          Đang xem lịch của một bệnh nhân.{" "}
          <RouteLink href="/reception/appointments">
            Xem tất cả bệnh nhân
          </RouteLink>
        </p>
      )}
      {error && (
        <p role="alert" className="mb-3 text-sm text-destructive">
          {error}
        </p>
      )}
      <DataTable
        key={`${date}-${search}-${status}`}
        label="Lịch hẹn"
        headers={[
          "Giờ hẹn",
          "Bệnh nhân",
          "Bác sĩ",
          "Dịch vụ",
          "Trạng thái",
          "Thao tác",
        ]}
        empty={
          loaded
            ? "Không có lịch hẹn phù hợp. Hãy đổi ngày hoặc bộ lọc."
            : "Đang tải lịch hẹn..."
        }
        rows={rows.map((a) => ({
          id: a.id,
          cells: [
            <div key="time">
              <p className="font-medium">
                {clinicLocal(a.startTime).slice(11, 16)}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                {dateText(a.startTime)}
              </p>
            </div>,
            <PatientName
              key="patient"
              name={a.patientName ?? "Chưa có tên"}
              code={`Mã lịch ${a.id.slice(0, 8)}`}
            />,
            a.doctorDisplayName ?? "Chưa phân bác sĩ",
            a.serviceName ?? "—",
            <Status key="status" value={badgeKey(a.status)} />,
            <div key="actions" className="flex flex-wrap gap-2">
              <Button variant="outline" onClick={() => setDetail(a)}>
                Chi tiết
              </Button>
              {canCheckIn(a) && clinicLocal(a.startTime).slice(0, 10) === today && (
                <RouteLink href={checkInHref(a)}>Tiếp nhận</RouteLink>
              )}
            </div>,
          ],
        }))}
      />
      {detail && (
        <AppointmentDetails
          appointment={detail}
          onClose={() => setDetail(null)}
        />
      )}
    </>
  );
}
