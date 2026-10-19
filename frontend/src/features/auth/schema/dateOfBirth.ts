// Ngày sinh nhập dạng dd/mm/yyyy (thói quen của người Việt). Không dùng
// <input type="date"> vì định dạng hiển thị đi theo ngôn ngữ trình duyệt
// (thường là mm/dd/yyyy), dễ nhập nhầm ngày với tháng trong hồ sơ y tế.

const MIN_YEAR = 1900;

// Tự chèn "/" khi gõ: "20051990" → "20/05/1990". Chỉ giữ chữ số, tối đa 8 số.
export function formatDateOfBirthInput(raw: string): string {
  const digits = raw.replace(/\D/g, "").slice(0, 8);
  if (digits.length <= 2) return digits;
  if (digits.length <= 4) return `${digits.slice(0, 2)}/${digits.slice(2)}`;
  return `${digits.slice(0, 2)}/${digits.slice(2, 4)}/${digits.slice(4)}`;
}

// Trả về ngày thật (giờ địa phương) hoặc undefined nếu sai định dạng hay không
// tồn tại (ví dụ 31/02/2000).
export function parseDateOfBirth(text: string): Date | undefined {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(text);
  if (!match) return undefined;
  const [day, month, year] = match.slice(1).map(Number);
  const date = new Date(year, month - 1, day);
  const exists =
    date.getFullYear() === year &&
    date.getMonth() === month - 1 &&
    date.getDate() === day;
  return exists ? date : undefined;
}

export function validateDateOfBirth(
  text: string,
  today = new Date(),
): string | undefined {
  if (!text) return "Nhập ngày sinh.";
  const date = parseDateOfBirth(text);
  if (!date) return "Nhập đúng dạng ngày/tháng/năm, ví dụ 20/05/1990.";
  if (date.getFullYear() < MIN_YEAR) return "Năm sinh chưa hợp lệ.";
  if (date > today) return "Ngày sinh không được ở tương lai.";
}

// Dạng yyyy-mm-dd để gửi backend.
export function dateOfBirthToIso(text: string): string {
  const [day, month, year] = text.split("/");
  return `${year}-${month}-${day}`;
}
