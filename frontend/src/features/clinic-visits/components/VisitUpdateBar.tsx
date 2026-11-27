import { RefreshCw } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { formatTime } from "@/lib/format/formatDate";
import { cn } from "@/lib/utils";

// Dòng "Cập nhật lúc" kèm lời nhắc tự làm mới (30 giây) và nút "Làm mới". Tải lại tự động lỗi chỉ hiện
// một dòng nhắc (không toast), dữ liệu cũ vẫn nằm bên dưới.
export function VisitUpdateBar({
  updatedAt,
  refreshFailedAt,
  refreshing,
  onRefresh,
}: {
  /** Thời điểm tải thành công gần nhất (ISO); null khi chưa tải được lần nào. */
  updatedAt: string | null;
  refreshFailedAt: Date | null;
  refreshing: boolean;
  onRefresh: () => void;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="space-y-1">
        {updatedAt ? (
          <p className="text-sm text-muted-foreground tabular-nums">
            Cập nhật lúc {formatTime(updatedAt)} · tự làm mới mỗi 30 giây
          </p>
        ) : null}
        {refreshFailedAt ? (
          <p className="text-sm text-destructive" role="status">
            Không cập nhật được lúc {formatTime(refreshFailedAt)}.
          </p>
        ) : null}
      </div>
      <button
        className={cn(
          buttonVariants({ variant: "outline" }),
          "h-12 gap-2 px-4 text-base",
        )}
        disabled={refreshing}
        onClick={onRefresh}
        type="button"
      >
        <RefreshCw
          aria-hidden
          className={cn(
            "size-4",
            refreshing && "animate-spin motion-reduce:animate-none",
          )}
        />
        {refreshing ? "Đang tải..." : "Làm mới"}
      </button>
    </div>
  );
}
