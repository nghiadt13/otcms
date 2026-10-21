// Chính sách mật khẩu (BR-04): ≥ 8 ký tự, có chữ và số. Dùng chung cho auth,
// my-account (15) và mật khẩu khởi tạo ở accounts (02). Đổi ở đây là đổi cho tất cả.
export const passwordPolicy = [
  {
    id: "length",
    label: "Tối thiểu 8 ký tự",
    test: (v: string) => v.length >= 8,
  },
  {
    id: "letter",
    label: "Có ít nhất một chữ cái",
    test: (v: string) => /\p{L}/u.test(v),
  },
  {
    id: "digit",
    label: "Có ít nhất một chữ số",
    test: (v: string) => /\d/.test(v),
  },
] as const;

export const isPasswordValid = (value: string) =>
  passwordPolicy.every((rule) => rule.test(value));
