import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

/** Chưa chọn ai (màn rộng): một câu hướng dẫn, không minh họa. */
export function CheckInPlaceholder() {
  return (
    <p className="text-sm text-muted-foreground">
      Chọn một lịch hẹn bên trái hoặc tìm bệnh nhân bằng số điện thoại và ngày sinh.
    </p>
  );
}

/** Đang tải: hình giống nội dung thật (tiêu đề, dòng phụ, ba khối). */
export function CheckInPanelSkeleton() {
  return (
    <div className="space-y-6" aria-busy="true">
      <div className="space-y-2">
        <Skeleton className="h-6 w-56 max-w-full" />
        <Skeleton className="h-4 w-80 max-w-full" />
      </div>
      {[0, 1, 2].map((index) => (
        <div key={index} className="space-y-3 border-t pt-5">
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-11 w-full" />
        </div>
      ))}
    </div>
  );
}

export function CheckInPanelError({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="space-y-3">
      <p role="alert" className="text-sm text-destructive">
        Không tải được thông tin tiếp nhận. Kiểm tra kết nối rồi bấm Thử lại.
      </p>
      <Button variant="outline" size="sm" onClick={onRetry}>
        Thử lại
      </Button>
    </div>
  );
}
