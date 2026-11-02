import Link from "next/link";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatDate } from "@/lib/format/formatDate";
import { PAYMENT_METHOD_LABELS } from "@/lib/domain/clinicVisit";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import type { PatientPayment } from "../../types/patientBilling";
import { LIST_LINK_CLASS } from "./patientBillingPresentation";

/** Một dòng lịch sử: ngày · người được khám, phương thức, số tiền. Cả dòng là link tới phiếu thu. */
export function PaymentRow({ payment }: { payment: PatientPayment }) {
  return (
    <li>
      <Link
        className={LIST_LINK_CLASS}
        href={PATIENT_ROUTES.paymentDetail(payment.id)}
      >
        <span className="min-w-0">
          <span className="block font-medium">
            {payment.paidAt ? formatDate(payment.paidAt) : "—"} ·{" "}
            {payment.patientName}
          </span>
          <span className="block text-sm text-muted-foreground">
            {PAYMENT_METHOD_LABELS[payment.method]}
          </span>
        </span>
        <span className="shrink-0 font-semibold tabular-nums">
          {formatCurrencyVnd(payment.total)}
        </span>
      </Link>
    </li>
  );
}
