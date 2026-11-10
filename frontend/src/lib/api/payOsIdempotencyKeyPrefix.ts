/**
 * Tiền tố khóa sessionStorage của khóa idempotency tạo mã PayOS (`<tiền tố><invoiceId>`). Dùng chung
 * để nơi tạo khóa (billingApi) và nơi xóa khóa khi giữ chỗ bị hủy (feature appointments) không lệch
 * nhau mà feature không phải import chéo.
 */
export const PAYOS_IDEMPOTENCY_KEY_PREFIX = "otcms:payos:idempotency:";
