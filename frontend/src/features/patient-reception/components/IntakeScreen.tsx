"use client";

import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { UserRoundPlus } from "lucide-react";
import { QRCodeSVG } from "qrcode.react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { isApiError } from "@/lib/api/apiError";
import {
  checkInAppointment,
  createReceptionWalkIn,
  getCurrentClinicArrivalProof,
  getReceptionVerificationProfile,
  listReceptionAppointments,
  listReceptionArrivals,
  recordManualReceptionArrival,
  searchReceptionPatients,
  type ReceptionAppointment,
  type ReceptionArrival,
  type ReceptionPatient,
} from "../api/receptionLiveApi";
import {
  dateText,
  FormModal,
  PatientName,
  RouteLink,
  Status,
  timeText,
  TextField,
} from "./ReceptionUi";

const DAILY_RECEPTION_STATUSES = new Set([
  "CONFIRMED",
  "NO_SHOW",
  "CHECKED_IN",
  "IN_PROGRESS",
  "COMPLETED",
]);

function receptionStatusValue(status: string) {
  switch (status) {
    case "NO_SHOW":
      return "no-show";
    case "CHECKED_IN":
      return "checked-in";
    case "IN_PROGRESS":
      return "examining";
    case "COMPLETED":
      return "completed";
    default:
      return "booked";
  }
}

