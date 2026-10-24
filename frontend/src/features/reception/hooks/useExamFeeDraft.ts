import { useState } from "react";
import { toast } from "sonner";
import { isApiError } from "@/lib/api/apiError";
import type { PaymentMethod } from "@/lib/domain/clinicVisit";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { notifyError, notifySuccess, notifyWarning } from "@/lib/messages/notify";
import { collectExamFee } from "../api/checkInApi";
import { buildExamFeeRequest, examFeeView } from "../schema/checkInDraft";
import type { CheckInOption, ExamFeePayment } from "../types/checkIn";

interface ExamFeeDraftInput {
  patientId: string | null;
  option: CheckInOption | null;
  serviceId: string | null;
  /** Gọi khi BE báo khoản không còn chờ thanh toán (MSG-49): màn tải lại bối cảnh. */
  onStale: () => void;
}

/**
 * Thu phí khám của lượt tiếp nhận: cách thu, khoản đã hoặc đang thu, và các thao tác thu.
 * Khoản đang chờ hoặc đã thu khóa việc đổi lựa chọn; chỉ khoản thất bại mới mở lại.
 */
export function useExamFeeDraft({ patientId, option, serviceId, onStale }: ExamFeeDraftInput) {
  const [method, setMethod] = useState<PaymentMethod>("CASH");
  const [payment, setPayment] = useState<ExamFeePayment | null>(null);
  const [collecting, setCollecting] = useState(false);

  const view = examFeeView(option, serviceId, payment);
  const locked = payment !== null && payment.status !== "FAILED";

  function notifyPaid(paid: ExamFeePayment) {
    // MSG-48 báo khoản vừa ghi nhận: tham số {balance} trong bảng nhận số tiền thu lần này.
    notifySuccess("MSG-48", { balance: formatCurrencyVnd(paid.amount) });
  }

  async function collect(next: PaymentMethod) {
    if (patientId === null || !option || view?.kind !== "FEE") return;
    setCollecting(true);
    try {
      const created = await collectExamFee(buildExamFeeRequest(patientId, option, serviceId, next));
      setPayment(created);
      if (created.status === "SUCCESS") notifyPaid(created);
      else notifyWarning("MSG-52");
    } catch (failure) {
      notifyError(failure);
      if (isApiError(failure) && failure.code === "MSG-49") onStale();
    } finally {
      setCollecting(false);
    }
  }

  function chooseMethod(next: PaymentMethod) {
    if (locked || next === method) return;
    setMethod(next);
    setPayment(null);
  }

  /** Chuyển sang tiền mặt cũng là lúc dừng theo dõi VNPay (khối QR bị gỡ khỏi màn). */
  function switchToCash() {
    setMethod("CASH");
    setPayment(null);
  }

  /** Đổi lựa chọn hoặc dịch vụ (khi chưa khóa): khoản thất bại còn lại được bỏ. */
  function resetPayment() {
    setPayment(null);
  }

  function handleQrSuccess(paid: ExamFeePayment) {
    setPayment(paid);
    notifyPaid(paid);
  }

  /** Thất bại do VNPay hoặc hết hạn phía FE: giữ khoản để hiện lý do, mở lại việc đổi cách thu. */
  function handleQrFailed(failed: ExamFeePayment | null) {
    const base = failed ?? payment;
    if (base) setPayment({ ...base, status: "FAILED" });
    toast.error(formatAppMessage("MSG-53"));
  }

  return {
    view,
    method,
    payment,
    collecting,
    locked,
    chooseMethod,
    switchToCash,
    resetPayment,
    collectCash: () => collect("CASH"),
    createQr: () => collect("VNPAY"),
    handleQrSuccess,
    handleQrFailed,
  };
}
