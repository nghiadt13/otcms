import type { InvoiceItemStatus } from "@/lib/api/billingApi";

// Nhãn tiếng Việt dùng chung cho khoản thu và giao dịch (Q10: PayOS là "Trực tuyến", không còn VNPAY).

export const INVOICE_ITEM_STATUS_LABELS: Record<InvoiceItemStatus, string> = {
  PENDING: "Chờ thanh toán",
  PAYMENT_PENDING: "Đang thanh toán",
  PAID: "Đã thanh toán",
  VOID: "Đã hủy",
  REFUND_PENDING: "Chờ hoàn tiền",
  REFUNDED: "Đã hoàn tiền",
};

const PAYMENT_CHANNEL_LABELS: Record<string, string> = {
  COUNTER: "Tiền mặt tại quầy",
  COUNTER_QR: "QR tại quầy",
  ONLINE: "Trực tuyến (PayOS)",
};

export function paymentChannelLabel(channel: string): string {
  return PAYMENT_CHANNEL_LABELS[channel] ?? channel;
}
