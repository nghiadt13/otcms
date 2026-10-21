import { isPasswordValid } from "@/lib/domain/passwordPolicy";

// Quy tắc tên đăng nhập là gợi ý của spec (chưa có trong R3): chữ thường, số, "." hoặc "_",
// 4–30 ký tự. Đổi quy tắc chỉ cần sửa ở đây.
export const USERNAME_PATTERN = /^[a-z0-9._]{4,30}$/;

export const USERNAME_HINT = "Chữ thường, số, dấu chấm hoặc gạch dưới; 4–30 ký tự.";
export const PASSWORD_HINT = "Ít nhất 8 ký tự, gồm chữ và số.";

export const validateUsername = (value: string): string | null =>
  USERNAME_PATTERN.test(value.trim())
    ? null
    : `Tên đăng nhập chưa đúng quy tắc. ${USERNAME_HINT}`;

export const validateInitialPassword = (value: string): string | null =>
  isPasswordValid(value) ? null : `Mật khẩu chưa đạt. ${PASSWORD_HINT}`;

// Bỏ ký tự dễ nhầm (0/O, 1/l/I) để Admin đọc hoặc đọc cho nhân viên không sai.
const LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
const DIGITS = "23456789";

function randomIndex(max: number): number {
  const buffer = new Uint32Array(1);
  // Loại phần dư để phân phối đều (rejection sampling).
  const limit = Math.floor(0x100000000 / max) * max;
  do {
    crypto.getRandomValues(buffer);
  } while (buffer[0] >= limit);
  return buffer[0] % max;
}

const pick = (source: string) => source[randomIndex(source.length)];

/** 12 ký tự, luôn có chữ và số (đạt BR-04), sinh bằng `crypto.getRandomValues`. */
export function generateInitialPassword(length = 12): string {
  const pool = LETTERS + DIGITS;
  const chars = [pick(LETTERS), pick(DIGITS)];
  while (chars.length < length) chars.push(pick(pool));
  // Xáo trộn Fisher–Yates để chữ và số bắt buộc không luôn đứng đầu.
  for (let i = chars.length - 1; i > 0; i--) {
    const j = randomIndex(i + 1);
    [chars[i], chars[j]] = [chars[j], chars[i]];
  }
  return chars.join("");
}
