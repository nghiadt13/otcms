"use client";

import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { UserRoundPlus } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  listReceptionAppointments,
  listReceptionArrivals,
  type ReceptionAppointment,
  type ReceptionArrival,
} from "../api/receptionLiveApi";
import {
  DAILY_RECEPTION_STATUSES,
  receptionStatusValue,
} from "../schema/intakeAppointmentStatus";
import { CheckInDialog } from "./CheckInDialog";
import { IntakeAppointmentDetail } from "./IntakeAppointmentDetail";
import { ReceptionArrivalQr } from "./ReceptionArrivalQr";
import {
  dateText,
  PatientName,
  RouteLink,
  Status,
  TextField,
  timeText,
} from "./ReceptionUi";

type CheckInTarget = { patientId?: string; appointmentId?: string };

/** Màn tiếp nhận hôm nay: lịch hẹn trong ngày, trạng thái đến và điểm mở hộp thoại tiếp nhận. */
export function IntakeScreen() {
  const params = useSearchParams();
  const [search, setSearch] = useState("");
  const [appointments, setAppointments] = useState<ReceptionAppointment[]>([]);
  const [arrivals, setArrivals] = useState<ReceptionArrival[]>([]);
  const [checkIn, setCheckIn] = useState<CheckInTarget | null>(
    params.get("patientId") || params.get("appointmentId")
      ? {
          patientId: params.get("patientId") ?? undefined,
          appointmentId: params.get("appointmentId") ?? undefined,
        }
      : null,
  );
  const today = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh",
  }).format(new Date());
  const [error, setError] = useState("");
  const [qrOpen, setQrOpen] = useState(false);
  const [detail, setDetail] = useState<ReceptionAppointment | null>(null);
  const reload = useCallback(async () => {
    const [appointmentsResult, arrivalsResult] = await Promise.allSettled([
      listReceptionAppointments({ from: today, to: today }),
      listReceptionArrivals({ date: today }),
    ]);
    if (appointmentsResult.status === "fulfilled")
      setAppointments(appointmentsResult.value.items);
    if (arrivalsResult.status === "fulfilled")
      setArrivals(arrivalsResult.value.items);
    const failed = [appointmentsResult, arrivalsResult].find(
      (result): result is PromiseRejectedResult => result.status === "rejected",
    );
    setError(failed ? messageFromError(failed.reason) : "");
  }, [today]);
  useEffect(() => {
    const initialLoad = window.setTimeout(() => void reload(), 0);
    const interval = window.setInterval(() => void reload(), 10_000);
    return () => {
      window.clearTimeout(initialLoad);
      window.clearInterval(interval);
    };
  }, [reload]);
  const visible = appointments.filter((item) => {
    if (!DAILY_RECEPTION_STATUSES.has(item.status)) return false;
    if (!search) return true;
    return [item.patientName, item.patientProfileId]
      .filter((value): value is string => Boolean(value))
      .some((value) =>
        value.toLocaleLowerCase().includes(search.toLocaleLowerCase()),
      );
  });
  const notCheckedInCount = appointments.filter(
    (appointment) => appointment.status === "CONFIRMED",
  ).length;

  return (
    <>
      <PageHeader
        title="Tiếp nhận bệnh nhân"
        description="Theo dõi lịch hẹn và trạng thái QR check-in hôm nay; hỗ trợ tiếp nhận tại quầy khi cần."
        actions={
          <>
            <Button variant="outline" onClick={() => setQrOpen(true)}>
              Mã QR check-in
            </Button>
            <Button onClick={() => setCheckIn({})}>
              <UserRoundPlus />
              Tiếp nhận bệnh nhân
            </Button>
          </>
        }
      />
      <div className="mb-4 grid grid-cols-2 gap-3 rounded-lg border bg-card p-4 md:grid-cols-3">
        <div>
          <p className="text-xs text-muted-foreground">Lịch hẹn hôm nay</p>
          <p className="mt-2 text-2xl font-semibold">
            {
              appointments.filter((item) =>
                DAILY_RECEPTION_STATUSES.has(item.status),
              ).length
            }
          </p>
        </div>
        <div>
          <p className="text-xs text-muted-foreground">Chưa check-in</p>
          <p className="mt-2 text-2xl font-semibold">{notCheckedInCount}</p>
        </div>
        <div>
          <p className="text-xs text-muted-foreground">Ngày phòng khám</p>
          <p className="mt-2 font-medium">{dateText(today)}</p>
        </div>
      </div>
      <div className="mb-4 w-full sm:w-72">
        <TextField
          label="Tìm theo tên hoặc mã hồ sơ"
          value={search}
          onChange={setSearch}
        />
      </div>
      {error && (
        <p role="alert" className="mb-3 text-sm text-destructive">
          {error}
        </p>
      )}
      <div className="overflow-x-auto rounded-lg border">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b text-left">
              <th className="p-3">Giờ hẹn</th>
              <th className="p-3">Bệnh nhân</th>
              <th className="p-3">Bác sĩ</th>
              <th className="p-3">Phí khám</th>
              <th className="p-3">Trạng thái</th>
              <th className="p-3">QR check-in</th>
              <th className="p-3">Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((appointment) => (
              <tr key={appointment.id} className="border-b last:border-0">
                <td className="p-3">{timeText(appointment.startTime)}</td>
                <td className="p-3">
                  <PatientName
                    name={appointment.patientName ?? "Chưa có tên bệnh nhân"}
                    code={appointment.patientProfileId}
                  />
                </td>
                <td className="p-3">
                  {appointment.doctorDisplayName ?? "Chưa phân bác sĩ"}
                </td>
                <td className="p-3">
                  {appointment.examFeePaid === true
                    ? "Đã thanh toán"
                    : appointment.examFeePaid === false
                      ? "Chưa thanh toán"
                      : "Chưa rõ"}
                </td>
                <td className="p-3">
                  <Status value={receptionStatusValue(appointment.status)} />
                </td>
                <td className="p-3">
                  {appointment.visitId
                    ? "Đã check-in"
                    : arrivals.find(
                          (arrival) => arrival.appointmentId === appointment.id,
                        )?.source === "PATIENT_QR"
                      ? "Đã quét QR"
                      : arrivals.some(
                            (arrival) =>
                              arrival.appointmentId === appointment.id,
                          )
                        ? "Đã xác nhận tại quầy"
                        : "Chưa quét"}
                </td>
                <td className="p-3">
                  <div className="flex flex-wrap gap-2">
                    <Button
                      variant="outline"
                      onClick={() => setDetail(appointment)}
                    >
                      Xem chi tiết
                    </Button>
                    {["CONFIRMED", "NO_SHOW"].includes(appointment.status) && (
                      <Button
                        onClick={() =>
                          setCheckIn({
                            patientId: appointment.patientProfileId,
                            appointmentId: appointment.id,
                          })
                        }
                      >
                        Hỗ trợ tiếp nhận
                      </Button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {visible.length === 0 && (
              <tr>
                <td
                  colSpan={7}
                  className="p-6 text-center text-muted-foreground"
                >
                  Không có lịch hẹn cần tiếp nhận.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <p className="mt-3 text-xs text-muted-foreground">
        Bệnh nhân mới chưa có hồ sơ?{" "}
        <RouteLink href="/reception/patients">Tra cứu hoặc tạo hồ sơ</RouteLink>
      </p>
      {checkIn && (
        <CheckInDialog
          {...checkIn}
          onClose={() => setCheckIn(null)}
          onUpdated={() => void reload()}
        />
      )}
      {detail && (
        <IntakeAppointmentDetail
          appointment={detail}
          onClose={() => setDetail(null)}
          onCheckIn={(appointment) => {
            setCheckIn({
              patientId: appointment.patientProfileId,
              appointmentId: appointment.id,
            });
            setDetail(null);
          }}
        />
      )}
      {qrOpen && <ReceptionArrivalQr onClose={() => setQrOpen(false)} />}
    </>
  );
}
