import type { TodayAppointmentRow } from "../types/checkIn";

// Lọc danh sách lịch hẹn hôm nay ngay trên FE (không gửi lên URL/BE). Tên không phân biệt
// dấu và hoa thường; số điện thoại bỏ khoảng trắng trước khi so.

/** Bỏ dấu tiếng Việt (dấu tổ hợp U+0300–U+036F), đổi đ→d, chữ thường: "hoa" khớp "Hoa". */
export function foldSearchText(text: string): string {
  return text
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "d");
}

/** Khớp tên hoặc số điện thoại; ô trống thì khớp mọi dòng. */
export function matchesAppointmentQuery(
  row: Pick<TodayAppointmentRow, "patientName" | "phone">,
  query: string,
): boolean {
  const text = query.trim();
  if (!text) return true;
  const folded = foldSearchText(text);
  const digits = text.replace(/\s/g, "");
  return foldSearchText(row.patientName).includes(folded) || row.phone.includes(digits);
}
