"use client";

import { RefreshCw, Search, Stethoscope } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { DoctorLoadState } from "./DoctorLoadState";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDate, formatTime, todayIsoDate } from "@/lib/format/formatDate";
import { cn } from "@/lib/utils";
import { doctorVisitHref } from "@/lib/routes/doctorRoutes";
import { useTodayPatients } from "../hooks/useDoctorData";
import {
  TODAY_FILTERS,
  canOpenVisit,
  countByStatus,
  filterPatients,
  type TodayFilter,
} from "../schema/todayPatientsModel";
import type { TodayPatient } from "../types/doctorExam";
import { TODAY_STATUS_LABELS, TodayStatusBadge } from "./doctorStatus";

const FILTER_LABELS: Record<TodayFilter, string> = {
  ...TODAY_STATUS_LABELS,
  ALL: "Tất cả",
};

function PatientCell({ patient }: { patient: TodayPatient }) {
  const facts = [
    patient.age !== null ? `${patient.age} tuổi` : null,
    patient.sex,
    patient.patientCode,
  ].filter(Boolean);
  return (
    <div className="min-w-0">
      <p className="truncate font-medium">{patient.patientName}</p>
      <p className="truncate text-xs text-muted-foreground">
        {facts.length ? facts.join(" · ") : patient.serviceName}
      </p>
    </div>
  );
}

function PatientRow({ patient }: { patient: TodayPatient }) {
  const open = canOpenVisit(patient);
  return (
    <TableRow>
      <TableCell className="w-14 tabular-nums">
        {patient.queueNumber ?? "—"}
      </TableCell>
      <TableCell className="w-20 tabular-nums">
        {patient.scheduledAt ? formatTime(patient.scheduledAt) : "—"}
      </TableCell>
      <TableCell>
        <PatientCell patient={patient} />
      </TableCell>
      <TableCell className="hidden xl:table-cell">
        {patient.serviceName}
      </TableCell>
      <TableCell className="hidden lg:table-cell">
        <StatusBadge tone="neutral">
          {patient.source === "WALK_IN" ? "Vãng lai" : "Đặt hẹn"}
        </StatusBadge>
      </TableCell>
      <TableCell>
        <TodayStatusBadge status={patient.status} />
      </TableCell>
      <TableCell className="text-right">
        {open ? (
          <Link
            href={doctorVisitHref(patient.visitId!)}
            className={buttonVariants({
              size: "sm",
              variant: patient.status === "COMPLETED" ? "outline" : "default",
            })}
          >
            <Stethoscope aria-hidden />
            {patient.status === "COMPLETED" ? "Xem lượt khám" : "Mở lượt khám"}
          </Link>
        ) : (
          <span className="text-xs text-muted-foreground">
            Chờ lễ tân tiếp nhận
          </span>
        )}
      </TableCell>
    </TableRow>
  );
}

export function TodayPatientsScreen() {
  const { data, error, loading, refresh } = useTodayPatients();
  const [filter, setFilter] = useState<TodayFilter>("ALL");
  const [query, setQuery] = useState("");
  const counts = countByStatus(data ?? []);
  const rows = filterPatients(data ?? [], filter, query);

  return (
    <>
      <PageHeader
        title="Bệnh nhân hôm nay"
        description={`${formatDate(todayIsoDate())} · Bệnh nhân đặt hẹn và vãng lai được giao cho bạn, cùng các lượt khám đang chờ kết luận.`}
        actions={
          <Button variant="outline" onClick={refresh}>
            <RefreshCw aria-hidden />
            Làm mới
          </Button>
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div
          role="group"
          aria-label="Lọc theo trạng thái"
          className="flex flex-wrap gap-1.5"
        >
          {TODAY_FILTERS.map((item) => (
            <Button
              key={item}
              size="sm"
              variant={filter === item ? "default" : "outline"}
              aria-pressed={filter === item}
              onClick={() => setFilter(item)}
            >
              {FILTER_LABELS[item]}
              <span
                className={cn(
                  "tabular-nums",
                  filter === item ? "opacity-90" : "text-muted-foreground",
                )}
              >
                {counts[item]}
              </span>
            </Button>
          ))}
        </div>
        <div className="relative ml-auto w-full sm:w-64">
          <Search
            aria-hidden
            className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground"
          />
          <Input
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Tìm theo tên hoặc mã bệnh nhân"
            aria-label="Tìm bệnh nhân"
            className="pl-8"
          />
        </div>
      </div>

      {loading || error ? (
        <DoctorLoadState loading={loading} error={error} retry={refresh} />
      ) : rows.length === 0 ? (
        <div className="rounded-lg border bg-card px-4 py-12 text-center">
          <p className="font-medium">
            {data?.length
              ? "Không có bệnh nhân phù hợp"
              : "Hôm nay chưa có bệnh nhân"}
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            {data?.length
              ? "Thử đổi bộ lọc hoặc từ khóa tìm kiếm."
              : "Bệnh nhân được lễ tân tiếp nhận sẽ hiện ở đây."}
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border bg-card">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>STT</TableHead>
                <TableHead>Giờ</TableHead>
                <TableHead>Bệnh nhân</TableHead>
                <TableHead className="hidden xl:table-cell">Dịch vụ</TableHead>
                <TableHead className="hidden lg:table-cell">Nguồn</TableHead>
                <TableHead>Trạng thái</TableHead>
                <TableHead className="text-right">
                  <span className="sr-only">Thao tác</span>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((patient) => (
                <PatientRow
                  key={
                    patient.visitId ??
                    patient.appointmentId ??
                    patient.patientName
                  }
                  patient={patient}
                />
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </>
  );
}
