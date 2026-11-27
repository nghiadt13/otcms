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
import {
  CLINICAL_ORDER_PAYMENT_LABELS,
  CLINICAL_ORDER_PAYMENT_TONE,
  CLINICAL_ORDER_STATUS_LABELS,
  CLINICAL_ORDER_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import {
  formatDate,
  formatDateTime,
  todayIsoDate,
} from "@/lib/format/formatDate";
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
    patient.source === "WALK_IN" ? "Vãng lai" : "Đặt hẹn",
  ].filter(Boolean);
  return (
    <div className="min-w-0">
      <p className="break-words font-medium">{patient.patientName}</p>
      {facts.length ? (
        <p className="break-words text-xs text-muted-foreground">
          {facts.join(" · ")}
        </p>
      ) : null}
    </div>
  );
}

function ServiceCell({ patient }: { patient: TodayPatient }) {
  const orders = patient.serviceOrders ?? [];
  return (
    <div className="min-w-0 space-y-1">
      <p className="break-words">{patient.serviceName}</p>
      {orders.length ? (
        <details>
          <summary className="inline-flex min-h-11 cursor-pointer items-center text-sm font-medium text-primary underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
            {orders.length} chỉ định cận lâm sàng
          </summary>
          <ul className="space-y-2 border-t pt-2">
            {orders.map((order) => (
              <li key={order.id} className="space-y-1">
                <p className="break-words text-sm font-medium">
                  {order.serviceName}
                </p>
                <div className="flex flex-wrap gap-1.5">
                  <StatusBadge tone={CLINICAL_ORDER_STATUS_TONE[order.status]}>
                    {CLINICAL_ORDER_STATUS_LABELS[order.status]}
                  </StatusBadge>
                  <StatusBadge
                    tone={CLINICAL_ORDER_PAYMENT_TONE[order.paymentStatus]}
                  >
                    {CLINICAL_ORDER_PAYMENT_LABELS[order.paymentStatus]}
                  </StatusBadge>
                </div>
              </li>
            ))}
          </ul>
        </details>
      ) : null}
    </div>
  );
}

function PatientRow({
  patient,
  rowNumber,
}: {
  patient: TodayPatient;
  rowNumber: number;
}) {
  const open = canOpenVisit(patient);
  return (
    <TableRow>
      <TableCell className="w-[5%] whitespace-nowrap tabular-nums">
        <span>{rowNumber}</span>
      </TableCell>
      <TableCell className="w-[12%] whitespace-normal font-mono break-all">
        {patient.patientCode ?? "—"}
      </TableCell>
      <TableCell className="w-[23%] whitespace-normal">
        <PatientCell patient={patient} />
      </TableCell>
      <TableCell className="w-[22%] whitespace-normal break-words">
        <ServiceCell patient={patient} />
      </TableCell>
      <TableCell className="w-[14%] whitespace-normal tabular-nums">
        {patient.scheduledAt ? formatDateTime(patient.scheduledAt) : "—"}
      </TableCell>
      <TableCell className="w-[10%] whitespace-normal">
        <TodayStatusBadge status={patient.status} />
      </TableCell>
      <TableCell className="w-[14%] whitespace-normal text-right">
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
          <Button variant="outline" onClick={refresh} disabled={loading}>
            <RefreshCw
              aria-hidden
              className={cn(
                loading && "animate-spin motion-reduce:animate-none",
              )}
            />
            {loading ? "Đang tải..." : "Làm mới"}
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
        <div className="relative ml-auto w-full sm:w-72 lg:w-80">
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
        <div>
          <p aria-live="polite" className="mb-2 text-sm text-muted-foreground">
            Hiển thị {rows.length} / {data?.length ?? 0} bệnh nhân
          </p>
          <div className="rounded-lg border bg-card">
            <Table className="min-w-[48rem] table-fixed lg:min-w-0">
              <colgroup>
                <col className="w-[5%]" />
                <col className="w-[12%]" />
                <col className="w-[23%]" />
                <col className="w-[22%]" />
                <col className="w-[14%]" />
                <col className="w-[10%]" />
                <col className="w-[14%]" />
              </colgroup>
              <TableHeader>
                <TableRow>
                  <TableHead>STT</TableHead>
                  <TableHead>Mã bệnh nhân</TableHead>
                  <TableHead>Tên bệnh nhân</TableHead>
                  <TableHead>Dịch vụ / chỉ định</TableHead>
                  <TableHead>Thời gian</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead className="text-right">Tác vụ</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((patient, index) => (
                  <PatientRow
                    key={
                      patient.visitId ??
                      patient.appointmentId ??
                      patient.patientName
                    }
                    patient={patient}
                    rowNumber={index + 1}
                  />
                ))}
              </TableBody>
            </Table>
          </div>
        </div>
      )}
    </>
  );
}
