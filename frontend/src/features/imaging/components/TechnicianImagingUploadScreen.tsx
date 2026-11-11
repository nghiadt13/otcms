"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { ArrowLeft } from "lucide-react";
import { Button } from "@/components/ui/button";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  getImagingOperation,
  getLatestImagingOperation,
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
import { ImagingViewerLauncher } from "./ImagingViewerLauncher";
import {
  patientBirthDateLabel,
  patientDemographicSummary,
} from "./patientIdentityPresentation";

const LABELS: Record<ImagingOperationResponse["state"], string> = {
  PROCESSING: "Đang xử lý",
  ARCHIVED_NOT_SENT: "Đã lưu archive, đang gửi xác nhận liên kết",
  CONFIRMATION_PENDING: "Đang chờ FEAT-003 xác nhận liên kết",
  LINKED: "Đã liên kết với chỉ định",
  QUARANTINED: "Đã nạp archive, chưa xác minh định danh",
  FAILED: "Study chưa hợp lệ, cần tải lại",
  RECONCILIATION_REQUIRED: "Đang xử lý sự cố lưu trữ",
};

function operationErrorMessage(operation: ImagingOperationResponse) {
  switch (operation.error?.code) {
    case "IDENTITY_UNKNOWN":
      return "Study đã được nạp vào archive nhưng chưa xác minh được định danh bệnh nhân/chỉ định. Study chưa liên kết và chưa gửi bác sĩ.";
    case "IDENTITY_MISMATCH":
      return "Study đã được nạp vào archive nhưng định danh không khớp chỉ định. Study chưa liên kết và chưa gửi bác sĩ.";
    case "ARCHIVE_STUDY_ALREADY_LINKED":
      return "Study này đã được OTCMS ghi nhận với chỉ định khác hoặc metadata khác. Không tải lặp lại; hãy kiểm tra chỉ định và chọn đúng study.";
    default:
      return operation.error?.message ?? null;
  }
}

function operationLabel(operation: ImagingOperationResponse) {
  if (
    operation.state === "RECONCILIATION_REQUIRED" &&
    operation.adminResolutionRecorded
  ) {
    return "ADMIN đã ghi nhận quyết định đối soát";
  }
  return LABELS[operation.state];
}

const FINAL_STATES = new Set([
  "LINKED",
  "QUARANTINED",
  "FAILED",
  "RECONCILIATION_REQUIRED",
]);

type DroppedDirectoryEntry = {
  isFile: boolean;
  isDirectory: boolean;
  file?: (
    success: (file: File) => void,
    failure?: (error: DOMException) => void,
  ) => void;
  createReader?: () => {
    readEntries: (
      success: (entries: DroppedDirectoryEntry[]) => void,
      failure?: (error: DOMException) => void,
    ) => void;
  };
};

type DroppedItemWithEntry = {
  webkitGetAsEntry?: () => DroppedDirectoryEntry | null;
};

function readDirectoryEntryFiles(entry: DroppedDirectoryEntry): Promise<File[]> {
  if (entry.isFile && entry.file) {
    return new Promise((resolve, reject) => {
      entry.file!(
        (file) => resolve([file]),
        (error) => reject(error),
      );
    });
  }

  if (!entry.isDirectory || !entry.createReader) return Promise.resolve([]);

  const reader = entry.createReader();
  const children: DroppedDirectoryEntry[] = [];
  return new Promise((resolve, reject) => {
    const readBatch = () => {
      reader.readEntries(
        (batch) => {
          if (batch.length === 0) {
            Promise.all(children.map(readDirectoryEntryFiles))
              .then((nested) => resolve(nested.flat()))
              .catch(reject);
            return;
          }
          children.push(...batch);
          readBatch();
        },
        reject,
      );
    };
    readBatch();
  });
}

