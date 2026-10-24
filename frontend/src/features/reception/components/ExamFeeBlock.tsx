import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import {
  EXAM_FEE_STATUS_LABELS,
  EXAM_FEE_STATUS_TONE,
  type PaymentMethod,
} from "@/lib/domain/clinicVisit";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import type { ExamFeeView } from "../schema/checkInDraft";
import type { ExamFeePayment } from "../types/checkIn";
import { CashCollectButton } from "./CashCollectButton";
import { VnpayQrPanel } from "./VnpayQrPanel";

interface ExamFeeBlockProps {
  view: ExamFeeView;
  payment: ExamFeePayment | null;
  method: PaymentMethod;
  collecting: boolean;
  onMethodChange: (method: PaymentMethod) => void;
  onCollectCash: () => void;
  onCreateQr: () => void;
  onSwitchToCash: () => void;
  onQrSuccess: (payment: ExamFeePayment) => void;
  onQrFailed: (payment: ExamFeePayment | null) => void;
}

// Phí khám của lượt tiếp nhận: trạng thái bằng chữ, và cách thu khi còn phải thu.
export function ExamFeeBlock(props: ExamFeeBlockProps) {
  const { view } = props;
  if (view.kind === "NEEDS_SERVICE") {
    return (
      <section aria-labelledby="checkin-fee-title" className="space-y-2 border-t pt-5">
        <h3 id="checkin-fee-title" className="text-sm font-medium">
          Phí khám
        </h3>
        <p className="text-sm text-muted-foreground">Chọn dịch vụ khám để xem phí.</p>
      </section>
    );
  }

  return (
    <section aria-labelledby="checkin-fee-title" className="space-y-3 border-t pt-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 id="checkin-fee-title" className="text-sm font-medium">
          Phí khám
        </h3>
        <div className="flex items-center gap-2">
          {view.status === "NOT_REQUIRED" ? null : (
            <span className="text-sm font-medium tabular-nums">{formatCurrencyVnd(view.amount)}</span>
          )}
          <StatusBadge tone={EXAM_FEE_STATUS_TONE[view.status]}>
            {EXAM_FEE_STATUS_LABELS[view.status]}
          </StatusBadge>
        </div>
      </div>
      <FeeBody {...props} />
    </section>
  );
}

function FeeBody({
  view,
  payment,
  method,
  collecting,
  onMethodChange,
  onCollectCash,
  onCreateQr,
  onSwitchToCash,
  onQrSuccess,
  onQrFailed,
}: ExamFeeBlockProps) {
  if (view.kind === "NEEDS_SERVICE") return null;
  if (view.status === "PAID") {
    // Chỉ nêu điều đã biết chắc: trả online, hoặc có phiếu thu vừa lập trên màn.
    const detail = view.paidOnline
      ? "Đã trả trực tuyến trước khi đến."
      : payment?.receiptNo
        ? `Phiếu thu ${payment.receiptNo}.`
        : null;
    return detail ? <p className="text-sm text-muted-foreground">{detail}</p> : null;
  }
  if (view.status === "NOT_REQUIRED") {
    return (
      <p className="text-sm text-muted-foreground">
        Đang trong 7 ngày của lượt khám nên không thu thêm.
      </p>
    );
  }

  if (payment?.status === "PENDING" && payment.vnpay) {
    return (
      <VnpayQrPanel
        paymentId={payment.id}
        qr={payment.vnpay}
        amount={view.amount}
        onSuccess={onQrSuccess}
        onFailed={onQrFailed}
        onSwitchToCash={onSwitchToCash}
      />
    );
  }

  const failed = payment?.status === "FAILED";
  return (
    <div className="space-y-3">
      <ToggleGroup
        aria-label="Cách thu phí"
        variant="outline"
        size="sm"
        spacing={0}
        value={[method]}
        disabled={collecting}
        onValueChange={(values) => {
          // Bấm lại mục đang chọn trả mảng rỗng: giữ nguyên lựa chọn hiện tại.
          const next = values[0];
          if (next === "CASH" || next === "VNPAY") onMethodChange(next);
        }}
      >
        <ToggleGroupItem value="CASH">Tiền mặt</ToggleGroupItem>
        <ToggleGroupItem value="VNPAY">VNPay QR</ToggleGroupItem>
      </ToggleGroup>

      {failed ? (
        <p role="alert" className="text-sm text-destructive">
          Chưa nhận được thanh toán. Thu bằng tiền mặt hoặc tạo mã QR mới.
        </p>
      ) : null}

      {method === "CASH" ? (
        <CashCollectButton amount={view.amount} pending={collecting} onCollect={onCollectCash} />
      ) : (
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="outline" disabled={collecting} onClick={onCreateQr}>
            {collecting ? "Đang tạo mã..." : failed ? "Tạo mã QR mới" : "Tạo mã QR"}
          </Button>
          {failed ? (
            <Button variant="link" className="h-auto p-0" onClick={onSwitchToCash}>
              Chuyển sang tiền mặt
            </Button>
          ) : null}
        </div>
      )}
    </div>
  );
}
