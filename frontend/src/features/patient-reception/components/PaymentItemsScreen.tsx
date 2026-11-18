"use client";

import { useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import type { BillingInvoice } from "@/lib/api/billingApi";
import type { ReceptionPatient } from "../api/receptionLiveApi";
import { CounterPaymentForm } from "./CounterPaymentForm";
import { PatientBillingPanel } from "./PatientBillingPanel";
import { PaymentReceiptDialog } from "./PaymentReceiptDialog";
import { ReceptionPatientPicker } from "./ReceptionPatientPicker";

/**
 * Thu phí tại quầy theo khoản (UC54): tìm bệnh nhân, chọn khoản chưa trả trên hóa đơn, thu bằng
 * tiền mặt hoặc QR PayOS tại quầy, rồi mở biên lai. Số liệu luôn đọc từ BE.
 */
export function PaymentItemsScreen() {
  const [patient, setPatient] = useState<ReceptionPatient | null>(null);
  // Tăng số này để dựng lại hóa đơn của bệnh nhân sau khi thu xong.
  const [version, setVersion] = useState(0);
  const [collecting, setCollecting] = useState<BillingInvoice | null>(null);
  const [receiptId, setReceiptId] = useState<string | null>(null);

  return (
    <>
      <PageHeader
        title="Thu phí tại quầy"
        description="Tìm bệnh nhân, chọn các khoản chờ thanh toán trên một hóa đơn rồi thu tại quầy."
      />
      <div className="mb-6 max-w-xl space-y-3">
        <ReceptionPatientPicker
          onPick={(picked) => setPatient(picked)}
          onTyping={() => setPatient(null)}
        />
        {patient ? (
          <p className="text-sm">
            Bệnh nhân: <span className="font-medium">{patient.fullName}</span>
          </p>
        ) : null}
      </div>
      {patient ? (
        <PatientBillingPanel
          key={`${patient.patientProfileId}:${version}`}
          profileId={patient.patientProfileId}
          onCollect={setCollecting}
        />
      ) : null}
      {collecting ? (
        <CounterPaymentForm
          invoice={collecting}
          title="Thu tiền tại quầy"
          description="Chọn các khoản cần thu trên hóa đơn. Số tiền do hệ thống tính từ các khoản đã chọn."
          onClose={() => setCollecting(null)}
          onPaid={(paymentId) => {
            setCollecting(null);
            setReceiptId(paymentId);
            setVersion((value) => value + 1);
          }}
        />
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