export function CheckInDialog({
  patientId: initialPatient,
  appointmentId: initialAppointment,
  onClose,
  onUpdated,
}: {
  patientId?: string;
  appointmentId?: string;
  onClose: () => void;
  onUpdated: () => void;
}) {
  const [pending, setPending] = useState(false);
  const [query, setQuery] = useState("");
  const [patients, setPatients] = useState<ReceptionPatient[]>([]);
  const [patientsQuery, setPatientsQuery] = useState("");
  const [patientId, setPatientId] = useState(initialPatient ?? "");
  const [appointments, setAppointments] = useState<ReceptionAppointment[]>([]);
  const [appointmentsPatientId, setAppointmentsPatientId] = useState("");
  const [arrivals, setArrivals] = useState<ReceptionArrival[]>([]);
  const [arrivalReason, setArrivalReason] = useState("");
  const [verified, setVerified] = useState<{
    patientProfileId: string;
    fullName: string;
    dateOfBirth: string;
  } | null>(null);
  const [error, setError] = useState("");
  const [result, setResult] = useState("");
  const idempotencyKeys = useRef(new Map<string, string>());
  const selected = patients.find(
    (patient) => patient.patientProfileId === patientId,
  );
  const today = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh",
  }).format(new Date());

  useEffect(() => {
    if (initialPatient || query.trim().length < 2) {
      return;
    }
    const controller = new AbortController();
    const timeout = window.setTimeout(() => {
      void searchReceptionPatients(query, controller.signal)
        .then((items) => {
          setPatients(items);
          setPatientsQuery(query);
        })
        .catch((cause) => {
          if (!controller.signal.aborted)
            setError(
              isApiError(cause) ? cause.message : "Không thể tìm hồ sơ.",
            );
        });
    }, 250);
    return () => {
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [initialPatient, query]);

  useEffect(() => {
    if (!patientId) {
      return;
    }
    const controller = new AbortController();
    void listReceptionAppointments(
      { from: today, to: today, patientProfileId: patientId },
      controller.signal,
    )
      .then((page) => {
        setAppointments(
          page.items.filter((item) =>
            ["CONFIRMED", "NO_SHOW"].includes(item.status),
          ),
        );
        setAppointmentsPatientId(patientId);
      })
      .catch((cause) => {
        if (!controller.signal.aborted)
          setError(
            isApiError(cause) ? cause.message : "Không thể tải lịch hẹn.",
          );
      });
    return () => controller.abort();
  }, [patientId, today]);

  useEffect(() => {
    const controller = new AbortController();
    void listReceptionArrivals({ date: today }, controller.signal)
      .then((worklist) => setArrivals(worklist.items))
      .catch((cause) => {
        if (!controller.signal.aborted)
          setError(
            isApiError(cause)
              ? cause.message
              : "Không thể tải danh sách bệnh nhân đã đến.",
          );
      });
    return () => controller.abort();
  }, [today]);

  async function verifyIdentity() {
    if (!patientId || pending) return;
    setPending(true);
    setError("");
    try {
      setVerified(await getReceptionVerificationProfile(patientId));
    } catch (cause) {
      setError(isApiError(cause) ? cause.message : "Không thể xác minh hồ sơ.");
    } finally {
      setPending(false);
    }
  }

  async function submit(appointmentId?: string, priorAppointmentId?: string) {
    if (!verified || pending) return;
    setPending(true);
    setError("");
    setResult("");
    try {
      const operation = appointmentId ?? `walk-in:${verified.patientProfileId}`;
      const idempotencyKey =
        idempotencyKeys.current.get(operation) ?? crypto.randomUUID();
      idempotencyKeys.current.set(operation, idempotencyKey);
      if (appointmentId) {
        if (
          !arrivals.some((arrival) => arrival.appointmentId === appointmentId)
        ) {
          setError("Cần ghi nhận bệnh nhân đã đến trước khi check-in.");
          return;
        }
        const response = await checkInAppointment({
          appointmentId,
          verifiedPatientProfileId: verified.patientProfileId,
          idempotencyKey,
        });
        setResult(`Đã tiếp nhận lịch hẹn. Mã lượt khám: ${response.visitId}`);
        onUpdated();
        setAppointments((items) =>
          items.filter((item) => item.id !== appointmentId),
        );
        idempotencyKeys.current.delete(operation);
      } else {
        const response = await createReceptionWalkIn({
          patientProfileId: verified.patientProfileId,
          priorAppointmentId,
          idempotencyKey,
        });
        setResult(
          `Đã tạo lượt khám trực tiếp. Mã lượt khám: ${response.visitId}`,
        );
        onUpdated();
        idempotencyKeys.current.delete(operation);
      }
    } catch (cause) {
      setError(
        isApiError(cause) ? cause.message : "Không thể hoàn tất tiếp nhận.",
      );
    } finally {
      setPending(false);
    }
  }

  return (
    <FormModal
      title="Tiếp nhận bệnh nhân"
      description="Tìm hồ sơ, đối chiếu thông tin bệnh nhân, rồi chọn lịch hẹn hoặc tiếp nhận trực tiếp."
      onClose={onClose}
      wide
    >
      <form className="space-y-5" onSubmit={(event) => event.preventDefault()}>
        {initialAppointment && (
          <p className="text-sm">Lịch hẹn: {initialAppointment}</p>
        )}
        {!initialPatient && (
          <TextField
            label="Tên hoặc số điện thoại"
            value={query}
            onChange={(value) => {
              setQuery(value);
              setPatientId("");
              setVerified(null);
            }}
          />
        )}
        {!initialPatient && patientsQuery === query && patients.length > 0 && (
          <div className="max-h-40 space-y-1 overflow-auto rounded-md border p-2">
            {patients.map((patient) => (
              <button
                key={patient.patientProfileId}
                type="button"
                className="block w-full rounded p-2 text-left hover:bg-muted"
                onClick={() => {
                  setPatientId(patient.patientProfileId);
                  setVerified(null);
                  setError("");
                }}
              >
                {patient.fullName} · {patient.phoneCanonical} ·{" "}
                {dateText(patient.dateOfBirth)}
              </button>
            ))}
          </div>
        )}
        {selected && (
          <p className="text-sm">
            Đã chọn: {selected.fullName} · {dateText(selected.dateOfBirth)}
          </p>
        )}
        {initialPatient && (
          <p className="text-xs text-muted-foreground">Mã hồ sơ: {patientId}</p>
        )}
        {!verified ? (
          <Button
            type="button"
            disabled={!patientId || pending}
            onClick={() => void verifyIdentity()}
          >
            {pending ? "Đang xác minh…" : "Xác minh hồ sơ bệnh nhân"}
          </Button>
        ) : (
          <>
            <p className="rounded-md bg-muted p-3 text-sm">
              Đã xác minh hồ sơ: {verified.fullName} ·{" "}
              {dateText(verified.dateOfBirth)}.
            </p>
            {appointmentsPatientId === verified.patientProfileId &&
              appointments
                .filter(
                  (appointment) =>
                    !initialAppointment ||
                    initialAppointment === appointment.id,
                )
                .map((appointment) => (
                  <div
                    key={appointment.id}
                    className="flex flex-wrap items-center justify-between gap-3 rounded-md border p-3"
                  >
                    <div>
                      <p className="font-medium">
                        {appointment.serviceName} ·{" "}
                        {timeText(appointment.startTime)}
                      </p>
                      <p className="text-sm text-muted-foreground">
                        {appointment.doctorDisplayName ?? "Chưa phân bác sĩ"} ·
                        Phí khám:{" "}
                        {appointment.examFeePaid === true
                          ? "đã thanh toán"
                          : appointment.examFeePaid === false
                            ? "chưa thanh toán"
                            : "chưa rõ"}
                      </p>
                    </div>
                    {appointment.status === "NO_SHOW" ? (
                      <p className="text-sm text-muted-foreground">
                        Lịch hẹn đã chuyển sang không đến; hãy tạo lượt walk-in.
                      </p>
                    ) : arrivals.some(
                        (arrival) =>
                          arrival.appointmentId === appointment.id &&
                          arrival.state !== "LATE",
                      ) ? (
                      <Button
                        type="button"
                        disabled={pending}
                        onClick={() => void submit(appointment.id)}
                      >
                        Check-in lịch hẹn
                      </Button>
                    ) : arrivals.some(
                        (arrival) =>
                          arrival.appointmentId === appointment.id &&
                          arrival.state === "LATE",
                      ) ? (
                      <p className="text-sm text-muted-foreground">
                        Bệnh nhân đến sau thời hạn check-in lịch hẹn; hãy tiếp
                        nhận thành walk-in.
                      </p>
                    ) : (
                      <div className="w-full space-y-2">
                        <p className="text-sm text-muted-foreground">
                          Chưa có ghi nhận bệnh nhân đến bằng QR.
                        </p>
                        <Textarea
                          aria-label="Lý do xác nhận bệnh nhân đã đến"
                          value={arrivalReason}
                          onChange={(event) =>
                            setArrivalReason(event.target.value)
                          }
                          placeholder="Lý do ghi nhận thủ công"
                          maxLength={500}
                        />
                        <Button
                          type="button"
                          disabled={
                            pending || arrivalReason.trim().length === 0
                          }
                          onClick={() => {
                            setPending(true);
                            setError("");
                            void recordManualReceptionArrival({
                              appointmentId: appointment.id,
                              reason: arrivalReason,
                            })
                              .then((arrival) => {
                                setArrivals((items) => [
                                  ...items.filter(
                                    (item) =>
                                      item.appointmentId !==
                                      arrival.appointmentId,
                                  ),
                                  arrival,
                                ]);
                                setArrivalReason("");
                              })
                              .catch((cause) =>
                                setError(
                                  isApiError(cause)
                                    ? cause.message
                                    : "Không thể ghi nhận bệnh nhân đã đến.",
                                ),
                              )
                              .finally(() => setPending(false));
                          }}
                        >
                          Xác nhận bệnh nhân đã đến
                        </Button>
                      </div>
                    )}
                  </div>
                ))}
            {(!initialAppointment ||
              appointments.some(
                (appointment) =>
                  appointment.id === initialAppointment &&
                  appointment.status === "NO_SHOW",
              ) ||
              arrivals.some(
                (arrival) =>
                  arrival.appointmentId === initialAppointment &&
                  arrival.state === "LATE",
              )) && (
              <Button
                type="button"
                variant="outline"
                disabled={pending}
                onClick={() => void submit(undefined, initialAppointment)}
              >
                Tiếp nhận walk-in
              </Button>
            )}
          </>
        )}
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        {result && (
          <p role="status" className="text-sm text-emerald-700">
            {result}
          </p>
        )}
        <div className="flex justify-end">
          <Button type="button" variant="outline" onClick={onClose}>
            Đóng
          </Button>
        </div>
      </form>
    </FormModal>
  );
}

export function IntakeScreen() {
  const params = useSearchParams();
  const [search, setSearch] = useState("");
  const [appointments, setAppointments] = useState<ReceptionAppointment[]>([]);
  const [arrivals, setArrivals] = useState<ReceptionArrival[]>([]);
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
    if (appointmentsResult.status === "fulfilled") {
      setAppointments(appointmentsResult.value.items);
    } else {
      const cause = appointmentsResult.reason;
      setError(isApiError(cause) ? cause.message : "Không thể tải lịch hẹn.");
    }
    if (arrivalsResult.status === "fulfilled") {
      setArrivals(arrivalsResult.value.items);
    } else {
      const cause = arrivalsResult.reason;
      setError(
        isApiError(cause)
          ? cause.message
          : "Không thể tải trạng thái bệnh nhân đã đến.",
      );
    }
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
      <p className="mt-5 text-xs text-muted-foreground">
        Phí khám được hiển thị theo trạng thái hóa đơn backend. Thu phí, ưu tiên
        và xếp phòng chưa thuộc thao tác check-in hiện được tích hợp.
      </p>
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
        <FormModal
          title="Chi tiết lịch hẹn"
          description="Thông tin lịch hẹn và hồ sơ bệnh nhân từ hệ thống."
          onClose={() => setDetail(null)}
          wide
        >
          <dl className="divide-y rounded-md border px-4">
            <DetailRow label="Mã lịch hẹn">{detail.id}</DetailRow>
            <DetailRow label="Bệnh nhân">
              {detail.patientName ?? "Chưa có tên bệnh nhân"}
            </DetailRow>
            <DetailRow label="Mã hồ sơ">{detail.patientProfileId}</DetailRow>
            <DetailRow label="Ngày / giờ">
              {timeText(detail.startTime)}
            </DetailRow>
            <DetailRow label="Dịch vụ">
              {detail.serviceName ?? "Chưa có thông tin dịch vụ"}
            </DetailRow>
            <DetailRow label="Bác sĩ">
              {detail.doctorDisplayName ?? "Chưa phân bác sĩ"}
            </DetailRow>
            <DetailRow label="Phí khám">
              {detail.examFeePaid === true
                ? "Đã thanh toán"
                : detail.examFeePaid === false
                  ? "Chưa thanh toán"
                  : "Chưa rõ"}
            </DetailRow>
            <DetailRow label="Trạng thái">
              <Status value={receptionStatusValue(detail.status)} />
            </DetailRow>
            {detail.purpose && (
              <DetailRow label="Lý do khám">{detail.purpose}</DetailRow>
            )}
          </dl>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setDetail(null)}>
              Đóng
            </Button>
            {["CONFIRMED", "NO_SHOW"].includes(detail.status) && (
              <Button
                onClick={() => {
                  setCheckIn({
                    patientId: detail.patientProfileId,
                    appointmentId: detail.id,
                  });
                  setDetail(null);
                }}
              >
                Hỗ trợ tiếp nhận
              </Button>
            )}
          </div>
        </FormModal>
      )}
      {qrOpen && <ReceptionArrivalQr onClose={() => setQrOpen(false)} />}
    </>
  );
}

