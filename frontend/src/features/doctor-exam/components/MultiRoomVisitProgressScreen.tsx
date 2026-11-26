"use client";

import Link from "next/link";
import { ArrowRight, RefreshCw } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button, buttonVariants } from "@/components/ui/button";
import { CLINICAL_ORDER_PAYMENT_LABELS, CLINICAL_ORDER_STATUS_LABELS } from "@/lib/domain/clinicVisit";
import { formatDate, formatTime, todayIsoDate } from "@/lib/format/formatDate";
import { cn } from "@/lib/utils";
import { doctorVisitHref } from "@/lib/routes/doctorRoutes";
import { useTodayPatients } from "../hooks/useDoctorData";
import { canOpenVisit } from "../schema/todayPatientsModel";
import { TodayStatusBadge } from "./doctorStatus";

export function MultiRoomVisitProgressScreen() {
  const { data, error, loading, refresh } = useTodayPatients();
  const visits = (data ?? []).filter((patient) => canOpenVisit(patient));

  return (
    <>
      <PageHeader
        title="Tiến trình nhiều phòng"
        description={`${formatDate(todayIsoDate())} · Theo dõi trạng thái lượt khám và các chỉ định của bệnh nhân hôm nay.`}
        actions={
          <Button variant="outline" onClick={refresh}>
            <RefreshCw aria-hidden /> Làm mới
          </Button>
        }
      />
      {loading ? (
        <p role="status">Đang tải tiến trình lượt khám…</p>
      ) : error ? (
        <div role="alert" className="space-y-2">
          <p>Chưa tải được tiến trình lượt khám.</p>
          <Button variant="outline" onClick={refresh}>Thử lại</Button>
        </div>
      ) : visits.length === 0 ? (
        <p className="rounded-lg border bg-card px-4 py-8 text-center text-sm text-muted-foreground">
          Hôm nay chưa có lượt khám cần theo dõi.
        </p>
      ) : (
        <ul className="space-y-3">
          {visits.map((patient) => (
            <li key={patient.visitId} className="space-y-3 rounded-lg border bg-card p-4">
              <div className="flex flex-wrap items-center gap-3">
                <div className="min-w-0 flex-1">
                  <p className="font-medium">{patient.patientName}</p>
                  <p className="text-sm text-muted-foreground">
                    {patient.scheduledAt ? formatTime(patient.scheduledAt) : "—"} · {patient.serviceName}
                  </p>
                </div>
                <TodayStatusBadge status={patient.status} />
                <Link href={doctorVisitHref(patient.visitId!)} className={cn(buttonVariants({ size: "sm", variant: "outline" }))}>
                  Mở lượt khám <ArrowRight aria-hidden />
                </Link>
              </div>
              {patient.serviceOrders?.length ? (
                <ul className="divide-y rounded-md border">
                  {patient.serviceOrders.map((order) => (
                    <li key={order.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 text-sm">
                      <span>{order.serviceName}</span>
                      <span className="flex flex-wrap gap-2">
                        <StatusBadge tone="neutral">{CLINICAL_ORDER_STATUS_LABELS[order.status]}</StatusBadge>
                        <StatusBadge tone="neutral">{CLINICAL_ORDER_PAYMENT_LABELS[order.paymentStatus]}</StatusBadge>
                      </span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-muted-foreground">Chưa có chỉ định cận lâm sàng.</p>
              )}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
