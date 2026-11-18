// Chính sách mật khẩu (BR-04): ≥ 8 ký tự, có chữ và số. Dùng chung cho auth,
// my-account (15) và mật khẩu khởi tạo ở accounts (02). Đổi ở đây là đổi cho tất cả.
export const passwordPolicy = [
  {
    id: "length",
    label: "Tối thiểu 8 ký tự",
    test: (v: string) => Array.from(v).length >= 8,
  },
  {
    id: "maximum",
    label: "Tối đa 64 ký tự và 72 byte UTF-8",
    test: (v: string) =>
      Array.from(v).length <= 64 && new TextEncoder().encode(v).length <= 72,
  },
  {
    id: "letter",
    label: "Có ít nhất một chữ cái",
    test: (v: string) => /\p{L}/u.test(v),
  },
  {
    id: "digit",
    label: "Có ít nhất một chữ số",
    test: (v: string) => /\p{Nd}/u.test(v),
  },
] as const;

export const isPasswordValid = (value: string) =>
  passwordPolicy.every((rule) => rule.test(value));
