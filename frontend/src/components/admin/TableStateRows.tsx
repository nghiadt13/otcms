import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { TableCell, TableRow } from "@/components/ui/table";

type TableStateRowsProps = {
  colSpan: number;
  loading: boolean;
  error: string | null;
  /** true khi đã tải xong và không có dòng nào. */
  empty: boolean;
  /** Không truyền thì ẩn "Thử lại" (ví dụ lỗi 403 tải lại cũng vô ích). */
  onRetry?: () => void;
  /** Trạng thái trống phải dạy cách dùng, không ghi "Không có dữ liệu". */
  emptyText: ReactNode;
  skeletonRows?: number;
};

/**
 * Bốn trạng thái của bảng trong `<TableBody>`: đang tải, lỗi, trống, hoặc không vẽ gì
 * (trả `null` để bảng vẽ dữ liệu thật). Dùng `Skeleton`, không dùng spinner.
 */
export function TableStateRows({
  colSpan,
  loading,
  error,
  empty,
  onRetry,
  emptyText,
  skeletonRows = 5,
}: TableStateRowsProps) {
  if (loading) {
    return Array.from({ length: skeletonRows }, (_, row) => (
      <TableRow key={row} aria-hidden>
        <TableCell colSpan={colSpan}>
          <Skeleton className="h-5 w-full" />
        </TableCell>
      </TableRow>
    ));
  }
  if (error) {
    return (
      <TableRow>
        <TableCell colSpan={colSpan} className="py-10 text-center">
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
          {onRetry ? (
            <Button
              variant="outline"
              size="sm"
              className="mt-3"
              onClick={onRetry}
            >
              Thử lại
            </Button>
          ) : null}
        </TableCell>
      </TableRow>
    );
  }
  if (empty) {
    return (
      <TableRow>
        <TableCell
          colSpan={colSpan}
          className="py-10 text-center text-sm text-muted-foreground"
        >
          {emptyText}
        </TableCell>
      </TableRow>
    );
  }
  return null;
}
