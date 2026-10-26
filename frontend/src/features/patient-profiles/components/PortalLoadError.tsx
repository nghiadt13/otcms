import { publicButtonClass } from "@/components/brand/publicButtonStyles";

// Lỗi tải của một khối trong khu bệnh nhân: nói rõ chuyện gì, cách khắc phục là thử lại.
export function PortalLoadError({
  title,
  message,
  onRetry,
}: {
  title: string;
  message: string;
  onRetry: () => void;
}) {
  return (
    <div className="space-y-4">
      <p className="text-base text-destructive" role="alert">
        {title}
      </p>
      <p className="text-base text-muted-foreground">{message}</p>
      <button
        className={publicButtonClass("secondary")}
        onClick={onRetry}
        type="button"
      >
        Thử lại
      </button>
    </div>
  );
}
