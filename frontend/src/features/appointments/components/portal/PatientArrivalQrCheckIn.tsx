"use client";

import { useEffect, useRef, useState } from "react";
import { isApiError } from "@/lib/api/apiError";
import { Button } from "@/components/ui/button";
import { recordPatientQrArrival } from "@/lib/api/clinicArrivalApi";
import type { PatientAppointment } from "../../types/patientAppointment";

export function PatientArrivalQrCheckIn({
  appointment,
  onRecorded,
}: {
  appointment: PatientAppointment;
  onRecorded: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [proof, setProof] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  const [cameraState, setCameraState] = useState<"closed" | "opening" | "scanning">("closed");
  const [cameraMessage, setCameraMessage] = useState("");
  const videoRef = useRef<HTMLVideoElement>(null);
  const scannerRef = useRef<{ stop: () => void } | null>(null);
  const cameraAttemptRef = useRef(0);
  const scanSubmittedRef = useRef(false);

  useEffect(() => {
    return () => {
      cameraAttemptRef.current += 1;
      scannerRef.current?.stop();
      scannerRef.current = null;
    };
  }, []);

  async function startCamera() {
    if (cameraState === "opening" || cameraState === "scanning") return;
    const attempt = ++cameraAttemptRef.current;
    setCameraState("opening");
    scanSubmittedRef.current = false;
    setCameraMessage("");
    setError("");
    try {
      if (!navigator.mediaDevices?.getUserMedia) {
        throw new Error("SECURE_CONTEXT_REQUIRED");
      }
      const { BrowserQRCodeReader } = await import("@zxing/browser");
      const reader = new BrowserQRCodeReader();
      const controls = await reader.decodeFromConstraints(
        { video: { facingMode: { ideal: "environment" } } },
        videoRef.current ?? undefined,
        (result) => {
          if (!result || scanSubmittedRef.current) return;
          scanSubmittedRef.current = true;
          const scannedProof = result.getText();
          setProof(scannedProof);
          setError("");
          stopCamera();
          void submit(scannedProof);
        },
      );
      if (attempt !== cameraAttemptRef.current) {
        controls.stop();
        return;
      }
      scannerRef.current = controls;
      setCameraState("scanning");
    } catch (cause) {
      setCameraState("closed");
      const name = cause instanceof Error ? cause.name : "";
      setCameraMessage(
        name === "NotAllowedError" || name === "PermissionDeniedError"
          ? "Bạn chưa cấp quyền camera cho trình duyệt. Hãy cho phép truy cập camera rồi thử lại, hoặc nhập mã thủ công."
          : name === "NotFoundError" || name === "NotReadableError"
            ? "Không tìm thấy camera khả dụng. Bạn có thể nhập mã QR thủ công."
            : "Không mở được camera. Hãy truy cập bằng HTTPS, cấp quyền camera cho trình duyệt rồi thử lại; bạn vẫn có thể nhập mã thủ công.",
      );
    }
  }

  function stopCamera() {
    cameraAttemptRef.current += 1;
    scannerRef.current?.stop();
    scannerRef.current = null;
    setCameraState("closed");
  }

  async function submit(value = proof) {
    const cleanProof = value.trim();
    if (!cleanProof || pending) return;
    setPending(true);
    setError("");
    try {
      const result = await recordPatientQrArrival(appointment.id, cleanProof);
      if (!result.checkedIn) {
        throw new Error("CHECK_IN_NOT_COMPLETED");
      }
      stopCamera();
      onRecorded();
      setOpen(false);
    } catch (cause) {
      setError(
        isApiError(cause) && cause.errorCode === "CHECK_IN_TIME_NOT_ALLOWED"
          ? "Chỉ có thể quét check-in từ 30 phút trước đến 15 phút sau giờ hẹn. Nếu không đúng khung giờ, hãy nhờ lễ tân hỗ trợ tiếp nhận."
          : isApiError(cause) && cause.errorCode === "ARRIVAL_PROOF_INVALID"
            ? "Mã QR không hợp lệ hoặc đã hết hạn. Hãy quét mã mới đang hiển thị tại quầy."
            : isApiError(cause) && cause.errorCode === "APPOINTMENT_NOT_CHANGEABLE"
              ? "Lịch hẹn không còn đủ điều kiện check-in. Hãy nhờ lễ tân kiểm tra giúp."
              : isApiError(cause)
                ? cause.message
                : "Không thể hoàn tất check-in. Hãy nhờ lễ tân hỗ trợ.",
      );
    } finally {
      setPending(false);
    }
  }

  if (!open)
    return (
      <Button
        className="w-full sm:w-auto"
        onClick={() => setOpen(true)}
        variant="secondary"
      >
        Quét QR tại quầy
      </Button>
    );

  return (
    <section
      aria-label="Quét QR check-in"
      className="space-y-4 rounded-lg border p-4"
    >
      <div>
        <h2 className="text-lg font-semibold">Xác nhận đã đến phòng khám</h2>
        <p className="text-sm text-muted-foreground">
          Quét mã QR đang hiển thị tại quầy lễ tân. Mã xác nhận sẽ gắn với lịch
          hẹn này.
        </p>
      </div>
      {cameraState !== "closed" && (
        <video
          ref={videoRef}
          aria-label="Camera quét mã QR"
          className="aspect-video w-full max-w-md rounded-md bg-black object-cover"
          muted
          playsInline
        />
      )}
      <div className="flex flex-wrap items-center gap-2">
        {cameraState === "scanning" ? (
          <Button onClick={stopCamera} variant="secondary">
            Tắt camera
          </Button>
        ) : (
          <Button
            disabled={cameraState === "opening"}
            onClick={() => void startCamera()}
            variant="secondary"
          >
            {cameraState === "opening" ? "Đang mở camera…" : "Mở camera để quét QR"}
          </Button>
        )}
        {cameraState === "scanning" && (
          <span className="text-sm text-muted-foreground">
            Đưa mã QR vào khung hình.
          </span>
        )}
      </div>
      {cameraMessage && (
        <p role="status" className="text-sm text-muted-foreground">
          {cameraMessage}
        </p>
      )}
      <label className="block space-y-1 text-sm font-medium">
        <span>Mã QR</span>
        <textarea
          className="min-h-24 w-full rounded-md border bg-background p-3 font-mono text-xs"
          maxLength={2048}
          onChange={(event) => setProof(event.target.value)}
          placeholder="Quét mã bằng camera hoặc dán nội dung mã"
          value={proof}
        />
      </label>
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      <div className="flex flex-wrap justify-end gap-2">
        <Button
          onClick={() => {
            stopCamera();
            setOpen(false);
          }}
          variant="outline"
        >
          Đóng
        </Button>
        <Button
          disabled={!proof.trim() || pending}
          onClick={() => void submit()}
        >
          {pending ? "Đang check-in…" : "Xác nhận mã QR"}
        </Button>
      </div>
    </section>
  );
}
