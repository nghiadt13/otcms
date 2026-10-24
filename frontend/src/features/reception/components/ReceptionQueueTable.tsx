import type { ReactNode } from "react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { TableFrame } from "@/components/admin/TableFrame";
import { TableStateRows } from "@/components/admin/TableStateRows";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { QUEUE_STATUS_LABELS, QUEUE_STATUS_TONE } from "@/lib/domain/clinicVisit";
import {
  appointmentCell,
  QUEUE_TYPE_SHORT_LABELS,
} from "../schema/receptionQueuePresentation";
import type { ReceptionQueueResponse, ReceptionQueueRow } from "../types/receptionQueue";

const COLUMN_COUNT = 8;

interface ReceptionQueueTableProps {
  rows: ReceptionQueueRow[];
  loading: boolean;
  /** Chữ lỗi khi chưa có dữ liệu để hiện; null khi không lỗi. */
  error: string | null;
  /** Không truyền thì ẩn "Thử lại" (lỗi 403 tải lại cũng vô ích). */
  onRetry?: () => void;
  /** Chữ khi danh sách trống: dạy cách dùng hoặc báo không khớp bộ lọc. */
  emptyText: ReactNode;
  /** Mở tóm tắt lượt khám. Cả dòng bấm được; nút tên bệnh nhân cho bàn phím. */
  onOpen: (queueEntryId: string) => void;
}

/** Bảng hàng đợi: số và thứ tự căn phải, số dính trái khi cuộn ngang ở màn hẹp. */
export function ReceptionQueueTable({
  rows,
  loading,
  error,
  onRetry,
  emptyText,
  onOpen,
}: ReceptionQueueTableProps) {
  return (
    <TableFrame>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="sticky left-0 z-10 bg-card text-right">Số</TableHead>
            <TableHead className="text-right">Thứ tự</TableHead>
            <TableHead>Bệnh nhân</TableHead>
            <TableHead>Phòng</TableHead>
            <TableHead>Hàng đợi</TableHead>
            <TableHead>Lịch hẹn</TableHead>
            <TableHead>Ưu tiên</TableHead>
            <TableHead>Trạng thái</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableStateRows
            colSpan={COLUMN_COUNT}
            loading={loading}
            error={error}
            onRetry={onRetry}
            empty={!loading && !error && rows.length === 0}
            emptyText={emptyText}
            skeletonRows={6}
          />
          {rows.map((row) => (
            <TableRow
              key={row.queueEntryId}
              onClick={() => onOpen(row.queueEntryId)}
              className="cursor-pointer"
            >
              <TableCell className="sticky left-0 z-10 bg-card text-right font-medium tabular-nums">
                {row.queueNumber}
              </TableCell>
              <TableCell className="text-right tabular-nums">{row.position ?? "—"}</TableCell>
              <TableCell>
                <button
                  type="button"
                  className="rounded-sm text-left font-medium outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                >
                  {row.patientName}
                </button>
                <span className="block text-xs text-muted-foreground tabular-nums">
                  {row.patientCode}
                </span>
              </TableCell>
              <TableCell>{row.roomName}</TableCell>
              <TableCell>{QUEUE_TYPE_SHORT_LABELS[row.queueType]}</TableCell>
              <TableCell className="tabular-nums">{appointmentCell(row.appointmentStartAt)}</TableCell>
              <TableCell>{row.priorityLabel ?? "—"}</TableCell>
              <TableCell>
                <StatusBadge tone={QUEUE_STATUS_TONE[row.status]}>
                  {QUEUE_STATUS_LABELS[row.status]}
                </StatusBadge>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableFrame>
  );
}

/** Dòng tổng dưới bảng, đếm cả ngày: "9 đã tiếp nhận hôm nay · 5 đang chờ · 1 vắng mặt". */
export function ReceptionQueueTotals({ counts }: { counts: ReceptionQueueResponse["counts"] }) {
  return (
    <p className="text-sm text-muted-foreground">
      <span className="font-medium text-foreground tabular-nums">{counts.total}</span> đã tiếp nhận
      hôm nay ·{" "}
      <span className="tabular-nums">{counts.waiting}</span> đang chờ ·{" "}
      <span className="tabular-nums">{counts.absent}</span> vắng mặt
    </p>
  );
}
