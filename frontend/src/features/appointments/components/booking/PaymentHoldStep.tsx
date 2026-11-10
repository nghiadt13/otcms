"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { isApiError } from "@/lib/api/apiError";
import { notifyError, notifySuccess, notifyWarning } from "@/lib/messages/notify";
import { clearPayOsIdempotencyKeys } from "../../api/clearPayOsIdempotencyKeys";
import {
  cancelPendingHold,
  getAppointmentStatus,
} from "../../api/patientAppointmentSource";
import { usePaymentHoldLeaveGuard } from "../../hooks/usePaymentHoldLeaveGuard";
import {
  HOLD_ALREADY_PAID_TEXT,
  HOLD_CANCELLED_BY_USER_TEXT,
  HOLD_CANCELLED_ELSEWHERE_TEXT,
  HOLD_CANCELLED_ON_LEAVE_TEXT,
} from "../../schema/paymentHoldMessages";
import type {
  BookedAppointment,
  PaymentRenderer,
  PaymentStepArgs,
} from "../../types/booking";
import { HoldLeaveDialog } from "./HoldLeaveDialog";

// Bước 5 của wizard: lịch tự đặt có phí đang giữ giờ (AWAITING_PAYMENT) chờ thanh toán. Rời trang =
// hủy thanh toán, nên component này sở hữu vòng đời của giữ chỗ: hủy khi người dùng bấm Hủy / Đặt
// lịch lại / đồng ý rời trang, hủy khi bị gỡ (đổi route bằng code), và theo dõi lịch bị tab khác hủy.

const POLL_INTERVAL_MS = 5_000;

/** Gọi `renderPayment` của nơi ghép; tách thành component để callback truyền qua prop JSX. */
function PaymentSlot({
  render,
  args,
}: {
  render: PaymentRenderer;
  args: PaymentStepArgs;
}) {
  return <>{render(args)}</>;
}

type ReleaseReason = "cancel" | "leave" | "bookAgain" | "abandoned";
type ReleaseOutcome = "released" | "paid" | "failed";

