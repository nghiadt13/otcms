"use client";

import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import type { BillingInvoice } from "@/lib/api/billingApi";
import { getExamQueueEntry, type ExamQueueItem } from "@/lib/api/examQueueApi";
import { messageFromError } from "@/lib/messages/appMessages";
import { createWalkInVisitInvoice } from "../api/receptionWalkInLiveApi";
import { queuedFeedbackText } from "../schema/receptionMessages";
import { CounterPaymentForm } from "./CounterPaymentForm";
import { FormModal } from "./ReceptionUi";

/**
 * Thu phí lượt walk-in đang `PENDING_PAYMENT`: lập hóa đơn nguồn lượt khám, thu tiền tại quầy, rồi đọc
 * lại hàng chờ để lấy số thứ tự (A5, A6). Dùng cho luồng vừa tạo lượt và luồng thu phí sau ở hàng chờ.
 */
export function WalkInFeeStep({
  visitId,
  serviceId,
  onQueued,
  onClose,
}: {
  visitId: string;
  serviceId: string;
  onQueued: (entry: ExamQueueItem) => void;
  onClose: () => void;
}) {
  const [invoice, setInvoice] = useState<BillingInvoice | null>(null);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const mounted = useRef(false);
  // Chặn gọi lặp khi React chạy lại effect (StrictMode): mỗi lần thử chỉ lập hóa đơn một lần.
  const requested = useRef("");
  // Một khoá cho cả lượt thu phí, kể cả khi thử lại sau lỗi mạng, để BE nhận ra cùng một yêu cầu.
  const invoiceKey = useRef("");

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  useEffect(() => {
    const key = `${visitId}:${serviceId}:${attempt}`;
    if (requested.current === key) return;
    requested.current = key;
    invoiceKey.current ||= crypto.randomUUID();
    createWalkInVisitInvoice(visitId, serviceId, invoiceKey.current)
      .then((created) => {
        if (mounted.current) setInvoice(created);
      })
      .catch((cause) => {
        if (mounted.current) setError(messageFromError(cause));
      });
  }, [visitId, serviceId, attempt]);

  if (invoice)
    return (
      <CounterPaymentForm
        invoice={invoice}
        title="Thu phí khám"
        description="Thu phí khám tại quầy để lượt được cấp số thứ tự."
        onClose={onClose}
        onPaid={async () => {
          const entry = await getExamQueueEntry(visitId);
          toast.success(queuedFeedbackText(entry.queueNumber, entry.roomLabel));
          onQueued(entry);
        }}
      />
    );

  return (
    <FormModal
      title="Thu phí khám"
      description="Lập hóa đơn phí khám cho lượt khám trực tiếp."
      onClose={onClose}
    >
      {error ? (
        <div className="space-y-3">
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
          <div className="flex flex-wrap justify-end gap-2">
            <Button variant="outline" onClick={onClose}>
              Đóng
            </Button>
            <Button
              onClick={() => {
                setError("");
                setAttempt((value) => value + 1);
              }}
            >
              Thử lại
            </Button>
          </div>
        </div>
      ) : (
        <p role="status" className="text-sm">
          Đang lập hóa đơn…
        </p>
      )}
    </FormModal>
  );
}
