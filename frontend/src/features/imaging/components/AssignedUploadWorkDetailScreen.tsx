"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ArrowLeft } from "lucide-react";
import { Button } from "@/components/ui/button";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  listAssignedImagingUploadWork,
  type AssignedImagingUploadWork,
} from "../api/imagingIngestApi";
import { Empty } from "./Empty";
import { ImagingViewerLauncher } from "./ImagingViewerLauncher";
import { PageHeading } from "./PageHeading";
import { Panel } from "./Panel";

function DetailField({ label, value, className = "" }: { label: string; value: string | null | undefined; className?: string }) {
  return (
    <div className={className}>
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="mt-1 whitespace-pre-wrap break-words">{value?.trim() || "Chưa có thông tin"}</dd>
    </div>
  );
}

export function AssignedUploadWorkDetailScreen({ requestId }: { requestId: string }) {
  const [work, setWork] = useState<AssignedImagingUploadWork | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (signal: AbortSignal) => {
    setLoading(true);
    setError(null);
    try {
      const page = await listAssignedImagingUploadWork(0, 100, signal);
      if (!signal.aborted) {
        setWork(page.items.find((item) => item.requestId === requestId) ?? null);
      }
    } catch (cause) {
      if (!signal.aborted) setError(messageFromError(cause));
    } finally {
      if (!signal.aborted) setLoading(false);
    }
  }, [requestId]);

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);

  return (
    <div className="space-y-4">
      <Link
        href="/imaging/orders"
        className="inline-flex min-h-9 items-center gap-2 rounded-md border border-border bg-background px-3 text-sm font-medium text-foreground hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <ArrowLeft aria-hidden className="size-4" />
        Danh sách chỉ định
      </Link>
      <PageHeading
        title="Chi tiết chỉ định hình ảnh"
        description="Thông tin chỉ định do bác sĩ gửi và lượt chụp được phân công."
      />
      {loading ? (
        <Panel><p role="status" className="p-5">Đang tải chi tiết chỉ định…</p></Panel>
      ) : error ? (
        <Panel>
          <div className="space-y-3 p-5">
            <p role="alert" className="im-form-error">{error}</p>
            <Button variant="outline" onClick={() => {
              const controller = new AbortController();
              void load(controller.signal);
            }}>Thử lại</Button>
          </div>
        </Panel>
      ) : !work ? (
        <Panel>
          <Empty
            title="Không tìm thấy chỉ định được phân công"
            description="Chỉ định có thể đã được gỡ khỏi worklist hoặc không thuộc phòng của bạn."
          />
        </Panel>
      ) : (
        <Panel>
          <dl className="grid gap-x-8 gap-y-5 p-5 sm:grid-cols-2 lg:grid-cols-3">
            <DetailField label="Bệnh nhân" value={work.patient.fullName} />
            <DetailField label="Ngày sinh" value={work.patient.dateOfBirth} />
            <DetailField label="Giới tính" value={work.patient.gender} />
            <DetailField label="Mã chỉ định" value={work.requestId} />
            <DetailField label="Mã lượt khám" value={work.visitId} />
            <DetailField label="Phòng được phân công" value={work.roomId} />
            <DetailField label="Dịch vụ" value={work.serviceName} />
            <DetailField label="Loại ảnh" value={work.imagingType} />
            <DetailField label="Vùng chụp" value={work.bodyArea} />
            <DetailField label="Trạng thái chỉ định" value={work.status} />
            <DetailField label="Lượt chụp" value={work.attemptId ? "Đã xác nhận bắt đầu chụp" : "Chưa bắt đầu"} />
            <DetailField label="Lý do lâm sàng" value={work.clinicalReason} className="sm:col-span-2 lg:col-span-3" />
            <DetailField label="Hướng dẫn kỹ thuật" value={work.technicianInstructions} className="sm:col-span-2 lg:col-span-3" />
          </dl>
          <div className="flex flex-wrap items-center gap-2 border-t p-5">
            {work.status === "RESULT_AVAILABLE" && work.currentStudyRef ? (
              <ImagingViewerLauncher studyRef={work.currentStudyRef} purpose="TECHNICIAN_ASSIGNED" />
            ) : work.attemptId ? (
              <Button render={<Link href={`/imaging/orders/${encodeURIComponent(work.requestId)}/upload`} />}>Mở màn tải study</Button>
            ) : (
              <p className="text-sm text-muted-foreground">Bắt đầu chụp từ danh sách chỉ định được phân công.</p>
            )}
          </div>
        </Panel>
      )}
    </div>
  );
}
