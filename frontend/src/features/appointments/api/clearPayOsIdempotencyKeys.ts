import { PAYOS_IDEMPOTENCY_KEY_PREFIX } from "@/lib/api/payOsIdempotencyKeyPrefix";

// Khóa idempotency tạo mã PayOS nằm trong sessionStorage theo hóa đơn (`<tiền tố><invoiceId>`, tiền
// tố dùng chung với billingApi). Khi giữ chỗ bị hủy hoặc đặt lịch mới thì xóa hết khóa cũ để lần thanh
// toán sau không dính attempt của hóa đơn đã VOID.
export function clearPayOsIdempotencyKeys(): void {
  try {
    const storage = window.sessionStorage;
    const stale: string[] = [];
    for (let index = 0; index < storage.length; index += 1) {
      const key = storage.key(index);
      if (key?.startsWith(PAYOS_IDEMPOTENCY_KEY_PREFIX)) stale.push(key);
    }
    for (const key of stale) storage.removeItem(key);
  } catch {
    // Trình duyệt chặn bộ nhớ phiên: không có khóa nào để xóa.
  }
}
