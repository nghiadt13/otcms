/**
 * Định dạng thời gian đăng nhập gần nhất thành dạng tương đối (ví dụ "2 ngày trước")
 * và chuỗi ngày giờ đầy đủ cho tooltip (ví dụ "19/10/2026 14:30").
 */
export function formatRelativeTime(
  isoDateString?: string | null,
  now: Date = new Date(),
): { relative: string; full: string } {
  if (!isoDateString) {
    return {
      relative: "Chưa đăng nhập",
      full: "Tài khoản chưa từng đăng nhập vào hệ thống",
    };
  }

  const date = new Date(isoDateString);
  if (Number.isNaN(date.getTime())) {
    return { relative: isoDateString, full: isoDateString };
  }

  const full = date.toLocaleString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });

  const diffMs = now.getTime() - date.getTime();
  const diffMinutes = Math.floor(diffMs / (1000 * 60));
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

  if (diffMinutes < 1) return { relative: "Vừa xong", full };
  if (diffMinutes < 60) return { relative: `${diffMinutes} phút trước`, full };
  if (diffHours < 24) return { relative: `${diffHours} giờ trước`, full };
  if (diffDays === 1) return { relative: "Hôm qua", full };
  if (diffDays < 7) return { relative: `${diffDays} ngày trước`, full };
  if (diffDays < 30) {
    const weeks = Math.floor(diffDays / 7);
    return { relative: `${weeks} tuần trước`, full };
  }
  return { relative: `${Math.floor(diffDays / 30)} tháng trước`, full };
}
