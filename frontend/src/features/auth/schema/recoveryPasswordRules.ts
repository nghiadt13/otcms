// Match the existing backend PasswordPolicy/ResetPasswordRequest contract.
// Registration and account-management policies are outside this integration.
export const recoveryPasswordPolicy = [
  {
    id: "length",
    label: "Từ 12 đến 64 ký tự",
    test: (value: string) => value.length >= 12 && value.length <= 64,
  },
  {
    id: "bytes",
    label: "Tối đa 72 byte UTF-8 (ký tự có dấu có thể chiếm nhiều byte)",
    test: (value: string) => new TextEncoder().encode(value).length <= 72,
  },
];

export function validateRecoveryPassword(value: string): string | undefined {
  if (!value) return "Nhập mật khẩu mới.";
  if (!recoveryPasswordPolicy.every((rule) => rule.test(value)))
    return "Mật khẩu chưa đạt yêu cầu bên dưới.";
}
