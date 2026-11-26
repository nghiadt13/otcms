"use client";

import { useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { DoctorLoadState } from "./DoctorLoadState";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { formatDate, formatDateTime } from "@/lib/format/formatDate";
import {
  useDoctorExamApi,
  useMedicalHistory,
  usePastVisits,
  usePatientAppointments,
} from "../hooks/useDoctorData";
import { BackendGapNotice } from "./BackendGapNotice";
import { MedicalHistoryForm } from "./MedicalHistoryForm";
import { useVisitWorkspace } from "./VisitWorkspaceProvider";
import { DoctorImagingViewer } from "./DoctorImagingViewer";

// UC33 uses current Visit; history and appointments use Patient ID.

function PastVisits({ visitId }: { visitId: string }) {
  const api = useDoctorExamApi();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(5);
  const [date, setDate] = useState("");
  const { data, loading, error, refresh } = usePastVisits(visitId, {
    page,
    size,
    date,
  });
  const totalPages = Math.max(1, Math.ceil((data?.total ?? 0) / size));
  const firstItem = data && data.total > 0 ? page * size + 1 : 0;
  const lastItem = data ? Math.min((page + 1) * size, data.total) : 0;

  function updateDate(value: string) {
    setDate(value);
    setPage(0);
  }

  function updateSize(value: number) {
    setSize(value);
    setPage(0);
  }

  return (
    <section aria-labelledby="past-title" className="space-y-3">
      <h2 id="past-title" className="text-lg font-semibold">
        Hồ sơ bệnh án cũ
      </h2>
      <div className="flex flex-wrap items-end gap-3 rounded-lg border bg-card p-3">
        <label className="grid min-w-48 gap-1 text-sm">
          <span>Lọc theo ngày khám</span>
          <Input
            aria-label="Lọc hồ sơ theo ngày khám"
            type="date"
            value={date}
            onChange={(event) => updateDate(event.target.value)}
          />
        </label>
        <label className="grid min-w-36 gap-1 text-sm">
          <span>Số hồ sơ mỗi trang</span>
          <NativeSelect
            aria-label="Số hồ sơ mỗi trang"
            value={String(size)}
            onChange={(event) => updateSize(Number(event.target.value))}
          >
            {[5, 10, 20].map((count) => (
              <NativeSelectOption key={count} value={String(count)}>
                {count} hồ sơ
              </NativeSelectOption>
            ))}
          </NativeSelect>
        </label>
        {date ? (
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              setDate("");
              setPage(0);
            }}
          >
            Xóa bộ lọc
          </Button>
        ) : null}
      </div>
      {!api.capabilities.pastRecords ? (
        <BackendGapNotice title="Chưa có API hồ sơ lượt khám cũ">
          Chẩn đoán, lời dặn, đơn thuốc, kết quả hình ảnh và xét nghiệm của các
          lượt trước sẽ hiện ở đây.
        </BackendGapNotice>
      ) : loading ? (
        <Skeleton className="h-28 w-full" />
      ) : error ? (
        <DoctorLoadState loading={false} error={error} retry={refresh} />
      ) : !data || data.total === 0 ? (
        <p className="rounded-lg border bg-card px-4 py-6 text-center text-sm text-muted-foreground">
          {date
            ? "Không tìm thấy hồ sơ phù hợp với bộ lọc."
            : "Bệnh nhân chưa có lượt khám nào trước đây."}
        </p>
      ) : (
        <>
          <ul className="divide-y rounded-lg border bg-card">
            {data.items.map((visit) => (
              <li key={visit.visitId} className="space-y-3 px-4 py-5">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-base font-semibold tabular-nums">
                    {formatDate(visit.visitDate)}
                  </span>
                  <StatusBadge tone={visit.status === "INCOMPLETE" ? "warning" : "success"}>
                    {visit.status === "INCOMPLETE" ? "Chưa hoàn tất" : "Đã hoàn tất"}
                  </StatusBadge>
                </div>
                <p className="text-sm text-muted-foreground">
                  Chẩn đoán: {visit.primaryDiagnosis ?? "Chưa ghi nhận"}
                  {visit.prescriptions
                    ? ` · ${visit.prescriptions} đơn thuốc`
                    : ""}
                </p>
                {visit.advice ||
                visit.imaging.length > 0 ||
                visit.laboratory.length > 0 ||
                visit.prescriptionItems?.length ||
                visit.prescriptions > 0 ? (
                  <details className="rounded-lg border bg-background px-4 py-3">
                    <summary className="min-h-11 cursor-pointer py-2 text-sm font-semibold text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
                      Xem kết quả và điều trị
                    </summary>
                    <div className="mt-3 space-y-4">
                      {visit.adviceSections?.length ? (
                        <section className="space-y-2">
                          <h3 className="text-sm font-semibold">
                            Lời dặn sau khám
                          </h3>
                          <dl className="divide-y rounded-md border">
                            {visit.adviceSections.map((section) => (
                              <div
                                key={section.label}
                                className="grid gap-1 px-3 py-3 sm:grid-cols-[12rem_1fr]"
                              >
                                <dt className="text-sm font-medium text-muted-foreground">
                                  {section.label}
                                </dt>
                                <dd className="whitespace-pre-wrap text-sm leading-6">
                                  {section.text}
                                </dd>
                              </div>
                            ))}
                          </dl>
                        </section>
                      ) : visit.advice ? (
                        <section className="space-y-1">
                          <h3 className="text-sm font-semibold">
                            Lời dặn sau khám
                          </h3>
                          <p className="whitespace-pre-wrap text-sm leading-6 text-muted-foreground">
                            {visit.advice}
                          </p>
                        </section>
                      ) : null}
                      {visit.imaging.length > 0 ? (
                        <section className="space-y-2">
                          <h3 className="text-sm font-semibold">
                            Kết quả chẩn đoán hình ảnh
                          </h3>
                          <ul className="space-y-2">
                            {visit.imaging.map((line) => {
                              const separator = line.indexOf(":");
                              const title =
                                separator >= 0
                                  ? line.slice(0, separator)
                                  : "Kết luận";
                              const result =
                                separator >= 0
                                  ? line.slice(separator + 1).trim()
                                  : line;
                              const study = visit.imagingStudies?.find(
                                (item) => [item.imagingType?.trim(), item.findingsText?.trim()]
                                  .filter(Boolean).join(": ") === line,
                              );
                              return (
                                <li
                                  key={line}
                                  className="space-y-3 rounded-md border p-3"
                                >
                                  <h4 className="text-sm font-medium">
                                    {title}
                                  </h4>
                                  <p className="mt-1 whitespace-pre-wrap text-sm leading-6">
                                    {result}
                                  </p>
                                  {study?.studyRef ? (
                                    <div className="overflow-hidden rounded-md border bg-muted/20">
                                      <DoctorImagingViewer studyRef={study.studyRef} />
                                    </div>
                                  ) : (
                                    <p className="text-sm text-muted-foreground">
                                      Chưa có ảnh DICOM cho kết quả này.
                                    </p>
                                  )}
                                </li>
                              );
                            })}
                          </ul>
                        </section>
                      ) : null}
                      {visit.laboratory.length > 0 ? (
                        <section className="space-y-2">
                          <h3 className="text-sm font-semibold">
                            Kết quả xét nghiệm
                          </h3>
                          <ul className="space-y-2">
                            {visit.laboratory.map((line) => (
                              <li
                                key={line}
                                className="rounded-md bg-background p-3 text-sm leading-6"
                              >
                                {line}
                              </li>
                            ))}
                          </ul>
                        </section>
                      ) : null}
                      {visit.prescriptionItems?.length ? (
                        <section className="space-y-2">
                          <h3 className="text-sm font-semibold">Đơn thuốc</h3>
                          <ul className="divide-y rounded-md border">
                            {visit.prescriptionItems.map((item, index) => (
                              <li key={`${item.medicine}-${index}`} className="space-y-1 px-3 py-3 text-sm">
                                <p className="font-medium">{item.medicine}</p>
                                <p className="text-muted-foreground">
                                  {item.dose} · {item.frequency}
                                </p>
                                <p className="whitespace-pre-wrap leading-6">{item.usage}</p>
                              </li>
                            ))}
                          </ul>
                        </section>
                      ) : null}
                    </div>
                  </details>
                ) : (
                  <p className="text-sm text-muted-foreground">
                    Lượt khám này chưa có lời dặn hoặc kết quả cận lâm sàng.
                  </p>
                )}
              </li>
            ))}
          </ul>
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-card px-4 py-3 text-sm">
            <p className="text-muted-foreground">
              Hiển thị {firstItem}–{lastItem} / {data.total} hồ sơ
            </p>
            <div className="flex items-center gap-2">
              <span className="text-muted-foreground">
                Trang {page + 1} / {totalPages}
              </span>
              <Button
                type="button"
                variant="outline"
                disabled={page === 0 || loading}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                Trước
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={page + 1 >= totalPages || loading}
                onClick={() =>
                  setPage((current) => Math.min(totalPages - 1, current + 1))
                }
              >
                Tiếp
              </Button>
            </div>
          </div>
        </>
      )}
    </section>
  );
}

