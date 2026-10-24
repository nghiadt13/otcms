import type { ApiFieldError } from "@/lib/api/apiError";
import { todayIsoDate } from "@/lib/format/formatDate";
import type { Sex } from "../types/patient";

// Luật nhập liệu của hồ sơ bệnh nhân (phase-02 §3.2). Dùng chung cho form tìm, form tạo hồ sơ
// và lớp API giả. Chuẩn hóa (bỏ khoảng trắng, viết hoa) làm trước khi kiểm tra.

export type PatientFieldName =
  | "patientCode"
  | "phone"
  | "dateOfBirth"
  | "fullName"
  | "sex"
  | "address"
  | "citizenId"
  | "email";

/** Lỗi theo trường: giá trị là câu gợi ý dưới ô (không phải mã MSG). */
export type PatientFieldErrors = Partial<Record<PatientFieldName, string>>;

/** Câu gợi ý khi ô sai. Lỗi 400 của BE (MSG-17) cũng hiện bằng câu này ở đúng ô. */
export const PATIENT_FIELD_HINTS: Record<PatientFieldName, string> = {
  patientCode: "Mã bệnh nhân gồm BN và 6 chữ số, ví dụ BN-000123.",
  phone: "Số điện thoại gồm 10 chữ số, bắt đầu bằng 0.",
  dateOfBirth: "Ngày sinh dạng dd/mm/yyyy, không ở tương lai.",
  fullName: "Họ và tên gồm ít nhất hai chữ, không có số.",
  sex: "Chọn giới tính.",
  address: "Nhập địa chỉ.",
  citizenId: "Số định danh gồm 8 đến 12 chữ hoặc số.",
  email: "Email chưa đúng định dạng, ví dụ ten@example.com.",
};

const PHONE_PATTERN = /^0\d{9}$/;
const PATIENT_CODE_PATTERN = /^BN-\d{6}$/;
const CITIZEN_ID_PATTERN = /^[A-Za-z0-9]{8,12}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
// Chữ cái Latin có dấu (đến U+1EF9 bao cả tiếng Việt), khoảng trắng, dấu chấm, nháy, gạch nối.
const FULL_NAME_PATTERN = /^[A-Za-zÀ-ỹ .'-]+$/;
const LETTER_PATTERN = /[A-Za-zÀ-ỹ]/;
const DATE_OF_BIRTH_TEXT_PATTERN = /^(\d{2})\/(\d{2})\/(\d{4})$/;
const ISO_DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

export const MAX_FULL_NAME_LENGTH = 100;
export const MAX_ADDRESS_LENGTH = 255;
export const MAX_EMAIL_LENGTH = 254;

/** Bỏ khoảng trắng, dấu chấm, gạch: "0912 345.678" → "0912345678". */
export function normalizePhone(raw: string): string {
  return raw.replace(/[\s.-]/g, "");
}

export function isValidPhone(phone: string): boolean {
  return PHONE_PATTERN.test(phone);
}

/** Bỏ khoảng trắng, viết hoa, tự thêm gạch: "bn000123" → "BN-000123". */
export function normalizePatientCode(raw: string): string {
  return raw.replace(/\s/g, "").toUpperCase().replace(/^BN(?=\d{6}$)/, "BN-");
}

export function isValidPatientCode(code: string): boolean {
  return PATIENT_CODE_PATTERN.test(code);
}

/**
 * Gõ liền ngày sinh: chỉ giữ chữ số và tự chèn "/" sau ngày và tháng.
 * "12031958" → "12/03/1958"; dấu "/" gõ tay bị bỏ rồi chèn lại đúng chỗ.
 */
export function formatDateOfBirthInput(raw: string): string {
  const digits = raw.replace(/\D/g, "").slice(0, 8);
  return [digits.slice(0, 2), digits.slice(2, 4), digits.slice(4, 8)]
    .filter((part) => part.length > 0)
    .join("/");
}

/** Ngày sinh dd/mm/yyyy → YYYY-MM-DD, hoặc null nếu sai dạng hoặc không hợp lệ. */
export function parseDateOfBirth(text: string): string | null {
  const match = DATE_OF_BIRTH_TEXT_PATTERN.exec(text.trim());
  if (!match) return null;
  const [, day, month, year] = match;
  const iso = `${year}-${month}-${day}`;
  return isValidBirthDate(iso) ? iso : null;
}

/** YYYY-MM-DD → dd/mm/yyyy để điền lại vào ô (ví dụ khi trùng hồ sơ phải tìm lại). */
export function toDateOfBirthText(iso: string): string {
  const match = ISO_DATE_PATTERN.exec(iso);
  return match ? `${match[3]}/${match[2]}/${match[1]}` : "";
}

/** Ngày lịch hợp lệ, từ 1900 đến hôm nay (giờ Việt Nam). */
export function isValidBirthDate(iso: string): boolean {
  const match = ISO_DATE_PATTERN.exec(iso);
  if (!match) return false;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const date = new Date(Date.UTC(year, month - 1, day));
  const isRealDate =
    date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day;
  return isRealDate && year >= 1900 && iso <= todayIsoDate();
}

/** Gọn khoảng trắng: "  Nguyễn   Văn An " → "Nguyễn Văn An". */
export function normalizeFullName(raw: string): string {
  return raw.trim().replace(/\s+/g, " ");
}

/** Ít nhất hai chữ, mỗi chữ có ít nhất một chữ cái, không có số, tối đa 100 ký tự. */
export function isValidFullName(raw: string): boolean {
  const name = normalizeFullName(raw).normalize("NFC");
  const words = name.split(" ");
  return (
    name.length <= MAX_FULL_NAME_LENGTH &&
    words.length >= 2 &&
    words.every((word) => LETTER_PATTERN.test(word)) &&
    FULL_NAME_PATTERN.test(name)
  );
}

export function isValidAddress(raw: string): boolean {
  const address = raw.trim();
  return address.length > 0 && address.length <= MAX_ADDRESS_LENGTH;
}

/** Không bắt buộc: rỗng hợp lệ. Chuỗi rỗng sau khi bỏ khoảng trắng coi là rỗng. */
export function normalizeCitizenId(raw: string): string {
  return raw.trim().toUpperCase();
}

export function isValidCitizenId(normalized: string): boolean {
  return normalized === "" || CITIZEN_ID_PATTERN.test(normalized);
}

/** Không bắt buộc: rỗng hợp lệ. */
export function isValidEmail(raw: string): boolean {
  const email = raw.trim();
  return (
    email === "" || (email.length <= MAX_EMAIL_LENGTH && EMAIL_PATTERN.test(email))
  );
}

export function isSex(value: string): value is Sex {
  return value === "MALE" || value === "FEMALE";
}

function isPatientFieldName(field: string): field is PatientFieldName {
  return Object.hasOwn(PATIENT_FIELD_HINTS, field);
}

/** `fieldErrors` của BE → lỗi theo ô đang hiện. Trường không có trên form bị bỏ qua. */
export function toPatientFieldErrors(
  fieldErrors: ApiFieldError[],
): PatientFieldErrors {
  const found: PatientFieldErrors = {};
  for (const { field } of fieldErrors) {
    if (isPatientFieldName(field)) found[field] = PATIENT_FIELD_HINTS[field];
  }
  return found;
}
