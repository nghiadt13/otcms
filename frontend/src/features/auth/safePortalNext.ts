// Đích sau đăng nhập (`?next=`). Chỉ nhận đường dẫn nội bộ trong khu bệnh nhân (`/portal`
// hoặc `/portal/...`); mọi giá trị khác (và khi không có `next`) về trang chủ `/`, nơi header
// hiện tên người dùng, để `next` không thành cổng chuyển hướng ra ngoài (open redirect).
// Hàm thuần, không đọc window.

const SITE_HOME = "/";
const PORTAL_HOME = "/portal";
// Gốc giả chỉ để phân tích đường dẫn tương đối; kết quả phải vẫn nằm trên gốc này.
const PARSE_BASE = "http://otcms.local";
const MAX_LENGTH = 2048;

export function safePortalNext(
  next: string | null | undefined,
  fallback = SITE_HOME,
): string {
  if (typeof next !== "string" || next.length === 0) return fallback;
  if (next.length > MAX_LENGTH) return fallback;
  // Phải bắt đầu bằng đúng một "/": "//host" và "/\host" là URL ngoài trên trình duyệt.
  if (!next.startsWith("/") || next.startsWith("//")) return fallback;
  if (hasBackslashOrControlChar(next)) return fallback;

  let url: URL;
  try {
    url = new URL(next, PARSE_BASE);
  } catch {
    return fallback;
  }
  const inPortal =
    url.pathname === PORTAL_HOME || url.pathname.startsWith(`${PORTAL_HOME}/`);
  if (url.origin !== PARSE_BASE || !inPortal) return fallback;
  return `${url.pathname}${url.search}${url.hash}`;
}

// Dấu "\" và ký tự điều khiển (tab, xuống dòng) bị trình duyệt bỏ hoặc đổi thành "/",
// nên một chuỗi vô hại khi đọc có thể thành URL ngoài khi đi qua trình duyệt.
function hasBackslashOrControlChar(value: string): boolean {
  for (const char of value) {
    const code = char.charCodeAt(0);
    if (char === "\\" || code < 0x20 || code === 0x7f) return true;
  }
  return false;
}