export function PaymentHoldStep({
  appointment,
  renderPayment,
  onFinish,
  onReleased,
}: {
  appointment: BookedAppointment;
  renderPayment: PaymentRenderer;
  /** Đã thanh toán xong. `announced`: thông báo đã hiện rồi (hủy gặp ca đã trả), không báo MSG-26 nữa. */
  onFinish: (result: { paid: boolean; announced: boolean }) => void;
  /** Giữ chỗ đã kết thúc không còn thanh toán được (hủy / hết hạn / tab khác hủy): reset wizard về bước 1. */
  onReleased: () => void;
}) {
  const [alive, setAlive] = useState(true);
  const [leaving, setLeaving] = useState(false);
  const aliveRef = useRef(true);
  const releasingRef = useRef(false);
  const onFinishRef = useRef(onFinish);
  const onReleasedRef = useRef(onReleased);
  useEffect(() => {
    onFinishRef.current = onFinish;
    onReleasedRef.current = onReleased;
  });

  const markEnded = useCallback(() => {
    aliveRef.current = false;
    setAlive(false);
    clearPayOsIdempotencyKeys();
  }, []);

  /** Hủy giữ chỗ ở BE rồi dọn FE. Không đụng wizard: nơi gọi quyết định bước tiếp theo. */
  const release = useCallback(
    async (reason: ReleaseReason): Promise<ReleaseOutcome> => {
      if (!aliveRef.current) return "released";
      releasingRef.current = true;
      try {
        const result = await cancelPendingHold();
        markEnded();
        if (result.alreadyPaid) {
          notifySuccess(HOLD_ALREADY_PAID_TEXT);
          if (reason !== "abandoned")
            onFinishRef.current({ paid: true, announced: true });
          return "paid";
        }
        // Chỉ báo "đã hủy" khi BE thật sự hủy được giữ chỗ (cancelledCount > 0); không có gì để hủy
        // (đã hết hạn, đã nhả) thì im lặng.
        if (result.cancelledCount > 0) {
          if (reason === "leave" || reason === "abandoned")
            notifyWarning(HOLD_CANCELLED_ON_LEAVE_TEXT);
          else if (reason === "cancel")
            notifySuccess(HOLD_CANCELLED_BY_USER_TEXT);
        }
        return "released";
      } catch (error) {
        // Đặt lại lịch: hold thường đã hết hạn/bị nhả, BE dọn nốt; không để lỗi chặn người dùng.
        if (reason === "bookAgain") {
          markEnded();
          return "released";
        }
        if (reason === "abandoned") {
          // Không còn giao diện để báo; hạn 15 phút và lần tải app kế tiếp sẽ dọn.
          console.warn("Không hủy được giữ chỗ khi rời trang", error);
          return "failed";
        }
        notifyError(error);
        return "failed";
      } finally {
        releasingRef.current = false;
      }
    },
    [markEnded],
  );

  const guard = usePaymentHoldLeaveGuard({
    active: alive,
    onAbandoned: () => void release("abandoned"),
  });

  // Tab/thiết bị khác đã hủy giữ chỗ: báo và về bước 1. Nếu đã quá hạn thì màn thanh toán đang hiện
  // khối "Hết thời gian giữ lịch" kèm nút Đặt lịch lại nên không giật người dùng về bước 1.
  useEffect(() => {
    let stopped = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const deadline = appointment.paymentDeadline
      ? Date.parse(appointment.paymentDeadline)
      : null;

    const goneElsewhere = () => {
      if (stopped || !aliveRef.current || releasingRef.current) return;
      stopped = true;
      if (deadline !== null && Date.now() >= deadline) return;
      markEnded();
      notifyWarning(HOLD_CANCELLED_ELSEWHERE_TEXT);
      onReleasedRef.current();
    };
    // Đã trả xong (webhook xác nhận lịch): kết thúc giữ chỗ phía FE để guard, poll, keepalive và hủy
    // khi gỡ đều dừng. Không reset wizard: màn thanh toán vẫn hiện "đã thanh toán" để bấm Hoàn tất.
    const confirmed = () => {
      if (stopped || !aliveRef.current || releasingRef.current) return;
      stopped = true;
      markEnded();
    };
    const tick = async () => {
      try {
        const status = await getAppointmentStatus(appointment.id);
        if (status === "CONFIRMED") confirmed();
        else if (status === "CANCELLED" || status === "PAYMENT_EXPIRED")
          goneElsewhere();
      } catch (error) {
        if (isApiError(error) && (error.status === 404 || error.code === "MSG-29"))
          goneElsewhere();
        // Lỗi tạm thời (mạng, 5xx): bỏ qua, lượt sau thử lại.
      }
      if (!stopped && aliveRef.current)
        timer = setTimeout(() => void tick(), POLL_INTERVAL_MS);
    };
    timer = setTimeout(() => void tick(), POLL_INTERVAL_MS);
    return () => {
      stopped = true;
      if (timer !== undefined) clearTimeout(timer);
    };
  }, [appointment.id, appointment.paymentDeadline, markEnded]);

  async function handleLeave(): Promise<void> {
    setLeaving(true);
    try {
      const outcome = await release("leave");
      if (outcome === "released") {
        // Reset wizard về bước 1 TRƯỚC khi đi tiếp: nếu Back/liên kết không đi đâu được (tab mới,
        // không có trang trước) thì không được để QR của lịch đã hủy còn hiện.
        onReleasedRef.current();
        guard.completeLeave();
      } else if (outcome === "paid") guard.closePrompt();
    } finally {
      setLeaving(false);
    }
  }

  return (
    <>
      <PaymentSlot
        args={{
          appointment,
          paymentDeadline: appointment.paymentDeadline ?? undefined,
          onFinish: ({ paid }) => {
            markEnded();
            onFinishRef.current({ paid, announced: false });
          },
          onCancelHold: async () => {
            if ((await release("cancel")) === "released")
              onReleasedRef.current();
          },
          onBookAgain: () => {
            void release("bookAgain").then((outcome) => {
              if (outcome === "released") onReleasedRef.current();
            });
          },
        }}
        render={renderPayment}
      />
      <HoldLeaveDialog
        onLeave={() => void handleLeave()}
        onStay={guard.dismiss}
        open={guard.prompt !== null}
        pending={leaving}
      />
    </>
  );
}
