// Lỗi chung của bước đổi email (từ API): khối đỏ có role="alert", nói rõ chuyện gì đã xảy ra.
export function EmailChangeErrorNotice({ text }: { text: string }) {
  return (
    <div
      role="alert"
      className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-base text-foreground"
    >
      {text}
    </div>
  );
}
