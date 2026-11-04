"use client";

import { ExternalLink } from "lucide-react";
import { useState } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { getDoctorExamApi } from "../api/doctorExamApi";
import { useAsyncAction } from "../hooks/useAsyncAction";
import { viewerUrl } from "../schema/viewerUrl";
import type { ImagingResult, ParaclinicalOrder } from "../types/doctorExam";

// Xem ảnh DICOM (UC49) và ghi kết luận hình ảnh (UC50). Lưu nháp: chưa hiện cho bệnh nhân.
// BE hiện đòi thêm tệp PDF báo cáo khi hoàn tất (capabilities.findingsReportPdfRequired).

export function ImagingFindingsDialog({
  order,
  onClose,
  onChanged,
}: {
  order: ParaclinicalOrder | null;
  onClose: () => void;
  onChanged: (result: ImagingResult, completed: boolean) => void;
}) {
  return (
    <Dialog open={order !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-xl">
        {order ? (
          <FindingsBody
            key={order.id}
            order={order}
            onClose={onClose}
            onChanged={onChanged}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function FindingsBody({
  order,
  onClose,
  onChanged,
}: {
  order: ParaclinicalOrder;
  onClose: () => void;
  onChanged: (result: ImagingResult, completed: boolean) => void;
}) {
  const imaging = order.imaging;
  const api = getDoctorExamApi();
  const [content, setContent] = useState(imaging?.findingsText ?? "");
  const [report, setReport] = useState<File | null>(null);
  const [error, setError] = useState<string>();
  const { pending, run } = useAsyncAction();
  const done = imaging?.findingsCompleted ?? false;
  const url = viewerUrl(imaging?.studyRef ?? null);
  const needsPdf = api.capabilities.findingsReportPdfRequired;

  async function save(complete: boolean) {
    if (complete && !content.trim()) return setError("Nhập kết luận hình ảnh.");
    if (complete && needsPdf && !report)
      return setError("Đính kèm tệp PDF báo cáo kết luận.");
    setError(undefined);
    const result = await run(
      () =>
        complete
          ? api.completeFindings(order, content.trim(), report)
          : api.saveFindings(order, content.trim()),
      "MSG-45",
    );
    if (!result) return;
    onChanged(result, complete);
    if (complete) onClose();
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>{order.serviceName}</DialogTitle>
        <DialogDescription>
          {[order.bodyArea, order.statusNote].filter(Boolean).join(" · ") ||
            "Ca chụp của lượt khám"}
        </DialogDescription>
      </DialogHeader>
      <div className="space-y-4">
        <div className="flex flex-wrap items-center gap-3 rounded-md border bg-secondary/40 p-3">
          {url ? (
            <a
              href={url}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1.5 text-sm font-medium text-primary underline-offset-4 hover:underline"
            >
              <ExternalLink aria-hidden className="size-4" />
              Mở ảnh DICOM trong trình xem
            </a>
          ) : (
            <p className="text-sm text-muted-foreground">
              {imaging?.studyRef
                ? "Chưa cấu hình trình xem ảnh DICOM (NEXT_PUBLIC_OHIF_DEV_URL)."
                : "Chưa có ca chụp để xem."}
            </p>
          )}
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="findings-text">Kết luận hình ảnh</Label>
          <Textarea
            id="findings-text"
            rows={6}
            value={content}
            disabled={pending || done}
            aria-invalid={!!error}
            onChange={(e) => setContent(e.target.value)}
          />
        </div>
        {needsPdf && !done ? (
          <div className="space-y-1.5">
            <Label htmlFor="findings-report">Tệp PDF báo cáo kết luận</Label>
            <Input
              id="findings-report"
              type="file"
              accept="application/pdf"
              disabled={pending}
              onChange={(e) => setReport(e.target.files?.[0] ?? null)}
            />
            <p className="text-xs text-muted-foreground">
              Backend hiện bắt buộc đính kèm PDF khi hoàn tất kết luận.
            </p>
          </div>
        ) : null}
        <FieldError message={error} />
      </div>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onClose}>
          Đóng
        </Button>
        {done ? null : (
          <>
            <Button
              type="button"
              variant="outline"
              disabled={pending || !content.trim() || !imaging?.studyRef}
              onClick={() => void save(false)}
            >
              Lưu nháp
            </Button>
            <Button
              type="button"
              disabled={pending || !imaging?.studyRef}
              onClick={() => void save(true)}
            >
              {pending ? "Đang lưu..." : "Hoàn tất kết luận"}
            </Button>
          </>
        )}
      </DialogFooter>
    </>
  );
}
