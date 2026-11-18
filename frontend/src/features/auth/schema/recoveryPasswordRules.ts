import { passwordPolicy } from "@/lib/domain/passwordPolicy";

export const recoveryPasswordPolicy = passwordPolicy;

export function validateRecoveryPassword(value: string): string | undefined {
  if (!value) return "Nhập mật khẩu mới.";
  if (!recoveryPasswordPolicy.every((rule) => rule.test(value)))
    return "Mật khẩu chưa đạt yêu cầu bên dưới.";
}
