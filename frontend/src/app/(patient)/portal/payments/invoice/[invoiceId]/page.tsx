import { notFound } from "next/navigation";
import { AppointmentPayOsPayment } from "@/features/billing/components/portal/AppointmentPayOsPayment";

export default async function InvoicePaymentPage({
  params,
}: {
  params: Promise<{ invoiceId: string }>;
}) {
  const { invoiceId } = await params;
  if (!invoiceId) notFound();
  return <AppointmentPayOsPayment invoiceId={invoiceId} />;
}
