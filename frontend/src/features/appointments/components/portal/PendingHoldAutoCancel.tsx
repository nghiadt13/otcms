"use client";

import { usePathname } from "next/navigation";
import { useEffect, useRef } from "react";
import { useCurrentAccount } from "@/hooks/useCurrentAccount";
import { notifySuccess, notifyWarning } from "@/lib/messages/notify";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { clearPayOsIdempotencyKeys } from "../../api/clearPayOsIdempotencyKeys";
import { cancelPendingHold } from "../../api/patientAppointmentSource";
import {
  HOLD_ALREADY_PAID_TEXT,
  HOLD_CANCELLED_ON_LEAVE_TEXT,
} from "../../schema/paymentHoldMessages";

// Rời trang = hủy thanh toán. Khi khu bệnh nhân vừa được nạp (tải mới, tải lại, đăng nhập lại), mọi
// giữ chỗ chờ thanh toán còn sống của tài khoản này đều là của một lần xem trước đã bị bỏ: hủy ngay
// một lần. Gắn ở layout khu bệnh nhân nên chạy một lần cho mỗi lần nạp layout, không chạy lại khi
// đổi trang trong app. Ngoại lệ: /portal/payments/result là nơi PayOS đưa người dùng quay về sau khi
// trả, giữ chỗ lúc đó còn cần để xác nhận.

function isPaymentResultPath(pathname: string): boolean {
  const result = PATIENT_ROUTES.paymentResult;
  return pathname === result || pathname.startsWith(`${result}/`);
}

export function PendingHoldAutoCancel() {
  const pathname = usePathname();
  const account = useCurrentAccount();
  const handledRef = useRef(false);
  const isPatient =
    account.status === "signedIn" && account.account.accountType === "PATIENT";

  useEffect(() => {
    if (!isPatient || handledRef.current) return;
    // Hoãn một nhịp: StrictMode (dev) chạy cleanup rồi setup lại ngay, hẹn cũ bị hủy nên chỉ gọi một lần.
    const timer = setTimeout(() => {
      handledRef.current = true;
      if (isPaymentResultPath(pathname)) return;
      void cancelLeftoverHold();
    }, 0);
    return () => clearTimeout(timer);
  }, [isPatient, pathname]);

  return null;
}

async function cancelLeftoverHold(): Promise<void> {
  try {
    const result = await cancelPendingHold();
    if (result.alreadyPaid) {
      notifySuccess(HOLD_ALREADY_PAID_TEXT);
    } else if (result.cancelledCount > 0) {
      notifyWarning(HOLD_CANCELLED_ON_LEAVE_TEXT);
    } else {
      return;
    }
    clearPayOsIdempotencyKeys();
  } catch (error) {
    // Việc dọn nền, không chặn người dùng: hold còn sót sẽ hết hạn sau 15 phút.
    console.warn("Không hủy được giữ chỗ thanh toán còn sót", error);
  }
}
