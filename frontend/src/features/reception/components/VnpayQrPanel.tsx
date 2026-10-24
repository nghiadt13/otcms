import Image from "next/image";
import { Button } from "@/components/ui/button";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { useExamFeePayment } from "../hooks/useExamFeePayment";
import { formatCountdown } from "../schema/checkInPresentation";
import type { ExamFeePayment, VnpayPaymentInfo } from "../types/checkIn";

interface VnpayQrPanelProps {
  paymentId: string;
  qr: VnpayPaymentInfo;
  amount: number;
  onSuccess: (payment: ExamFeePayment) => void;
  onFailed: (payment: ExamFeePayment | null) => void;
  onSwitchToCash: () => void;
}

// Mã QR thu tại quầy: ảnh QR, đếm ngược đến hạn, và hỏi kết quả mỗi 3 giây. Ảnh QR là dữ liệu
// thanh toán nên không ghi log và không lưu localStorage.
export function VnpayQrPanel({
  paymentId,
  qr,
  amount,
  onSuccess,
  onFailed,
  onSwitchToCash,
}: VnpayQrPanelProps) {
  const watch = useExamFeePayment(
    { id: paymentId, expiresAt: qr.expiresAt },
    { onSuccess, onFailed },
  );

  return (
    <div className="grid gap-4 sm:grid-cols-[200px_minmax(0,1fr)] sm:items-start">
      <Image
        src={qr.qrImageUrl}
        alt="Mã QR thanh toán VNPay"
        width={200}
        height={200}
        unoptimized
        className="mx-auto size-50 rounded-md border bg-background sm:mx-0"
      />
      <div className="space-y-2 text-sm">
        <p>Phí khám {formatCurrencyVnd(amount)} · Quét mã bằng ứng dụng ngân hàng</p>
        <p>
          Đang chờ VNPay xác nhận…{" "}
          <span className="font-medium tabular-nums">
            còn {formatCountdown(watch.secondsLeft ?? 0)}
          </span>
        </p>
        {watch.pollFailed ? (
          <p role="alert" className="text-destructive">
            Chưa kiểm tra được trạng thái. Đang thử lại.
          </p>
        ) : null}
        <p className="text-xs text-muted-foreground">
          Mã giao dịch: <span className="tabular-nums">{qr.transactionRef}</span>
        </p>
        <Button variant="link" className="h-auto p-0" onClick={onSwitchToCash}>
          Chuyển sang tiền mặt
        </Button>
        <p className="text-xs text-muted-foreground">
          Bệnh nhân báo đã bị trừ tiền mà màn chưa đổi? Kiểm tra lịch sử giao dịch VNPay trước khi
          thu lại.
        </p>
      </div>
    </div>
  );
}
