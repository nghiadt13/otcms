import { ReceiptView } from "@/features/billing/components/portal/ReceiptView";

// Phiếu thu của một thanh toán đã hoàn tất. Mã không thuộc tài khoản hoặc chưa hoàn tất: không tìm thấy.
export default async function PaymentReceiptPage({
  params,
}: {
  params: Promise<{ paymentId: string }>;
}) {
  const { paymentId } = await params;
  return <ReceiptView paymentId={paymentId} />;
}