function DetailRow({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="grid gap-1 py-3 sm:grid-cols-[10rem_1fr] sm:gap-4">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="min-w-0 break-words text-sm font-medium">{children}</dd>
    </div>
  );
}

function ReceptionArrivalQr({ onClose }: { onClose: () => void }) {
  const [proof, setProof] = useState("");
  const [expiresAt, setExpiresAt] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    let timer: number | undefined;
    const refresh = async () => {
      try {
        const result = await getCurrentClinicArrivalProof();
        if (!active) return;
        setProof(result.proof);
        setExpiresAt(result.expiresAt);
        setError("");
        timer = window.setTimeout(
          () => void refresh(),
          Math.max(5, result.refreshAfterSeconds) * 1000,
        );
      } catch (cause) {
        if (!active) return;
        setError(
          isApiError(cause) && cause.status === 503
            ? "Backend chưa nạp APP_ARRIVAL_QR_HMAC_SECRET. Hãy khởi động lại backend sau khi cấu hình backend/.env."
            : isApiError(cause)
              ? cause.message
              : "Không thể tạo mã QR check-in.",
        );
        timer = window.setTimeout(() => void refresh(), 5000);
      }
    };
    void refresh();
    return () => {
      active = false;
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, []);

  return (
    <FormModal
      title="Mã QR check-in tại quầy"
      description="Mời bệnh nhân đăng nhập, mở lịch hẹn và quét mã này để xác nhận đã đến. Mã tự làm mới định kỳ."
      onClose={onClose}
    >
      <div className="flex flex-col items-center gap-4">
        {proof ? (
          <div className="rounded-xl bg-white p-4">
            <QRCodeSVG value={proof} size={240} level="M" />
          </div>
        ) : (
          <p role={error ? "alert" : "status"} className="text-sm">
            {error || "Đang tạo mã QR…"}
          </p>
        )}
        {expiresAt && (
          <p className="text-xs text-muted-foreground">
            Mã hiện tại hết hạn lúc{" "}
            {new Date(expiresAt).toLocaleTimeString("vi-VN", {
              timeZone: "Asia/Ho_Chi_Minh",
            })}
          </p>
        )}
        {error && proof && (
          <p role="alert" className="text-sm text-destructive">
            {error}. Hệ thống sẽ thử tạo mã mới.
          </p>
        )}
        <Button variant="outline" onClick={onClose}>
          Đóng
        </Button>
      </div>
    </FormModal>
  );
}
