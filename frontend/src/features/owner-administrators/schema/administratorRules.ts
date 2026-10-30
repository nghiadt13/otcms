export function validateAdministratorUsername(
  value: string,
): string | undefined {
  if (!value.trim()) return "Nhập tên đăng nhập.";
  if (value.length > 64) return "Tên đăng nhập tối đa 64 ký tự.";
  for (const char of value) {
    const point = char.codePointAt(0)!;
    if (
      point < 32 ||
      (point >= 127 && point <= 159) ||
      (point >= 0xd800 && point <= 0xdfff)
    )
      return "Tên đăng nhập chứa ký tự không hợp lệ.";
  }
  return undefined;
}
