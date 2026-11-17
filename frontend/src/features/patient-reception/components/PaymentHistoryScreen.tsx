"use client";

import { useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { usePaymentRecordList } from "@/hooks/usePaymentRecordList";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatDateTime } from "@/lib/format/formatDate";
import { paymentChannelLabel } from "@/lib/domain/billingLabels";
import { formatAppMessage } from "@/lib/messages/appMessages";
import type { ReceptionPatient } from "../api/receptionLiveApi";
import { PaymentReceiptDialog } from "./PaymentReceiptDialog";
import { ReceptionPatientPicker } from "./ReceptionPatientPicker";
import { DataLoading, TextField } from "./ReceptionUi";

/** Lịch sử thu tiền (UC56) cho lễ tân: khoảng ngày, có thể lọc theo một bệnh nhân, mở biên lai. */
export function PaymentHistoryScreen() {
  const [patient, setPatient] = useState<ReceptionPatient | null>(null);
  return (
    <>
      <PageHeader
        title="Lịch sử thanh toán"
        description="Mỗi dòng là một lần thu đã xác nhận. Chọn dòng để xem và in biên lai."
      />
      <div className="mb-4 max-w-xl space-y-3">
        <ReceptionPatientPicker
          onPick={(picked) => setPatient(picked)}
          onTyping={() => setPatient(null)}
        />
        {patient ? (
          <p className="text-sm">
            Lọc theo bệnh nhân: <span className="font-medium">{patient.fullName}</span>
          </p>
        ) : null}
      </div>
      <PaymentHistoryTable
        key={patient?.patientProfileId ?? "all"}
        patientId={patient?.patientProfileId}
      />
    </>
  );
}

function PaymentHistoryTable({ patientId }: { patientId?: string }) {
  const history = usePaymentRecordList(
    patientId ? { patientId } : undefined,
  );
  const [receiptId, setReceiptId] = useState<string | null>(null);
  const rangeInvalid = history.rangeError !== null;

  return (
    <>
      <div className="mb-4 grid gap-3 sm:grid-cols-2">
        <TextField
          label="Từ ngày"
          type="date"
          value={history.range.from}
          onChange={(value) =>
            history.setDateRange({ from: value, to: history.range.to })
          }
        />
        <TextField
          label="Đến ngày"
          type="date"
          value={history.range.to}
          onChange={(value) =>
            history.setDateRange({ from: history.range.from, to: value })
          }
        />
      </div>
      {rangeInvalid ? (
        <p role="alert" className="mb-4 text-destructive">
          {formatAppMessage("MSG-54")}
        </p>
      ) : null}
      {history.error ? (
        <div className="space-y-3">
          <p role="alert" className="text-destructive">
            {history.error}
          </p>
          <Button variant="outline" onClick={history.reload}>
            Thử lại
          </Button>
        </div>
      ) : history.loading ? (
        <DataLoading />
      ) : history.items.length === 0 ? (
        <p className="text-muted-foreground">
          Không có thanh toán đã xác nhận trong khoảng đã chọn.
        </p>
      ) : (
        <ul className="divide-y rounded-lg border bg-card">
          {history.items.map((payment) => (
            <li
              className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
              key={payment.id}
            >
              <span className="min-w-0 space-y-1">
                <span className="block font-medium tabular-nums">
                  {payment.invoiceNumber}
                </span>
                <span className="block text-sm text-muted-foreground">
                  {formatDateTime(payment.confirmedAt)} ·{" "}
                  {paymentChannelLabel(payment.channel)}
                </span>
              </span>
              <span className="flex items-center gap-3">
                <span className="tabular-nums">
                  {formatCurrencyVnd(payment.amount)}
                </span>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => setReceiptId(payment.id)}
                >
                  Biên lai
                </Button>
              </span>
            </li>
          ))}
        </ul>
      )}
      {history.items.length > 0 && history.totalPages > 1 ? (
        <div className="mt-4 flex items-center justify-between gap-3">
          <Button
            variant="outline"
            disabled={history.page === 0}
            onClick={() => history.setPage(history.page - 1)}
          >
            Trang trước
          </Button>
          <span className="text-sm text-muted-foreground">
            Trang {history.page + 1}/{history.totalPages}
          </span>
          <Button
            variant="outline"
            disabled={history.page + 1 >= history.totalPages}
            onClick={() => history.setPage(history.page + 1)}
          >
            Trang sau
          </Button>
        </div>
      ) : null}
      {receiptId ? (
        <PaymentReceiptDialog
          paymentId={receiptId}
          onClose={() => setReceiptId(null)}
        />
      ) : null}
    </>
  );
}
