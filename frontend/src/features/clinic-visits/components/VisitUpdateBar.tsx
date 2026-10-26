import { buttonVariants } from "@/components/ui/button";
import { formatTime } from "@/lib/format/formatDate";
import { cn } from "@/lib/utils";

// Dòng "Cập nhật lúc" và nút "Làm mới". Tải lại tự động lỗi chỉ hiện một dòng nhắc (không toast),
// dữ liệu cũ vẫn nằm bên dưới.
export function VisitUpdateBar({
  updatedAt,
  refreshFailedAt,
  refreshing,
  onRefresh,
}: {
  updatedAt: string;
  refreshFailedAt: Date | null;
  refreshing: boolean;
  onRefresh: () => void;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="space-y-1">
        <p className="text-sm text-muted-foreground tabular-nums">
          Cập nhật lúc {formatTime(updatedAt)}
        </p>
        {refreshFailedAt ? (
          <p className="text-sm text-destructive" role="status">
            Không cập nhật được lúc {formatTime(refreshFailedAt)}.
          </p>
        ) : null}
      </div>
      <button
        className={cn(
          buttonVariants({ variant: "ghost" }),
          "h-12 px-4 text-base",
        )}
        disabled={refreshing}
        onClick={onRefresh}
        type="button"
      >
        {refreshing ? "Đang tải..." : "Làm mới"}
      </button>
    </div>
  );
}
