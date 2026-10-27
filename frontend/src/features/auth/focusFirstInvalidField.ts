// Sau khi gửi form thất bại, đưa focus về ô lỗi đầu tiên (WCAG: người dùng bàn
// phím/trình đọc màn hình không phải dò lại cả form). Chờ một khung hình để React
// kịp gắn `aria-invalid` lên ô.
export function focusFirstInvalidField(form: HTMLFormElement | null) {
  requestAnimationFrame(() => {
    form?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
  });
}
