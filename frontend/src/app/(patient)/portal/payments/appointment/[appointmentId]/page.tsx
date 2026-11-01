import { notFound } from "next/navigation";
import { AppointmentPayOsPayment } from "@/features/billing/components/portal/AppointmentPayOsPayment";

export default async function AppointmentPaymentPage({
  params,
}: {
  params: Promise<{ appointmentId: string }>;
}) {
  const { appointmentId } = await params;
  if (!appointmentId) notFound();
  return <AppointmentPayOsPayment appointmentId={appointmentId} />;
}
