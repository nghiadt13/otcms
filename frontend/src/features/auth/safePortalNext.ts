// Đích sau đăng nhập (`?next=`). Chỉ nhận đường dẫn nội bộ trong khu bệnh nhân (`/portal`
// hoặc `/portal/...`); mọi giá trị khác về `/portal`, để `next` không thành cổng chuyển hướng
// ra ngoài (open redirect). Hàm thuần, không đọc window.

const PORTAL_HOME = "/portal";
// Gốc giả chỉ để phân tích đường dẫn tương đối; kết quả phải vẫn nằm trên gốc này.
const PARSE_BASE = "http://otcms.local";
const MAX_LENGTH = 2048;

export function safePortalNext(next: string | null | undefined): string {
  if (typeof next !== "string" || next.length === 0) return PORTAL_HOME;
  if (next.length > MAX_LENGTH) return PORTAL_HOME;
  // Phải bắt đầu bằng đúng một "/": "//host" và "/\host" là URL ngoài trên trình duyệt.
  if (!next.startsWith("/") || next.startsWith("//")) return PORTAL_HOME;
  if (hasBackslashOrControlChar(next)) return PORTAL_HOME;

  let url: URL;
  try {
    url = new URL(next, PARSE_BASE);
  } catch {
    return PORTAL_HOME;
  }
  const inPortal =
    url.pathname === PORTAL_HOME || url.pathname.startsWith(`${PORTAL_HOME}/`);
  if (url.origin !== PARSE_BASE || !inPortal) return PORTAL_HOME;
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
