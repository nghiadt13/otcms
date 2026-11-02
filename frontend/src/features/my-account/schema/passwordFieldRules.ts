import { isPasswordValid } from "@/lib/domain/passwordPolicy";

// Validator thuần cho form đổi mật khẩu. Trả về chuỗi lỗi, hoặc null nếu hợp lệ.
// BE kiểm lại mọi luật; đây chỉ là phản hồi sớm.

export const passwordMessages = {
  currentRequired: "Nhập mật khẩu hiện tại.",
  currentWrong:
    "Mật khẩu hiện tại không đúng. Kiểm tra lại, hoặc nhờ Chủ phòng khám cấp lại mật khẩu.",
  newRequired: "Nhập mật khẩu mới.",
  newInvalid:
    "Mật khẩu mới chưa đạt yêu cầu. Cần tối thiểu 8 ký tự, gồm chữ cái và chữ số.",
  confirmRequired: "Nhập lại mật khẩu mới.",
  confirmMismatch: "Mật khẩu nhập lại chưa khớp với mật khẩu mới.",
} as const;

export function validateCurrentPassword(value: string): string | null {
  return value.length === 0 ? passwordMessages.currentRequired : null;
}

export function validateNewPassword(value: string): string | null {
  if (value.length === 0) return passwordMessages.newRequired;
  return isPasswordValid(value) ? null : passwordMessages.newInvalid;
}

export function validateConfirmPassword(
  newPassword: string,
  confirm: string,
): string | null {
  if (confirm.length === 0) return passwordMessages.confirmRequired;
  return confirm === newPassword ? null : passwordMessages.confirmMismatch;
}
