"use client";

import { PlayCircle } from "lucide-react";
import { useState } from "react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { formatTime } from "@/lib/format/formatDate";
import { notifySuccess } from "@/lib/messages/notify";
import { notifyDoctorError } from "../schema/doctorErrors";
import { getDoctorExamApi } from "../api/doctorExamApi";
import { TodayStatusBadge } from "./doctorStatus";
import { useVisitWorkspace } from "./VisitWorkspaceProvider";

// Thanh thông tin tối thiểu của bệnh nhân luôn hiện trong workspace khám (R3 màn 9, BR-13):
// họ tên, tuổi, giới, mã bệnh nhân, dị ứng, tiền sử; không có số điện thoại hay địa chỉ.

function initials(name: string | null): string {
  const words = (name ?? "").trim().split(/\s+/).filter(Boolean);
  return (
    words.length
      ? words
          .slice(-2)
          .map((w) => w[0])
          .join("")
      : "?"
  ).toUpperCase();
}

function Fact({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="truncate text-sm">{value || "—"}</dd>
    </div>
  );
}

export function VisitPatientHeader() {
  const { context, loading, error, refresh } = useVisitWorkspace();
  const [starting, setStarting] = useState(false);

  if (loading)
    return (
      <Skeleton
        aria-label="Đang tải thông tin bệnh nhân"
        className="mb-6 h-32 w-full"
      />
    );
  if (error || !context)
    return (
      <Alert variant="destructive" className="mb-6">
        <AlertDescription className="flex items-center justify-between gap-3">
          Không tải được thông tin bệnh nhân của lượt khám này.
          <Button size="sm" variant="outline" onClick={refresh}>
            Thử lại
          </Button>
        </AlertDescription>
      </Alert>
    );

  const canStart = context.status === "WAITING_FOR_EXAMINATION";
  async function start() {
    if (!context) return;
    setStarting(true);
    try {
      await getDoctorExamApi().startExamination(context);
      notifySuccess("MSG-43");
      refresh();
    } catch (cause) {
      notifyDoctorError(cause);
    } finally {
      setStarting(false);
    }
  }

  const facts = [
    context.age !== null ? `${context.age} tuổi` : null,
    context.sex,
    context.patientCode,
  ].filter(Boolean);

  return (
    <Card className="mb-6" aria-label="Thông tin bệnh nhân của lượt khám">
      <CardContent className="space-y-4 py-4">
        <div className="flex flex-wrap items-center gap-4">
          <span
            aria-hidden
            className="flex size-12 shrink-0 items-center justify-center rounded-full bg-secondary text-base font-semibold text-secondary-foreground"
          >
            {initials(context.patientName)}
          </span>
          <div className="min-w-0 flex-1">
            <h2 className="truncate text-lg font-semibold">
              {context.patientName ?? "Chưa tải được tên bệnh nhân"}
            </h2>
            <p className="truncate text-sm text-muted-foreground">
              {facts.length
                ? facts.join(" · ")
                : "Tuổi, giới tính và mã bệnh nhân chưa có từ hệ thống"}
              {context.serviceName ? ` · ${context.serviceName}` : ""}
              {context.scheduledAt
                ? ` · ${formatTime(context.scheduledAt)}`
                : ""}
            </p>
          </div>
          <div className="flex items-center gap-3">
            {context.status ? (
              <TodayStatusBadge status={context.status} />
            ) : null}
            {canStart ? (
              <Button onClick={start} disabled={starting}>
                <PlayCircle aria-hidden />
                {starting ? "Đang bắt đầu..." : "Bắt đầu khám"}
              </Button>
            ) : null}
          </div>
        </div>
        <dl className="grid gap-x-6 gap-y-3 border-t pt-4 sm:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
          <div className="min-w-0">
            <dt className="text-xs text-muted-foreground">Dị ứng</dt>
            <dd className="mt-0.5 text-sm">
              {!context.allergiesLoaded && context.patientProfileId ? (
                <StatusBadge tone="warning">
                  Chưa đọc được dị ứng, hỏi bệnh nhân trước khi chỉ định
                </StatusBadge>
              ) : context.allergies && context.allergies !== "Không" ? (
                <StatusBadge tone="danger">{context.allergies}</StatusBadge>
              ) : (
                <span className="text-muted-foreground">
                  {context.allergies || "Chưa ghi nhận"}
                </span>
              )}
            </dd>
          </div>
          <Fact label="Tiền sử bệnh" value={context.medicalHistory} />
        </dl>
      </CardContent>
    </Card>
  );
}
