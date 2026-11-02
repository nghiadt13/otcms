/** Lỗi đỏ dưới ô nhập. Không render khi không có nội dung. */
export function FieldError({
  message,
  id,
}: {
  message?: string | null;
  id?: string;
}) {
  if (!message) return null;
  return (
    <p id={id} role="alert" className="mt-1.5 text-xs text-destructive">
      {message}
    </p>
  );
}
