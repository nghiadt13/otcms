import { publicButtonClass } from "@/components/brand/publicButtonStyles";

/**
 * Lỗi tải của một khối: câu nói rõ chuyện gì và nút "Thử lại". `role="alert"` để trình đọc
 * màn hình báo ngay.
 */
export function PaymentErrorBlock({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="space-y-4 rounded-lg border bg-card px-4 py-4">
      <p className="text-base text-destructive" role="alert">
        {message}
      </p>
      <button className={publicButtonClass("secondary")} onClick={onRetry} type="button">
        Thử lại
      </button>
    </div>
  );
}
