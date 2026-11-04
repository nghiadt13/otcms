import { QRCodeSVG } from "qrcode.react";
import type { PayOsPaymentAttempt } from "../../api/payOsPaymentApi";

// Card "Quét mã QR": mã QR, đồng hồ đếm ngược và lời nhắc. Chỉ vẽ; trạng thái do component cha giữ.

export function countdownLabel(milliseconds: number): string {
  const seconds = Math.max(0, Math.floor(milliseconds / 1_000));
  const hours = Math.floor(seconds / 3_600);
  const minutes = Math.floor((seconds % 3_600) / 60);
  const remainder = seconds % 60;
  return [hours, minutes, remainder]
    .map((value) => String(value).padStart(2, "0"))
    .join(":");
}

export function PayOsQrCard({
  attempt,
  remainingMs,
  syncError,
}: {
  attempt: PayOsPaymentAttempt;
  remainingMs: number;
  syncError: boolean;
}) {
  const live = remainingMs > 0;
  return (
    <section
      aria-label="Quét mã QR"
      className="flex flex-col gap-4 rounded-xl border bg-card p-5"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-base font-semibold">
          Quét mã QR bằng ứng dụng ngân hàng
        </h3>
        {live && attempt.expiresAt ? (
          <p
            aria-live="off"
            className="rounded-full bg-secondary px-3 py-1 text-sm tabular-nums"
            role="timer"
          >
            Hết hạn sau{" "}
            <span className="font-semibold">{countdownLabel(remainingMs)}</span>
          </p>
        ) : null}
      </div>

      <div className="flex min-h-64 items-center justify-center rounded-lg border bg-white p-3">
        {attempt.qrCode && live ? (
          <QRCodeSVG
            aria-label="Mã QR thanh toán PayOS"
            includeMargin
            size={224}
            value={attempt.qrCode}
          />
        ) : live ? (
          <p className="text-center text-sm text-muted-foreground">
            PayOS chưa trả dữ liệu QR. Hãy mở trang thanh toán PayOS.
          </p>
        ) : (
          <p className="text-center text-sm text-muted-foreground">
            Mã QR đã hết hạn.
          </p>
        )}
      </div>

      {!live && attempt.expiresAt ? (
        <p className="text-sm text-destructive" role="status">
          Đã hết thời gian. Đang xác nhận trạng thái với PayOS…
        </p>
      ) : null}
      {syncError ? (
        <p className="text-sm text-destructive" role="alert">
          Chưa lấy được trạng thái mới nhất. Hệ thống sẽ thử lại; bạn cũng có
          thể kiểm tra ngay.
        </p>
      ) : null}
      {attempt.checkoutUrl && live ? (
        <a
          className="text-sm text-muted-foreground underline underline-offset-4 hover:text-primary"
          href={attempt.checkoutUrl}
          rel="noreferrer"
          target="_blank"
        >
          Không quét được? Mở trang thanh toán PayOS
        </a>
      ) : null}
      <p className="text-sm text-muted-foreground">
        Không đóng trang này trong lúc thanh toán. Hệ thống tự kiểm tra trạng
        thái.
      </p>
    </section>
  );
}