const APPOINTMENT_STATUS: Record<string, string> = {
  CONFIRMED: "Đã đặt",
  CHECKED_IN: "Đã tiếp nhận",
  IN_PROGRESS: "Đang khám",
  COMPLETED: "Đã hoàn tất",
  CANCELLED: "Đã hủy",
  NO_SHOW: "Không đến",
  // Giữ giờ chờ thanh toán: BE đã ẩn khỏi bác sĩ; chỉ để lỡ trả ra thì không hiện chuỗi thô.
  AWAITING_PAYMENT: "Chờ thanh toán",
  PAYMENT_EXPIRED: "Hết hạn giữ chỗ",
};

function Appointments({ patientProfileId }: { patientProfileId: string }) {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(5);
  const [date, setDate] = useState("");
  const [status, setStatus] = useState("");
  const { data, loading, error, refresh } = usePatientAppointments(
    patientProfileId,
    { page, size, date, status },
  );
  const totalPages = Math.max(1, data?.totalPages ?? 1);
  const total = data?.totalElements ?? 0;
  const firstItem = total > 0 ? page * size + 1 : 0;
  const lastItem = Math.min((page + 1) * size, total);

  return (
    <section aria-labelledby="appt-title" className="space-y-3">
      <h2 id="appt-title" className="text-lg font-semibold">
        Lịch hẹn của bệnh nhân
      </h2>
      <div className="flex flex-wrap items-end gap-3 rounded-lg border bg-card p-3">
        <label className="grid min-w-44 gap-1 text-sm">
          <span>Trạng thái lịch hẹn</span>
          <NativeSelect
            aria-label="Lọc lịch hẹn theo trạng thái"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
          >
            <NativeSelectOption value="">Tất cả trạng thái</NativeSelectOption>
            {Object.entries(APPOINTMENT_STATUS)
              .filter(
                ([key]) =>
                  key !== "AWAITING_PAYMENT" && key !== "PAYMENT_EXPIRED",
              )
              .map(([key, label]) => (
                <NativeSelectOption key={key} value={key}>
                  {label}
                </NativeSelectOption>
              ))}
          </NativeSelect>
        </label>
        <label className="grid min-w-44 gap-1 text-sm">
          <span>Lọc theo ngày hẹn</span>
          <Input
            aria-label="Lọc lịch hẹn theo ngày"
            type="date"
            value={date}
            onChange={(event) => {
              setDate(event.target.value);
              setPage(0);
            }}
          />
        </label>
        <label className="grid min-w-36 gap-1 text-sm">
          <span>Số lịch hẹn mỗi trang</span>
          <NativeSelect
            aria-label="Số lịch hẹn mỗi trang"
            value={String(size)}
            onChange={(event) => {
              setSize(Number(event.target.value));
              setPage(0);
            }}
          >
            {[5, 10, 20].map((count) => (
              <NativeSelectOption key={count} value={String(count)}>
                {count} lịch hẹn
              </NativeSelectOption>
            ))}
          </NativeSelect>
        </label>
        {date || status ? (
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              setDate("");
              setStatus("");
              setPage(0);
            }}
          >
            Xóa bộ lọc
          </Button>
        ) : null}
      </div>
      {loading || error ? (
        <DoctorLoadState loading={loading} error={error} retry={refresh} />
      ) : !data || data.items.length === 0 ? (
        <p className="rounded-lg border bg-card px-4 py-6 text-center text-sm text-muted-foreground">
          {date || status
            ? "Không tìm thấy lịch hẹn phù hợp với bộ lọc."
            : "Chưa có lịch hẹn nào."}
        </p>
      ) : (
        <>
          <ul className="divide-y rounded-lg border bg-card">
            {data.items.map((appointment) => (
              <li
                key={appointment.id}
                className="flex min-h-16 items-center justify-between gap-3 px-4 py-2.5"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">
                    {appointment.serviceName}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {formatDateTime(appointment.startTime)}
                  </p>
                </div>
                <StatusBadge tone="neutral">
                  {APPOINTMENT_STATUS[appointment.status] ?? appointment.status}
                </StatusBadge>
              </li>
            ))}
          </ul>
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-card px-4 py-3 text-sm">
            <p className="text-muted-foreground">
              Hiển thị {firstItem}–{lastItem} / {total} lịch hẹn
            </p>
            <div className="flex items-center gap-2">
              <span className="text-muted-foreground">
                Trang {page + 1} / {totalPages}
              </span>
              <Button
                type="button"
                variant="outline"
                disabled={page === 0 || loading}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                Trước
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={page + 1 >= totalPages || loading}
                onClick={() =>
                  setPage((current) => Math.min(totalPages - 1, current + 1))
                }
              >
                Tiếp
              </Button>
            </div>
          </div>
        </>
      )}
    </section>
  );
}

