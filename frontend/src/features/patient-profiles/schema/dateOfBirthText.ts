import { todayIsoDate } from "@/lib/format/formatDate";

// Ngày sinh gõ dạng dd/mm/yyyy (thói quen người Việt). Không dùng <input type="date">:
// định dạng hiển thị theo trình duyệt (thường mm/dd/yyyy) dễ nhầm ngày với tháng.
// Bản sao có chủ đích của luật ở features/auth: không import chéo feature.

const TEXT_PATTERN = /^(\d{2})\/(\d{2})\/(\d{4})$/;
const ISO_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;
const MIN_YEAR = 1900;

/** Tự chèn "/" khi gõ, chỉ giữ chữ số, tối đa 8 số: "12031958" → "12/03/1958". */
export function formatDateOfBirthText(raw: string): string {
  const digits = raw.replace(/\D/g, "").slice(0, 8);
  return [digits.slice(0, 2), digits.slice(2, 4), digits.slice(4, 8)]
    .filter((part) => part.length > 0)
    .join("/");
}

/** dd/mm/yyyy → yyyy-mm-dd nếu là ngày có thật (không có 31/02); còn lại undefined. */
export function dateOfBirthTextToIso(text: string): string | undefined {
  const match = TEXT_PATTERN.exec(text);
  if (!match) return undefined;
  const day = Number(match[1]);
  const month = Number(match[2]);
  const year = Number(match[3]);
  const date = new Date(Date.UTC(year, month - 1, day));
  const exists =
    date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day;
  return exists ? `${match[3]}-${match[2]}-${match[1]}` : undefined;
}

/** yyyy-mm-dd → dd/mm/yyyy để điền lại form sửa. Chuỗi không đúng dạng trả về rỗng. */
export function isoToDateOfBirthText(iso: string): string {
  const match = ISO_PATTERN.exec(iso);
  return match ? `${match[3]}/${match[2]}/${match[1]}` : "";
}

/** Câu lỗi dưới ô ngày sinh, hoặc undefined khi hợp lệ. `today` tách ra để kiểm thử. */
export function validateDateOfBirthText(
  text: string,
  today: Date = new Date(),
): string | undefined {
  const value = text.trim();
  if (!value) return "Nhập ngày sinh.";
  const iso = dateOfBirthTextToIso(value);
  if (!iso) return "Nhập đúng dạng ngày/tháng/năm, ví dụ 20/05/1990.";
  if (Number(iso.slice(0, 4)) < MIN_YEAR) return "Năm sinh chưa hợp lệ.";
  if (iso > todayIsoDate(today)) return "Ngày sinh không được ở tương lai.";
  return undefined;
}
