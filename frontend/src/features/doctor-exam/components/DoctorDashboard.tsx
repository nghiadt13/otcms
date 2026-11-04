"use client";

import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { PageHeader } from "@/components/PageHeader";
import { DoctorLoadState } from "./DoctorLoadState";
import { buttonVariants } from "@/components/ui/button";
import { formatDate, formatTime, todayIsoDate } from "@/lib/format/formatDate";
import { DOCTOR_ROUTES, doctorVisitHref } from "@/lib/routes/doctorRoutes";
import { useTodayPatients } from "../hooks/useDoctorData";
import { canOpenVisit, countByStatus } from "../schema/todayPatientsModel";
import { TodayStatusBadge } from "./doctorStatus";

// Bảng điều khiển: tóm tắt công việc trong ngày và lối vào bệnh nhân cần xử lý (R3 màn 4).
// Chỉ gồm những gì BE trả được; "ảnh mới cần xem" chờ BE liệt kê ca chụp theo bác sĩ.

const STATS = [
  ["WAITING_FOR_EXAMINATION", "Chờ khám"],
  ["IN_EXAMINATION", "Đang khám"],
  ["AWAITING_CONCLUSION", "Chờ kết luận"],
  ["COMPLETED", "Đã hoàn tất"],
] as const;

export function DoctorDashboard() {
  const { data, error, loading, refresh } = useTodayPatients();
  const counts = countByStatus(data ?? []);
  const active = (data ?? [])
    .filter(
      (p) =>
        canOpenVisit(p) &&
        (p.status === "IN_EXAMINATION" ||
          p.status === "AWAITING_CONCLUSION" ||
          p.status === "WAITING_FOR_EXAMINATION"),
    )
    .sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt));

  return (
    <>
      <PageHeader
        title="Bảng điều khiển"
        description={`${formatDate(todayIsoDate())} · Công việc của bạn trong ngày.`}
        actions={
          <Link
            href={DOCTOR_ROUTES.todayPatients}
            className={buttonVariants({ variant: "outline" })}
          >
            Bệnh nhân hôm nay
            <ArrowRight aria-hidden />
          </Link>
        }
      />
      {loading || error || !data ? (
        <DoctorLoadState loading={loading} error={error} retry={refresh} />
      ) : (
        <div className="space-y-8">
          <dl className="grid grid-cols-2 divide-x divide-y rounded-lg border bg-card sm:grid-cols-4 sm:divide-y-0">
            {STATS.map(([status, label]) => (
              <div key={status} className="px-5 py-4">
                <dt className="text-sm text-muted-foreground">{label}</dt>
                <dd className="mt-1 text-3xl font-semibold tabular-nums">
                  {counts[status]}
                </dd>
              </div>
            ))}
          </dl>

          <section aria-labelledby="todo-title">
            <h2 id="todo-title" className="mb-3 text-lg font-semibold">
              Cần xử lý
            </h2>
            {active.length === 0 ? (
              <p className="rounded-lg border bg-card px-4 py-8 text-center text-sm text-muted-foreground">
                Không có bệnh nhân nào đang chờ bạn.
              </p>
            ) : (
              <ul className="divide-y rounded-lg border bg-card">
                {active.map((patient) => (
                  <li
                    key={patient.visitId}
                    className="flex flex-wrap items-center gap-3 px-4 py-3"
                  >
                    <span className="w-14 text-sm tabular-nums text-muted-foreground">
                      {formatTime(patient.scheduledAt)}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium">
                        {patient.patientName}
                      </p>
                      <p className="truncate text-xs text-muted-foreground">
                        {patient.serviceName}
                      </p>
                    </div>
                    <TodayStatusBadge status={patient.status} />
                    <Link
                      href={doctorVisitHref(patient.visitId!)}
                      className={buttonVariants({ size: "sm" })}
                    >
                      Mở lượt khám
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      )}
    </>
  );
}
