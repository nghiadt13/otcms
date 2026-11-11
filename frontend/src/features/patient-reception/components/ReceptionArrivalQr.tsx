"use client";

import { useEffect, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { Button } from "@/components/ui/button";
import { isApiError } from "@/lib/api/apiError";
import { getCurrentClinicArrivalProof } from "../api/receptionLiveApi";
import { messageFromError } from "@/lib/messages/appMessages";
import { FormModal } from "./ReceptionUi";

/** Mã QR tại quầy để bệnh nhân quét trên cổng; mã tự làm mới theo `refreshAfterSeconds`. */
export function ReceptionArrivalQr({ onClose }: { onClose: () => void }) {
  const [proof, setProof] = useState("");
  const [expiresAt, setExpiresAt] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    let timer: number | undefined;
    const refresh = async () => {
      try {
        const result = await getCurrentClinicArrivalProof();
        if (!active) return;
        setProof(result.proof);
        setExpiresAt(result.expiresAt);
        setError("");
        timer = window.setTimeout(
          () => void refresh(),
          Math.max(5, result.refreshAfterSeconds) * 1000,
        );
      } catch (cause) {
        if (!active) return;
        setError(
          isApiError(cause) && cause.status === 503
            ? "Backend chưa nạp APP_ARRIVAL_QR_HMAC_SECRET. Hãy khởi động lại backend sau khi cấu hình backend/.env."
            : messageFromError(cause),
        );
        timer = window.setTimeout(() => void refresh(), 5000);
      }
    };
    void refresh();
    return () => {
      active = false;
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, []);

  return (
    <FormModal
      title="Mã QR check-in tại quầy"
      description="Mời bệnh nhân đăng nhập, mở lịch hẹn và quét mã này để xác nhận đã đến. Mã tự làm mới định kỳ."
      onClose={onClose}
    >
      <div className="flex flex-col items-center gap-4">
        {proof ? (
          <div className="rounded-xl bg-white p-4">
            <QRCodeSVG value={proof} size={240} level="M" />
          </div>
        ) : (
          <p role={error ? "alert" : "status"} className="text-sm">
            {error || "Đang tạo mã QR…"}
          </p>
        )}
        {expiresAt && (
          <p className="text-xs text-muted-foreground">
            Mã hiện tại hết hạn lúc{" "}
            {new Date(expiresAt).toLocaleTimeString("vi-VN", {
              timeZone: "Asia/Ho_Chi_Minh",
            })}
          </p>
        )}
        {error && proof && (
          <p role="alert" className="text-sm text-destructive">
            {error}. Hệ thống sẽ thử tạo mã mới.
          </p>
        )}
        <Button variant="outline" onClick={onClose}>
          Đóng
        </Button>
      </div>
    </FormModal>
  );
}
