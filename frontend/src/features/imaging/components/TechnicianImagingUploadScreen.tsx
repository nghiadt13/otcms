"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  getImagingOperation,
  listAssignedImagingUploadWork,
  prepareImagingUpload,
  sendImagingOperation,
  uploadImagingStudy,
  type AssignedImagingUploadWork,
  type ImagingOperationResponse,
  type ImagingUploadSubmission,
} from "../api/imagingIngestApi";
import { PageHeading } from "./PageHeading";
import { Panel } from "./Panel";

const LABELS: Record<ImagingOperationResponse["state"], string> = {
  PROCESSING: "Đang xử lý",
  ARCHIVED_NOT_SENT: "Đã lưu archive, chờ kỹ thuật viên gửi xác nhận",
  CONFIRMATION_PENDING: "Đang chờ FEAT-003 xác nhận liên kết",
  LINKED: "Đã liên kết với chỉ định",
  QUARANTINED: "Đã cách ly để đối soát",
  FAILED: "Xử lý thất bại",
  RECONCILIATION_REQUIRED: "Cần ADMIN đối soát",
};

const FINAL_STATES = new Set([
  "LINKED",
  "QUARANTINED",
  "FAILED",
  "RECONCILIATION_REQUIRED",
]);

export function TechnicianImagingUploadScreen({
  requestId,
}: {
  requestId: string;
}) {
  const [work, setWork] = useState<AssignedImagingUploadWork | null>(null);
  const [loading, setLoading] = useState(true);
  const [files, setFiles] = useState<File[]>([]);
  const [submission, setSubmission] = useState<ImagingUploadSubmission | null>(
    null,
  );
  const [operation, setOperation] = useState<ImagingOperationResponse | null>(
    null,
  );
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const sendKeyRef = useRef<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    listAssignedImagingUploadWork(0, 100, controller.signal)
      .then((page) => {
        const item = page.items.find(
          (candidate) => candidate.requestId === requestId,
        );
        setWork(item ?? null);
      })
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setError(messageFromError(cause));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [requestId]);

  const poll = useCallback(async (operationId: string, signal: AbortSignal) => {
    while (!signal.aborted) {
      const current = await getImagingOperation(operationId, signal);
      setOperation(current);
      if (
        current.state !== "PROCESSING" &&
        current.state !== "CONFIRMATION_PENDING"
      )
        return;
      await new Promise<void>((resolve) => {
        const timeout = window.setTimeout(resolve, 2000);
        signal.addEventListener(
          "abort",
          () => {
            window.clearTimeout(timeout);
            resolve();
          },
          { once: true },
        );
      });
    }
  }, []);

  useEffect(() => {
    const operationId = operation?.operationId;
    const state = operation?.state;
    if (
      !operationId ||
      !state ||
      FINAL_STATES.has(state) ||
      state === "ARCHIVED_NOT_SENT"
    )
      return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      poll(operationId, controller.signal).catch((cause: unknown) => {
        if (!controller.signal.aborted) setError(messageFromError(cause));
      });
    }, 0);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [operation?.operationId, operation?.state, poll]);

  async function upload() {
    if (!work?.attemptId) return;
    setBusy(true);
    setError(null);
    try {
      const immutableRequest = submission ?? prepareImagingUpload(files);
      setSubmission(immutableRequest);
      const created = await uploadImagingStudy(work, immutableRequest);
      setOperation(created);
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setBusy(false);
    }
  }

  async function send() {
    if (!operation || operation.state !== "ARCHIVED_NOT_SENT") return;
    setBusy(true);
    setError(null);
    try {
      setSent(true);
      sendKeyRef.current ??= crypto.randomUUID();
      const accepted = await sendImagingOperation(
        operation.operationId,
        sendKeyRef.current,
      );
      setOperation(accepted);
    } catch (cause) {
      setSent(false);
      setError(messageFromError(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <Link
        href="/imaging/orders"
        className="text-sm text-primary underline underline-offset-4"
      >
        Quay lại danh sách việc được phân công
      </Link>
      <PageHeading
        eyebrow="UPLOAD STUDY DICOM"
        title="Tải study lên archive"
        description="Chọn các file DICOM cùng study hoặc một file ZIP. Backend sẽ kiểm tra quyền, metadata và trạng thái operation trước khi lưu/gửi."
      />
      <Panel>
        <div className="space-y-5 p-5">
          {loading ? (
            <p role="status">Đang kiểm tra chỉ định được phân công…</p>
          ) : null}
          {!loading && !error && !work ? (
            <p role="alert" className="text-destructive">
              Không tìm thấy chỉ định trong worklist được phân công của tài
              khoản này.
            </p>
          ) : null}
          {work ? (
            <dl className="grid gap-2 rounded-md bg-muted p-4 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-muted-foreground">Mã chỉ định</dt>
                <dd className="font-mono">{work.requestId}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Loại ảnh</dt>
                <dd>{work.imagingType}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Trạng thái</dt>
                <dd>{work.status}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Attempt</dt>
                <dd>{work.attemptId ?? "Chưa có xác nhận bắt đầu chụp"}</dd>
              </div>
            </dl>
          ) : null}
          {!work?.attemptId && work ? (
            <p role="status" className="text-sm text-amber-700">
              Upload đang bị khóa. FEAT-003 phải xác nhận sự kiện bắt đầu chụp
              và cấp attempt hiện hành trước.
            </p>
          ) : null}
          {!operation ? (
            <div className="space-y-3">
              <label
                htmlFor="dicom-files"
                className="block text-sm font-medium"
              >
                File DICOM hoặc ZIP
              </label>
              <Input
                id="dicom-files"
                ref={inputRef}
                type="file"
                multiple
                accept=".dcm,application/dicom,.zip,application/zip"
                onChange={(event) => {
                  setFiles(Array.from(event.target.files ?? []));
                  setSubmission(null);
                  setError(null);
                }}
                disabled={busy || !work?.attemptId}
              />
              {files.length ? (
                <p className="text-sm text-muted-foreground">
                  Đã chọn {files.length} file ·{" "}
                  {(
                    files.reduce((sum, file) => sum + file.size, 0) /
                    1024 /
                    1024
                  ).toFixed(1)}{" "}
                  MB
                </p>
              ) : null}
              <Button
                onClick={upload}
                disabled={
                  busy || loading || !work?.attemptId || files.length === 0
                }
              >
                {busy ? "Đang tải…" : "Tải lên và kiểm tra"}
              </Button>
            </div>
          ) : (
            <div className="space-y-4 rounded-md border p-4">
              <p role="status" className="font-medium">
                {LABELS[operation.state]}
              </p>
              <p className="break-all text-xs text-muted-foreground">
                Operation: {operation.operationId}
              </p>
              {operation.error ? (
                <p role="alert" className="text-sm text-destructive">
                  {operation.error.message} ({operation.error.code})
                </p>
              ) : null}
              {operation.state === "ARCHIVED_NOT_SENT" ? (
                <Button onClick={send} disabled={busy || sent}>
                  {busy ? "Đang gửi…" : "Gửi xác nhận liên kết"}
                </Button>
              ) : null}
              {operation.state === "LINKED" ? (
                <p className="text-sm text-green-700">
                  Study đã được liên kết thành công.
                </p>
              ) : null}
              {operation.state === "QUARANTINED" ||
              operation.state === "RECONCILIATION_REQUIRED" ? (
                <p className="text-sm">
                  Study được giữ an toàn và chờ ADMIN đối soát; không tự ghép
                  vào hồ sơ.
                </p>
              ) : null}
            </div>
          )}
          {error ? (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          ) : null}
        </div>
      </Panel>
    </div>
  );
}
