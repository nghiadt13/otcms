"use client";

import { useRef, useState } from "react";
import {
  createCounterQrAttempt,
  recordCounterPayment,
  type BillingInvoice,
  type PayOsPaymentAttempt,
} from "@/lib/api/billingApi";
import { isApiError } from "@/lib/api/apiError";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { INVOICE_ITEM_STATUS_LABELS } from "@/lib/domain/billingLabels";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import {
  FormModal,
  InfoRow,
  money,
  SelectField,
} from "./ReceptionUi";
import { CounterQrPanel } from "./CounterQrPanel";

type Method = "CASH" | "COUNTER_QR";

/**
 * Thu tiền tại quầy trên một hóa đơn (phí khám hoặc đợt chỉ định). Lễ tân chọn các khoản PENDING
 * cần thu; số tiền do BE tính. Phương thức chỉ có tiền mặt hoặc QR PayOS tại quầy (B13).
 * `onPaid` nhận mã lần thanh toán khi BE xác nhận, để màn gọi mở biên lai.
 */
export function CounterPaymentForm({
  invoice,
  title,
  description,
  note,
  onClose,
  onPaid,
}: {
  invoice: BillingInvoice;
  title: string;
  description: string;
  /** Ghi chú thêm dưới nút xác nhận (ví dụ quy định gửi kỹ thuật viên). */
  note?: string;
  onClose: () => void;
  onPaid: (paymentId: string) => Promise<void> | void;
}) {
  const payable = invoice.items.filter((item) => item.status === "PENDING");
  const [selected, setSelected] = useState<string[]>(() =>
    payable.map((item) => item.id),
  );
  const [method, setMethod] = useState<Method>("CASH");
  const [received, setReceived] = useState(false);
  const [pending, setPending] = useState(false);
  const [qrAttempt, setQrAttempt] = useState<PayOsPaymentAttempt | null>(null);
  const [error, setError] = useState("");
  const key = useRef({ fingerprint: "", value: "" });
  const busy = useRef(false);
  const chosen = payable.filter((item) => selected.includes(item.id));
  const total = chosen.reduce((sum, item) => sum + item.lineTotal, 0);
  const valid = chosen.length > 0;

  function idempotencyKey(fingerprint: string): string {
    // Giữ một khóa cho cùng một lần thu (kể cả khi thử lại sau lỗi mạng hoặc mở lại form).
    if (key.current.fingerprint === fingerprint) return key.current.value;
    let value = crypto.randomUUID();
    try {
      const slot = `otcms:counter-payment:${fingerprint}`;
      value = sessionStorage.getItem(slot) ?? value;
      sessionStorage.setItem(slot, value);
    } catch {
      /* Khóa trong bộ nhớ vẫn ổn định khi không có sessionStorage. */
    }
    key.current = { fingerprint, value };
    return value;
  }

  async function pay() {
    if (!valid || busy.current) return;
    busy.current = true;
    setPending(true);
    setError("");
    const fingerprint = `${invoice.id}:${[...selected].sort().join(",")}:${method}`;
    try {
      if (method === "CASH") {
        const attempt = await recordCounterPayment(
          invoice.id,
          selected,
          idempotencyKey(fingerprint),
        );
        await onPaid(attempt.id);
      } else {
        setQrAttempt(
          await createCounterQrAttempt(
            invoice.id,
            selected,
            idempotencyKey(fingerprint),
          ),
        );
      }
    } catch (cause) {
      // Lỗi có mã MSG thì hiện đúng mã. Lỗi mạng: không biết giao dịch đã ghi hay chưa → MSG-52.
      setError(
        isApiError(cause)
          ? messageFromError(cause)
          : formatAppMessage("MSG-52"),
      );
    } finally {
      busy.current = false;
      setPending(false);
    }
  }

  return (
    <FormModal
      title={title}
      description={description}
      onClose={onClose}
      busy={pending}
      wide
    >
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          void pay();
        }}
      >
        <p>Hóa đơn {invoice.invoiceNumber}</p>
        <div className="space-y-2">
          {invoice.items.map((item) => (
            <label className="flex items-start gap-3" key={item.id}>
              <Checkbox
                checked={selected.includes(item.id)}
                disabled={pending || item.status !== "PENDING" || qrAttempt !== null}
                onCheckedChange={(value) =>
                  setSelected((ids) =>
                    value === true
                      ? [...new Set([...ids, item.id])]
                      : ids.filter((id) => id !== item.id),
                  )
                }
              />
              <span className="min-w-0 flex-1">
                <span className="block break-words">{item.description}</span>
                <span className="block text-sm text-muted-foreground">
                  {money(item.lineTotal)} · {INVOICE_ITEM_STATUS_LABELS[item.status]}
                </span>
              </span>
            </label>
          ))}
        </div>
        <dl>
          <InfoRow label="Đã thu">{money(invoice.paidAmount)}</InfoRow>
          <InfoRow label="Còn phải thu">
            {money(invoice.outstandingBalance)}
          </InfoRow>
        </dl>
        {payable.length > 0 && qrAttempt === null ? (
          <>
            <SelectField
              label="Phương thức thanh toán"
              value={method}
              onChange={(value) => {
                setMethod(value as Method);
                setReceived(false);
              }}
              disabled={pending}
              options={[
                { value: "CASH", label: "Tiền mặt tại quầy" },
                { value: "COUNTER_QR", label: "Mã QR PayOS tại quầy" },
              ]}
            />
            <p>
              Số tiền thu: <strong>{money(total)}</strong>
            </p>
            {method === "CASH" ? (
              <label className="flex items-start gap-3">
                <Checkbox
                  checked={received}
                  onCheckedChange={(value) => setReceived(value === true)}
                  disabled={pending || !valid}
                />
                <span>
                  Đã nhận tiền và kiểm tra đúng số tiền, bệnh nhân và các khoản.
                </span>
              </label>
            ) : null}
            {note && <p className="text-sm text-muted-foreground">{note}</p>}
            {error && (
              <p role="alert" className="text-sm text-destructive">
                {error}
              </p>
            )}
            <Button
              type="submit"
              disabled={pending || !valid || (method === "CASH" && !received)}
            >
              {pending
                ? "Đang ghi nhận..."
                : method === "CASH"
                  ? "Xác nhận thu tiền"
                  : "Tạo mã QR"}
            </Button>
          </>
        ) : null}
        {qrAttempt ? (
          <CounterQrPanel
            attempt={qrAttempt}
            onConfirmed={(paymentId) => void onPaid(paymentId)}
          />
        ) : null}
        {payable.length === 0 ? <p role="status">Đã thanh toán</p> : null}
      </form>
    </FormModal>
  );
}
