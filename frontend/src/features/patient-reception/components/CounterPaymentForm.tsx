"use client";

import { useRef, useState } from "react";
import {
  recordCounterPayment,
  type BillingInvoice,
} from "@/lib/api/billingApi";
import { isApiError } from "@/lib/api/apiError";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import {
  FormModal,
  InfoRow,
  money,
  SelectField,
  TextField,
} from "./ReceptionUi";

/**
 * Form thu tiền tại quầy trên một hóa đơn (dùng chung cho đợt chỉ định và phí khám).
 * Tổng tiền và đơn giá luôn lấy từ Billing. `onPaid` chạy sau khi backend ghi nhận khoản thu.
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
  onPaid: () => Promise<void>;
}) {
  const [amount, setAmount] = useState(String(invoice.outstandingBalance));
  const [method, setMethod] = useState<"CASH" | "BANK_TRANSFER" | "OTHER">(
    "CASH",
  );
  const [received, setReceived] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const key = useRef({ fingerprint: "", value: "" });
  const busy = useRef(false);
  const valid =
    /^\d+$/.test(amount) &&
    Number.isSafeInteger(Number(amount)) &&
    Number(amount) > 0 &&
    Number(amount) <= invoice.outstandingBalance;

  async function pay() {
    if (!valid || !received || busy.current) return;
    busy.current = true;
    setPending(true);
    setError("");
    const fingerprint = `${invoice.id}:${amount}:${method}`;
    if (key.current.fingerprint !== fingerprint) {
      // Preserve one key across retries/reopening after an ambiguous transport failure.
      let value = crypto.randomUUID();
      try {
        const slot = `otcms:counter-payment:${fingerprint}`;
        value = sessionStorage.getItem(slot) ?? value;
        sessionStorage.setItem(slot, value);
      } catch {
        /* In-memory key remains stable when browser storage is unavailable. */
      }
      key.current = { fingerprint, value };
    }
    try {
      await recordCounterPayment(
        invoice.id,
        Number(amount),
        method,
        key.current.value,
      );
      await onPaid();
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
        <dl>
          {invoice.items.map((item) => (
            <InfoRow key={item.lineNumber} label={item.description}>
              {money(item.lineTotal)}
            </InfoRow>
          ))}
          <InfoRow label="Đã thu">{money(invoice.paidAmount)}</InfoRow>
          <InfoRow label="Còn phải thu">
            {money(invoice.outstandingBalance)}
          </InfoRow>
        </dl>
        {invoice.outstandingBalance > 0 ? (
          <>
            <TextField
              label="Số tiền thu (₫)"
              type="number"
              value={amount}
              onChange={(value) => {
                setAmount(value);
                setReceived(false);
              }}
              disabled={pending}
            />
            <SelectField
              label="Phương thức thanh toán"
              value={method}
              onChange={(value) => {
                setMethod(value as typeof method);
                setReceived(false);
              }}
              disabled={pending}
              options={[
                { value: "CASH", label: "Tiền mặt tại quầy" },
                { value: "BANK_TRANSFER", label: "Chuyển khoản đã đối soát" },
                { value: "OTHER", label: "Khác" },
              ]}
            />
            <label className="flex items-start gap-3">
              <Checkbox
                checked={received}
                onCheckedChange={(value) => setReceived(value === true)}
                disabled={pending || !valid}
              />
              <span>
                Đã nhận tiền và kiểm tra đúng số tiền, bệnh nhân và hóa đơn.
              </span>
            </label>
            {note && <p className="text-sm text-muted-foreground">{note}</p>}
            {error && (
              <p role="alert" className="text-sm text-destructive">
                {error}
              </p>
            )}
            <Button type="submit" disabled={pending || !valid || !received}>
              {pending ? "Đang ghi nhận..." : "Xác nhận thu tiền"}
            </Button>
          </>
        ) : (
          <p role="status">Đã thanh toán</p>
        )}
      </form>
    </FormModal>
  );
}
