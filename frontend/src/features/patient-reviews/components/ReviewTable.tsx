import { TableFrame } from "@/components/admin/TableFrame";
import { TableStateRows } from "@/components/admin/TableStateRows";
import { StatusBadge } from "@/components/admin/StatusBadge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDate } from "@/lib/format/formatDate";
import type { ReviewListItem } from "../types/reviewModeration";
import { STATUS_LABEL, STATUS_TONE } from "../utils/reviewModerationLabels";
import { ReviewRatingStars } from "./ReviewRatingStars";

const COLUMNS = [
  "Ngày gửi",
  "Bệnh nhân",
  "Khám · Bác sĩ",
  "Điểm",
  "Nội dung",
  "Trạng thái",
  "Phản hồi",
];

type ReviewTableProps = {
  items: ReviewListItem[];
  loading: boolean;
  error: string | null;
  empty: boolean;
  emptyText: string;
  onRetry: () => void;
  onOpen: (review: ReviewListItem) => void;
};

/** Bảng kiểm duyệt. Bấm dòng mở Sheet; nút ở ô Bệnh nhân giúp dùng được bằng bàn phím. */
export function ReviewTable({
  items,
  loading,
  error,
  empty,
  emptyText,
  onRetry,
  onOpen,
}: ReviewTableProps) {
  const showRows = !loading && !error;
  return (
    <TableFrame>
      <Table>
        <TableHeader>
          <TableRow>
            {COLUMNS.map((column) => (
              <TableHead key={column}>{column}</TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableStateRows
            colSpan={COLUMNS.length}
            loading={loading}
            error={error}
            empty={empty}
            onRetry={onRetry}
            emptyText={emptyText}
          />
          {showRows
            ? items.map((review) => (
                <TableRow
                  key={review.id}
                  className="cursor-pointer"
                  onClick={() => onOpen(review)}
                >
                  <TableCell className="tabular-nums">
                    {formatDate(review.submittedAt)}
                  </TableCell>
                  <TableCell>
                    <button
                      type="button"
                      data-review-trigger={review.id}
                      onClick={(event) => {
                        event.stopPropagation();
                        onOpen(review);
                      }}
                      className="rounded-sm text-left font-medium underline-offset-4 outline-none hover:underline focus-visible:ring-[3px] focus-visible:ring-ring/50"
                    >
                      {review.patientName}
                    </button>
                  </TableCell>
                  <TableCell className="min-w-44">
                    <span className="tabular-nums">{formatDate(review.visit.visitDate)}</span>
                    <span className="text-muted-foreground"> · {review.visit.doctorName}</span>
                  </TableCell>
                  <TableCell>
                    <ReviewRatingStars rating={review.rating} />
                  </TableCell>
                  <TableCell className="max-w-64 min-w-48 whitespace-normal">
                    {review.content ? (
                      <span className="line-clamp-2">{review.content}</span>
                    ) : (
                      <span className="text-muted-foreground">Không có nội dung.</span>
                    )}
                  </TableCell>
                  <TableCell>
                    <StatusBadge tone={STATUS_TONE[review.status]}>
                      {STATUS_LABEL[review.status]}
                    </StatusBadge>
                  </TableCell>
                  <TableCell className="whitespace-nowrap">
                    {review.replied ? (
                      "Đã phản hồi"
                    ) : (
                      <span className="text-muted-foreground">Chưa phản hồi</span>
                    )}
                  </TableCell>
                </TableRow>
              ))
            : null}
        </TableBody>
      </Table>
    </TableFrame>
  );
}
