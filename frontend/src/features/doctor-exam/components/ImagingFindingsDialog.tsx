"use client";

import { useEffect, useState } from "react";
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
import { DoctorImagingViewer } from "./DoctorImagingViewer";
import type { ImagingResult, ParaclinicalOrder } from "../types/doctorExam";

// Xem ảnh DICOM (UC41) và ghi kết luận hình ảnh (UC42). Lưu nháp: chưa hiện cho bệnh nhân.
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
  const [loadError, setLoadError] = useState<string>();
  const needsExistingFindings = !!imaging?.studyRef && !imaging.findingsCompleted;
  const [loadedOrderId, setLoadedOrderId] = useState<string | null>(() =>
    needsExistingFindings ? null : order.id,
  );
  const loadingExisting = needsExistingFindings && loadedOrderId !== order.id;
  const [releaseState, setReleaseState] = useState(
    imaging?.findingsReleaseState ??
      (imaging?.findingsCompleted ? "RELEASED" : "PRIVATE"),
  );
  const { pending, run } = useAsyncAction();
  const done = releaseState === "RELEASED" || (imaging?.findingsCompleted ?? false);
  const releasePending = releaseState === "RELEASE_PENDING";
  const releaseFailed = releaseState === "RELEASE_FAILED";
  const locked = done || releasePending || releaseFailed;
  const needsPdf = api.capabilities.findingsReportPdfRequired;

  useEffect(() => {
    if (!needsExistingFindings) return;
    const controller = new AbortController();
    let active = true;
    void api
      .getFindings(order, controller.signal)
      .then((current) => {
        if (!active) return;
        setLoadError(undefined);
        if (!current) return;
        setContent(current.findingsText);
        setReleaseState(
          current.findingsReleaseState ??
            (current.findingsCompleted ? "RELEASED" : "PRIVATE"),
        );
      })
      .catch((cause: unknown) => {
        if (!active || controller.signal.aborted) return;
        setLoadError(
          cause instanceof Error
            ? cause.message
            : "Không tải được kết luận đã lưu.",
        );
      })
      .finally(() => {
        if (active) setLoadedOrderId(order.id);
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [api, imaging?.findingsCompleted, imaging?.studyRef, needsExistingFindings, order]);

  useEffect(() => {
    if (!releasePending) return;
    const controller = new AbortController();
    let active = true;
    let inFlight = false;
    const timer = window.setInterval(() => {
      if (inFlight) return;
      inFlight = true;
      void api
        .getFindings(order, controller.signal)
        .then((current) => {
          if (!active || !current) return;
          setLoadError(undefined);
          setContent(current.findingsText);
          const nextState =
            current.findingsReleaseState ??
            (current.findingsCompleted ? "RELEASED" : "PRIVATE");
          setReleaseState(nextState);
          if (nextState !== "RELEASE_PENDING") onChanged(current, false);
        })
        .catch((cause: unknown) => {
          if (active && !controller.signal.aborted) {
            setLoadError(
              cause instanceof Error
                ? cause.message
                : "Chưa kiểm tra được trạng thái phát hành.",
            );
          }
        })
        .finally(() => {
          inFlight = false;
        });
    }, 5000);
    return () => {
      active = false;
      controller.abort();
      window.clearInterval(timer);
    };
  }, [api, onChanged, order, releasePending]);

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
    setContent(result.findingsText);
    setReleaseState(
      result.findingsReleaseState ??
        (result.findingsCompleted ? "RELEASED" : "PRIVATE"),
    );
    onChanged(result, result.findingsReleaseState === "RELEASE_PENDING");
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
          {imaging?.studyRef ? (
            <DoctorImagingViewer studyRef={imaging.studyRef} />
          ) : (
            <p className="text-sm text-muted-foreground">
              Chưa có ca chụp để xem.
            </p>
          )}
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="findings-text">Kết luận hình ảnh</Label>
          <Textarea
            id="findings-text"
            rows={6}
            value={content}
            disabled={pending || loadingExisting || locked || !!loadError}
            aria-invalid={!!error}
            onChange={(e) => setContent(e.target.value)}
          />
        </div>
        {loadingExisting ? (
          <p className="text-sm text-muted-foreground" role="status">
            Đang tải kết luận đã lưu…
          </p>
        ) : null}
        {releasePending ? (
          <p className="text-sm text-muted-foreground" role="status">
            Kết luận và PDF đã được gửi, đang chờ hệ thống phát hành.
          </p>
        ) : null}
        {releaseFailed ? (
          <p className="text-sm text-destructive" role="alert">
            Hệ thống chưa phát hành được kết luận. Lượt khám vẫn chưa đủ điều kiện hoàn tất.
          </p>
        ) : null}
        {done ? (
          <p className="text-sm text-success" role="status">
            Kết luận đã được phát hành.
          </p>
        ) : null}
        {needsPdf && !locked ? (
          <div className="space-y-1.5">
            <Label htmlFor="findings-report">Tệp PDF báo cáo kết luận</Label>
            <Input
              id="findings-report"
              type="file"
              accept="application/pdf"
              disabled={pending || loadingExisting}
              onChange={(e) => setReport(e.target.files?.[0] ?? null)}
            />
            <p className="text-xs text-muted-foreground">
              Backend hiện bắt buộc đính kèm PDF khi hoàn tất kết luận.
            </p>
          </div>
        ) : null}
        <FieldError message={error ?? loadError} />
      </div>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onClose}>
          Đóng
        </Button>
        {locked || loadingExisting || loadError ? null : (
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
