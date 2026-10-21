import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatInteger } from "@/lib/format/formatNumber";

type TablePaginationProps = {
  /** 0-based */
  page: number;
  totalPages: number;
  totalItems: number;
  onPageChange: (page: number) => void;
};

/** Thanh dưới bảng: tổng số bản ghi + trước/sau. Ẩn khi chưa có bản ghi nào. */
export function TablePagination({
  page,
  totalPages,
  totalItems,
  onPageChange,
}: TablePaginationProps) {
  if (totalItems === 0) return null;
  return (
    <nav
      aria-label="Phân trang"
      className="mt-3 flex flex-wrap items-center justify-between gap-3 text-sm"
    >
      <p className="text-muted-foreground tabular-nums">
        {formatInteger(totalItems)} bản ghi · Trang {page + 1}/{totalPages}
      </p>
      <div className="flex gap-2">
        <Button
          variant="outline"
          size="sm"
          disabled={page <= 0}
          onClick={() => onPageChange(page - 1)}
        >
          <ChevronLeft data-icon="inline-start" />
          Trước
        </Button>
        <Button
          variant="outline"
          size="sm"
          disabled={page >= totalPages - 1}
          onClick={() => onPageChange(page + 1)}
        >
          Sau
          <ChevronRight data-icon="inline-end" />
        </Button>
      </div>
    </nav>
  );
}