async function filesFromDrop(dataTransfer: DataTransfer): Promise<File[]> {
  const entries = Array.from(dataTransfer.items)
    .map((item) => (item as unknown as DroppedItemWithEntry).webkitGetAsEntry?.() ?? null)
    .filter((entry): entry is DroppedDirectoryEntry => entry !== null);

  if (entries.length === 0) return Array.from(dataTransfer.files);
  const nestedFiles = await Promise.all(entries.map(readDirectoryEntryFiles));
  return nestedFiles.flat();
}

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
  const [uploadProgress, setUploadProgress] = useState<{
    loaded: number;
    total: number;
  } | null>(null);
  const [uploadPhase, setUploadPhase] = useState<"sending" | "processing">(
    "sending",
  );
  const [sent, setSent] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [refreshingWork, setRefreshingWork] = useState(false);
  const autoSendAttemptRef = useRef<string | null>(null);
  const filesInputRef = useRef<HTMLInputElement>(null);
  const folderInputRef = useRef<HTMLInputElement>(null);

  const refreshAssignedWork = useCallback(async (signal?: AbortSignal) => {
    const page = await listAssignedImagingUploadWork(0, 100, signal);
    const item = page.items.find((candidate) => candidate.requestId === requestId);
    if (signal?.aborted) return;
    setWork(item ?? null);
    const latest = item?.attemptId && item.status !== "RESULT_AVAILABLE"
      ? await getLatestImagingOperation(requestId, item.attemptId, signal)
      : null;
    if (signal?.aborted) return;
    setOperation(latest);
  }, [requestId]);

  useEffect(() => {
    const controller = new AbortController();
    Promise.resolve().then(() => refreshAssignedWork(controller.signal))
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setError(messageFromError(cause));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [refreshAssignedWork]);

  async function refreshWorklist() {
    setRefreshingWork(true);
    setError(null);
    try {
      await refreshAssignedWork();
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setRefreshingWork(false);
    }
  }

  useEffect(() => {
    if (operation?.state !== "LINKED") return;
    const controller = new AbortController();
    listAssignedImagingUploadWork(0, 100, controller.signal)
      .then((page) => {
        const linkedWork = page.items.find(
          (candidate) => candidate.requestId === requestId,
        );
        if (linkedWork?.currentStudyRef) setWork(linkedWork);
      })
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setError(messageFromError(cause));
      });
    return () => controller.abort();
  }, [operation?.state, requestId]);

  useEffect(() => {
    if (operation?.state !== "ARCHIVED_NOT_SENT") return;
    if (autoSendAttemptRef.current === operation.operationId) return;
    autoSendAttemptRef.current = operation.operationId;
    void send();
  }, [operation?.operationId, operation?.state, send]);

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
    setUploadProgress({ loaded: 0, total: 0 });
    setUploadPhase("sending");
    try {
      const immutableRequest = submission ?? prepareImagingUpload(files);
      setSubmission(immutableRequest);
      const created = await uploadImagingStudy(
        work,
        immutableRequest,
        (loaded, total) => {
          setUploadProgress({ loaded, total });
          if (loaded >= total) setUploadPhase("processing");
        },
      );
      setOperation(created);
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setBusy(false);
    }
  }

  async function send() {
    const canSend = operation?.state === "ARCHIVED_NOT_SENT" || (
      operation?.state === "RECONCILIATION_REQUIRED" &&
      operation.externalResolutionConfirmed
    );
    if (!operation || !canSend) return;
    setBusy(true);
    setError(null);
    try {
      setSent(true);
      const accepted = await sendImagingOperation(
        operation.operationId,
        `auto-send-${operation.operationId}`,
      );
      setOperation(accepted);
    } catch (cause) {
      setSent(false);
      setError(messageFromError(cause));
    } finally {
      setBusy(false);
    }
  }

  const selectedBytes = files.reduce((sum, file) => sum + file.size, 0);
  const uploadComplete = operation && FINAL_STATES.has(operation.state);
  const hasPersistedStudy =
    work?.status === "RESULT_AVAILABLE" && Boolean(work.currentStudyRef);

  function addFiles(nextFiles: File[]) {
    const readableFiles = nextFiles.filter((file) => file.size > 0);
    if (nextFiles.length === 0) {
      setError(
        "Không tìm thấy file bên trong thư mục. Hãy chọn thư mục chứa các file DICOM hoặc chọn trực tiếp các file đó.",
      );
    } else if (readableFiles.length !== nextFiles.length) {
      setError(
        readableFiles.length === 0
          ? "Trình duyệt chỉ gửi thư mục rỗng (0 byte), chưa lấy được các file bên trong. Hãy dùng “Chọn thư mục” hoặc kéo thả trực tiếp thư mục vào vùng tải lên."
          : "Một số mục được chọn không có dữ liệu và đã bị bỏ qua.",
      );
    } else {
      setError(null);
    }
    setFiles(readableFiles);
    setSubmission(null);
    setOperation(null);
    setUploadProgress(null);
  }

  return (
    <div className="mx-auto max-w-[1600px] space-y-4">
      <Link
        href="/imaging/orders"
        className="inline-flex min-h-9 items-center gap-2 rounded-md border border-border bg-background px-3 text-sm font-medium text-foreground transition-colors hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <ArrowLeft aria-hidden className="size-4" />
        Danh sách chỉ định
      </Link>
      <PageHeading
        title="Tải study lên archive"
        description="Nạp thư mục DICOM hoặc file ZIP cho chỉ định đang được phân công."
      />
      {work ? (
        <div className="flex justify-end">
          <Button
            type="button"
            variant="outline"
            onClick={refreshWorklist}
            disabled={refreshingWork || busy}
          >
            {refreshingWork ? "Đang làm mới…" : "Làm mới trạng thái"}
          </Button>
        </div>
      ) : null}
      {loading ? (
        <p role="status">Đang kiểm tra chỉ định được phân công…</p>
      ) : null}
      {!loading && !error && !work ? (
        <Panel>
          <p role="alert" className="p-4 text-destructive">
            Không tìm thấy chỉ định trong worklist được phân công của tài khoản
            này.
          </p>
        </Panel>
      ) : null}
      {work ? (
        <Panel>
          <div className="grid min-h-[620px] lg:grid-cols-[300px_minmax(0,1fr)]">
            <aside
              className="border-b p-4 lg:border-b-0 lg:border-r"
              aria-label="Nạp dữ liệu DICOM"
            >
              <h2 className="mb-3 text-lg font-semibold">Tải lên</h2>
              <div
                onDragOver={(event) => {
                  event.preventDefault();
                  setDragging(true);
                }}
                onDragLeave={() => setDragging(false)}
                onDrop={(event) => {
                  event.preventDefault();
                  setDragging(false);
                  if (!busy && work.status !== "RESULT_AVAILABLE") {
                    void filesFromDrop(event.dataTransfer)
                      .then(addFiles)
                      .catch(() =>
                        setError(
                          "Không đọc được thư mục vừa thả. Hãy thử nút “Chọn thư mục” hoặc chọn các file DICOM bên trong.",
                        ),
                      );
                  }
                }}
                className={`rounded-md border-2 border-dashed p-5 text-center transition-colors ${dragging ? "border-primary bg-secondary" : "border-muted-foreground/40 bg-muted/30"}`}
              >
                <p className="mb-4 font-medium">Thả file vào đây hoặc</p>
                <div className="grid gap-2">
                  <Button
                    type="button"
                    onClick={() => {
                      const input = folderInputRef.current;
                      if (!input) return;
                      input.setAttribute("webkitdirectory", "");
                      input.setAttribute("directory", "");
                      input.click();
                    }}
                    disabled={busy || !work.attemptId || work.status === "RESULT_AVAILABLE"}
                  >
                    Chọn thư mục
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => filesInputRef.current?.click()}
                    disabled={busy || !work.attemptId || work.status === "RESULT_AVAILABLE"}
                  >
                    Chọn file
                  </Button>
                </div>
                <input
                  ref={folderInputRef}
                  className="sr-only"
                  type="file"
                  multiple
                  onClick={(event) => {
                    event.currentTarget.setAttribute("webkitdirectory", "");
                    event.currentTarget.setAttribute("directory", "");
                  }}
                  onChange={(event) =>
                    {
                      addFiles(Array.from(event.target.files ?? []));
                      event.currentTarget.value = "";
                    }
                  }
                  disabled={busy || !work.attemptId || work.status === "RESULT_AVAILABLE"}
                  aria-label="Chọn thư mục DICOM"
                />
                <input
                  ref={filesInputRef}
                  className="sr-only"
                  type="file"
                  multiple
                  accept=".dcm,application/dicom,.zip,application/zip"
                  onChange={(event) =>
                    addFiles(Array.from(event.target.files ?? []))
                  }
                  disabled={busy || !work.attemptId || work.status === "RESULT_AVAILABLE"}
                  aria-label="Chọn file DICOM hoặc ZIP"
                />
              </div>

              {files.length ? (
                <div className="mt-4 space-y-3 rounded-md border p-3">
                  <div className="flex items-center justify-between gap-2">
                    <p className="font-medium">
                      {busy ? "Đang gửi study" : "Tệp đã chọn"}
                    </p>
                    <span className="text-sm tabular-nums text-muted-foreground">
                      {files.length} file
                    </span>
                  </div>
                  {uploadProgress ? (
                    <div className="space-y-1">
                      <progress
                        aria-label="Tiến trình gửi dữ liệu DICOM"
                        className="h-2 w-full accent-primary"
                        max={100}
                        value={
                          uploadProgress.total > 0
                            ? Math.min(
                                100,
                                Math.round(
                                  (uploadProgress.loaded / uploadProgress.total) *
                                    100,
                                ),
                              )
                            : 0
                        }
                      />
                      <p role="status" className="text-xs text-muted-foreground">
                        {uploadProgress.total > 0
                          ? `${Math.min(100, Math.round((uploadProgress.loaded / uploadProgress.total) * 100))}% · ${(uploadProgress.loaded / 1024 / 1024).toFixed(1)} / ${(uploadProgress.total / 1024 / 1024).toFixed(1)} MB`
                          : "Đang chuẩn bị gửi tệp…"}
                        {busy && uploadPhase === "processing"
                          ? " · Đã gửi tệp, backend đang nạp vào archive…"
                          : null}
                      </p>
                    </div>
                  ) : null}
                  <p className="text-xs text-muted-foreground">
                    {(selectedBytes / 1024 / 1024).toFixed(1)} MB · upload qua
                    OTCMS
                  </p>
                  <ul className="max-h-40 space-y-1 overflow-auto text-xs text-muted-foreground">
                    {files.slice(0, 8).map((file, index) => (
                      <li
                        key={`${file.name}-${index}`}
                        className="truncate"
                        title={file.webkitRelativePath || file.name}
                      >
                        {file.webkitRelativePath || file.name}
                      </li>
                    ))}
                    {files.length > 8 ? (
                      <li>và {files.length - 8} file khác</li>
                    ) : null}
                  </ul>
                </div>
              ) : null}
              <Button
                className="mt-3 w-full"
                onClick={upload}
                disabled={
                  busy || loading || !work.attemptId || files.length === 0
                  || work.status === "RESULT_AVAILABLE"
                }
              >
                {busy
                  ? "Đang tải lên…"
                  : work.status === "RESULT_AVAILABLE"
                    ? "Study đã được liên kết"
                    : "Tải lên và gửi bác sĩ"}
              </Button>
              {work.status === "RESULT_AVAILABLE" ? (
                <p role="status" className="mt-3 text-sm text-muted-foreground">
                  Study đã được liên kết. Dùng nút xem ảnh ở phần thông tin chỉ định.
                </p>
              ) : null}
              {!work.attemptId ? (
                <p
                  role="status"
                  className="mt-3 text-sm text-warning-foreground"
                >
                  Upload đang bị khóa cho đến khi lượt chụp có attempt hợp lệ.
                </p>
              ) : null}
              {error ? (
                <p role="alert" className="mt-3 text-sm text-destructive">
                  {error}
                </p>
              ) : null}

              <section
                className="mt-6 border-t pt-4"
                aria-labelledby="uploaded-studies-title"
              >
                <h3 id="uploaded-studies-title" className="font-semibold">
                  Study đã tải lên
                </h3>
                {operation || hasPersistedStudy ? (
                  <ul className="mt-2 space-y-2 text-sm">
                    <li className="rounded-md bg-muted p-3">
                      <p className="font-medium">
                        {work.patient.fullName} · {work.bodyArea}
                      </p>
                      <p className="mt-1 text-xs text-muted-foreground">
                        {operation
                          ? operationLabel(operation)
                          : "Đã liên kết với chỉ định"}
                      </p>
                      {hasPersistedStudy ? (
                        <p className="mt-1 break-all font-mono text-xs text-muted-foreground">
                          Study: {work.currentStudyRef}
                        </p>
                      ) : null}
                    </li>
                  </ul>
                ) : (
                  <p className="mt-2 text-sm text-muted-foreground">
                    Chưa có study được liên kết với chỉ định này.
                  </p>
                )}
              </section>
            </aside>

            <section
              className="min-w-0"
              aria-label="Thông tin chỉ định và study"
            >
              <div className="border-b bg-muted/30 p-4">
                <h2 className="text-lg font-semibold">
                  {work.patient.fullName}
                </h2>
                <p className="text-sm text-muted-foreground">
                  {patientDemographicSummary(work.patient)}
                  {patientBirthDateLabel(work.patient.dateOfBirth)
                    ? ` · Sinh ngày ${patientBirthDateLabel(work.patient.dateOfBirth)}`
                    : ""}
                </p>
                <p className="mt-1 break-all font-mono text-xs text-muted-foreground">
                  Mã hồ sơ OTCMS: {work.patient.patientProfileId}
                </p>
              </div>
              <div className="overflow-x-auto">
                <p className="px-3 py-2 text-xs text-muted-foreground">
                  Danh sách này theo dõi chỉ định và trạng thái nạp qua OTCMS.
                  Metadata DICOM chi tiết sẽ hiển thị khi archive trả dữ liệu
                  study.
                </p>
                <table className="w-full min-w-[900px] border-collapse text-sm">
                  <thead className="bg-muted/60 text-left">
                    <tr>
                      {[
                        "Bệnh nhân / mã hồ sơ",
                        "Dịch vụ / vùng chụp",
                        "Loại ảnh",
                        "Ngày tạo operation",
                        "Trạng thái archive",
                        "Số ảnh DICOM",
                        "Mã chỉ định",
                        "Operation",
                      ].map((heading) => (
                        <th
                          key={heading}
                          className="border-b px-3 py-3 font-semibold"
                        >
                          {heading}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {operation ? (
                      <tr className="align-top">
                        <td className="border-b px-3 py-4">
                          <p className="font-semibold">
                            {work.patient.fullName}
                          </p>
                          <p className="mt-1 break-all font-mono text-xs text-muted-foreground">
                            {work.patient.patientProfileId}
                          </p>
                          <p className="text-xs text-muted-foreground">
                            {patientBirthDateLabel(work.patient.dateOfBirth) ||
                              "Chưa có ngày sinh"}
                          </p>
                        </td>
                        <td className="border-b px-3 py-4">
                          <p>{work.serviceName ?? work.imagingType}</p>
                          <p className="text-xs text-muted-foreground">
                            {work.bodyArea} · {work.clinicalReason}
                          </p>
                        </td>
                        <td className="border-b px-3 py-4">
                          {work.imagingType}
                        </td>
                        <td className="border-b px-3 py-4">
                          {new Date(operation.createdAt).toLocaleDateString(
                            "vi-VN",
                          )}
                        </td>
                        <td className="border-b px-3 py-4">
                          {operationLabel(operation)}
                        </td>
                        <td className="border-b px-3 py-4 tabular-nums">
                          {operation.instanceCount ?? "—"}
                        </td>
                        <td className="max-w-40 break-all border-b px-3 py-4 font-mono text-xs">
                          {work.requestId}
                        </td>
                        <td className="max-w-40 break-all border-b px-3 py-4 font-mono text-xs">
                          {operation.operationId}
                        </td>
                      </tr>
                    ) : hasPersistedStudy ? (
                      <tr className="align-top">
                        <td className="border-b px-3 py-4">
                          <p className="font-semibold">{work.patient.fullName}</p>
                          <p className="mt-1 break-all font-mono text-xs text-muted-foreground">
                            {work.patient.patientProfileId}
                          </p>
                          <p className="text-xs text-muted-foreground">
                            {patientBirthDateLabel(work.patient.dateOfBirth) ||
                              "Chưa có ngày sinh"}
                          </p>
                        </td>
                        <td className="border-b px-3 py-4">
                          <p>{work.serviceName ?? work.imagingType}</p>
                          <p className="text-xs text-muted-foreground">
                            {work.bodyArea} · {work.clinicalReason}
                          </p>
                        </td>
                        <td className="border-b px-3 py-4">{work.imagingType}</td>
                        <td className="border-b px-3 py-4">—</td>
                        <td className="border-b px-3 py-4">Đã liên kết</td>
                        <td className="border-b px-3 py-4">—</td>
                        <td className="max-w-40 break-all border-b px-3 py-4 font-mono text-xs">
                          {work.requestId}
                        </td>
                        <td className="max-w-40 break-all border-b px-3 py-4 font-mono text-xs">
                          {work.currentStudyRef}
                        </td>
                      </tr>
                    ) : (
                      <tr>
                        <td
                          colSpan={8}
                          className="px-4 py-8 text-center text-muted-foreground"
                        >
                          Chọn thư mục hoặc file để nạp study cho chỉ định này.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
              <div className="space-y-4 p-4">
                <section className="rounded-md border p-4">
                  <h3 className="font-semibold">Phiếu chỉ định</h3>
                  <dl className="mt-3 grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
                    <div>
                      <dt className="text-muted-foreground">Dịch vụ</dt>
                      <dd>{work.serviceName ?? work.imagingType}</dd>
                    </div>
                    <div>
                      <dt className="text-muted-foreground">Vùng cần chụp</dt>
                      <dd>{work.bodyArea}</dd>
                    </div>
                    <div>
                      <dt className="text-muted-foreground">Loại ảnh</dt>
                      <dd>{work.imagingType}</dd>
                    </div>
                    <div>
                      <dt className="text-muted-foreground">Lý do lâm sàng</dt>
                      <dd>{work.clinicalReason}</dd>
                    </div>
                    <div>
                      <dt className="text-muted-foreground">Trạng thái</dt>
                      <dd>{work.status}</dd>
                    </div>
                    <div>
                      <dt className="text-muted-foreground">
                        Hướng dẫn kỹ thuật
                      </dt>
                      <dd>{work.technicianInstructions || "—"}</dd>
                    </div>
                  </dl>
                  {hasPersistedStudy ? (
                    <div className="mt-4 border-t pt-4">
                      <p className="mb-2 text-sm text-muted-foreground">
                        Study này đã được backend liên kết với chỉ định đang phân công.
                      </p>
                      <ImagingViewerLauncher
                        studyRef={work.currentStudyRef!}
                        purpose="TECHNICIAN_ASSIGNED"
                      />
                    </div>
                  ) : null}
                </section>
                {operation ? (
                  <section className="rounded-md border p-4">
                    <p role="status" className="font-medium">
                      {operationLabel(operation)}
                    </p>
                    <p className="mt-2 break-all font-mono text-xs text-muted-foreground">
                      Operation: {operation.operationId}
                    </p>
                    {operation.error ? (
                      <p role="alert" className="mt-2 text-sm text-destructive">
                        {operationErrorMessage(operation)} ({operation.error.code})
                      </p>
                    ) : null}
                    {operation.state === "ARCHIVED_NOT_SENT" ||
                    (operation.state === "RECONCILIATION_REQUIRED" &&
                      operation.externalResolutionConfirmed) ? (
                      <Button
                        className="mt-3"
                        onClick={send}
                        disabled={busy || sent}
                      >
                        {busy
                          ? "Đang gửi…"
                          : operation.state === "RECONCILIATION_REQUIRED"
                            ? "Tiếp tục gửi xác nhận liên kết"
                            : "Gửi xác nhận liên kết"}
                      </Button>
                    ) : null}
                    {operation.state === "LINKED" ? (
                      <div className="mt-2 space-y-3">
                        <p className="text-sm text-success-foreground">
                          Study đã được liên kết với chỉ định.
                        </p>
                        {!work.currentStudyRef ? (
                          <p role="status" className="text-sm text-muted-foreground">
                            Đang tải study đã liên kết để cấp quyền xem…
                          </p>
                        ) : null}
                      </div>
                    ) : null}
                    {operation.state === "QUARANTINED" ? (
                      <p className="mt-2 text-sm">
                        File đã được lưu trong Orthanc để kỹ thuật viên kiểm tra. Vì định danh chưa được xác minh, OTCMS không liên kết study này với hồ sơ bệnh nhân, không gửi bác sĩ và không cấp quyền xem qua OTCMS. Nếu đây là file chọn nhầm, hãy tải study đúng.
                      </p>
                    ) : null}
                    {operation.state === "RECONCILIATION_REQUIRED" ? (
                      <p className="mt-2 text-sm">
                        {operation.adminResolutionRecorded
                          ? operation.externalResolutionConfirmed
                            ? "ADMIN đã xác nhận xử lý ngoài hệ thống. Hãy tiếp tục gửi xác nhận liên kết; backend chỉ cho phép nếu study đã lưu khớp hoàn toàn với operation và chỉ định. Study chỉ được xem sau khi FEAT-003 xác nhận liên kết."
                            : "ADMIN đã ghi nhận quyết định đối soát. Quyết định này không tự liên kết study với chỉ định; chỉ FEAT-003 xác nhận liên kết mới cho phép tiếp tục luồng xem ảnh."
                          : "Có sự cố không xác định được kết quả lưu trữ. Study đang được giữ an toàn; không tải lặp lại operation này."}
                      </p>
                    ) : null}
                    {uploadComplete ? (
                      <Button
                        className="mt-3"
                        variant="outline"
                        onClick={() => {
                          setOperation(null);
                          setFiles([]);
                          setSubmission(null);
                          if (filesInputRef.current)
                            filesInputRef.current.value = "";
                          if (folderInputRef.current)
                            folderInputRef.current.value = "";
                        }}
                      >
                        Tải study khác
                      </Button>
                    ) : null}
                  </section>
                ) : null}
              </div>
            </section>
          </div>
        </Panel>
      ) : null}
    </div>
  );
}
