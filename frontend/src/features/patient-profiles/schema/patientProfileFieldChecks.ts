import {
  CARE_RELATIONSHIP_LABELS,
  type CareRelationship,
} from "@/lib/domain/careRelationship";

// Từng luật của một trường hồ sơ. Mỗi hàm nhận chuỗi người dùng gõ (đã có thể có khoảng trắng)
// và trả câu lỗi dưới ô, hoặc undefined khi hợp lệ. Trường tùy chọn rỗng thì hợp lệ.

export const MAX_NAME_LENGTH = 100;
export const MAX_ADDRESS_LENGTH = 255;
export const MAX_EMAIL_LENGTH = 254;

const NAME_PATTERN = /^[A-Za-zÀ-ỹ .'-]+$/;
const LETTER_PATTERN = /[A-Za-zÀ-ỹ]/;
const PHONE_PATTERN = /^0\d{9}$/;
const CITIZEN_ID_PATTERN = /^[A-Za-z0-9]{8,12}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Bỏ khoảng trắng, dấu chấm, gạch: "0912 345.678" → "0912345678". */
export function normalizePhone(raw: string): string {
  return raw.replace(/[\s.-]/g, "");
}

/** Quan hệ thuộc bảng quan hệ dùng chung (chuỗi rỗng khi chưa chọn thì không hợp lệ). */
export function isCareRelationship(value: string): value is CareRelationship {
  return Object.keys(CARE_RELATIONSHIP_LABELS).includes(value);
}

export function checkRelationship(value: string): string | undefined {
  return isCareRelationship(value) ? undefined : "Chọn quan hệ với bạn.";
}

export function checkFullName(value: string): string | undefined {
  const name = value.trim();
  if (!name) return "Nhập họ và tên.";
  if (name.length > MAX_NAME_LENGTH)
    return `Họ và tên tối đa ${MAX_NAME_LENGTH} ký tự.`;
  if (name.length < 2 || !NAME_PATTERN.test(name) || !LETTER_PATTERN.test(name))
    return "Họ và tên gồm ít nhất hai chữ, không có số.";
  return undefined;
}

export function checkSex(value: string): string | undefined {
  return value ? undefined : "Chọn giới tính.";
}

export function checkPhone(value: string): string | undefined {
  const phone = normalizePhone(value);
  if (!phone) return "Nhập số điện thoại.";
  return PHONE_PATTERN.test(phone)
    ? undefined
    : "Số điện thoại gồm 10 chữ số, bắt đầu bằng 0.";
}

export function checkAddress(value: string): string | undefined {
  const address = value.trim();
  if (!address) return "Nhập địa chỉ.";
  if (address.length > MAX_ADDRESS_LENGTH)
    return `Địa chỉ tối đa ${MAX_ADDRESS_LENGTH} ký tự.`;
  return undefined;
}

export function checkCitizenId(value: string): string | undefined {
  const citizenId = value.trim();
  if (!citizenId) return undefined;
  return CITIZEN_ID_PATTERN.test(citizenId)
    ? undefined
    : "Số định danh gồm 8 đến 12 chữ hoặc số.";
}

export function checkEmail(value: string): string | undefined {
  const email = value.trim();
  if (!email) return undefined;
  if (email.length > MAX_EMAIL_LENGTH)
    return `Email tối đa ${MAX_EMAIL_LENGTH} ký tự.`;
  return EMAIL_PATTERN.test(email)
    ? undefined
    : "Email chưa đúng định dạng, ví dụ ten@example.com.";
}