function History({ patientProfileId }: { patientProfileId: string }) {
  const { data, error, loading, refresh } = useMedicalHistory(patientProfileId);
  const workspace = useVisitWorkspace();
  if (loading || error || !data)
    return <DoctorLoadState loading={loading} error={error} retry={refresh} />;
  return (
    <MedicalHistoryForm
      key={data.version}
      patientProfileId={patientProfileId}
      initial={data}
      onSaved={() => {
        refresh();
        workspace.refresh(); // header hiện dị ứng, phải cập nhật theo
      }}
    />
  );
}

export function RecordsScreen() {
  const { visitId, context, loading } = useVisitWorkspace();
  const patientProfileId = context?.patientProfileId ?? null;

  return (
    <>
      <PageHeader
        title="Hồ sơ bệnh nhân"
        description="Tiền sử, hồ sơ các lượt khám trước và lịch hẹn để tham khảo khi khám."
      />
      {loading ? (
        <Skeleton className="h-40 w-full" />
      ) : !patientProfileId ? (
        <BackendGapNotice title="Chưa xác định được hồ sơ bệnh nhân">
          Backend chưa cho bác sĩ đọc hồ sơ bệnh nhân của lượt khám vãng lai.
        </BackendGapNotice>
      ) : (
        <div className="space-y-8">
          <History patientProfileId={patientProfileId} />
          <PastVisits visitId={visitId} />
          <Appointments patientProfileId={patientProfileId} />
        </div>
      )}
    </>
  );
}
