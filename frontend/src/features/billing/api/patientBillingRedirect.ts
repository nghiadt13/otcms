// Kiểm tra `redirectUrl` BE trả về trước khi trình duyệt rời trang (plan phase 06 §14).
// Chỉ chấp nhận đường dẫn nội bộ `/...` hoặc https tới máy chủ VNPay đã cấu hình. Danh sách
// máy chủ khớp cấu hình BE; đổi môi trường thì sửa cả hai bên.

const VNPAY_HOSTS: readonly string[] = ["sandbox.vnpayment.vn", "pay.vnpay.vn"];

export function isSafeRedirectUrl(value: string): boolean {
  // `//host` và `/\host` là URL khác máy chủ, không phải đường dẫn nội bộ.
  if (value.startsWith("/"))
    return !value.startsWith("//") && !value.startsWith("/\\");
  try {
    const url = new URL(value);
    return url.protocol === "https:" && VNPAY_HOSTS.includes(url.hostname);
  } catch {
    return false;
  }
}
