"use client";

import { Check, Copy } from "lucide-react";
import { useState } from "react";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import type { PayOsPaymentAttempt } from "../../api/payOsPaymentApi";
import { bankNameFromBin } from "./payOsBankNames";

// Chi tiết chuyển khoản hiển thị ngay trong app (thay cho trang "Chi tiết đơn hàng" của PayOS):
// ngân hàng, chủ tài khoản, số tài khoản, số tiền, nội dung. Dữ liệu do BE lưu từ phản hồi tạo link
// của PayOS; lần thanh toán cũ không có thì không vẽ gì (vẫn còn QR và link PayOS).

function CopyButton({ label, value }: { label: string; value: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <button
      aria-label={`Sao chép ${label}`}
      className="inline-flex h-8 shrink-0 items-center gap-1 rounded-md border px-2 text-sm hover:border-primary hover:text-primary"
      onClick={() => {
        void navigator.clipboard.writeText(value).then(() => {
          setCopied(true);
          window.setTimeout(() => setCopied(false), 1_500);
        });
      }}
      type="button"
    >
      {copied ? (
        <Check aria-hidden className="size-3.5" />
      ) : (
        <Copy aria-hidden className="size-3.5" />
      )}
      {copied ? "Đã chép" : "Sao chép"}
    </button>
  );
}

function Row({
  label,
  value,
  copyValue,
}: {
  label: string;
  value: string;
  copyValue?: string;
}) {
  return (
    <div className="flex items-center justify-between gap-3 py-2.5">
      <div className="min-w-0">
        <dt className="text-sm text-muted-foreground">{label}</dt>
        <dd className="break-words text-base font-semibold">{value}</dd>
      </div>
      {copyValue ? (
        <CopyButton label={label.toLowerCase()} value={copyValue} />
      ) : null}
    </div>
  );
}

export function PayOsTransferDetails({
  attempt,
  amount,
}: {
  attempt: PayOsPaymentAttempt;
  amount: number;
}) {
  const {
    transferBankBin,
    transferAccountName,
    transferAccountNumber,
    transferDescription,
  } = attempt;
  if (!transferAccountNumber) return null;

  return (
    <section
      aria-label="Thông tin chuyển khoản"
      className="rounded-xl border bg-card"
    >
      <h3 className="border-b px-4 py-3 text-base font-semibold">
        Chuyển khoản thủ công
      </h3>
      <dl className="divide-y px-4">
        <Row label="Ngân hàng" value={bankNameFromBin(transferBankBin)} />
        {transferAccountName ? (
          <Row label="Chủ tài khoản" value={transferAccountName} />
        ) : null}
        <Row
          copyValue={transferAccountNumber}
          label="Số tài khoản"
          value={transferAccountNumber}
        />
        <Row
          copyValue={String(Math.round(amount))}
          label="Số tiền"
          value={formatCurrencyVnd(amount)}
        />
        {transferDescription ? (
          <Row
            copyValue={transferDescription}
            label="Nội dung"
            value={transferDescription}
          />
        ) : null}
      </dl>
      <p className="border-t px-4 py-3 text-sm text-muted-foreground">
        Nhập chính xác số tiền và nội dung khi chuyển khoản để hệ thống tự xác
        nhận.
      </p>
    </section>
  );
}
