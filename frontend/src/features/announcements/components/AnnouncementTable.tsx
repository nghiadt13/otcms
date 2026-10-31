"use client";

import { StatusBadge } from "@/components/admin/StatusBadge";
import { TableFrame } from "@/components/admin/TableFrame";
import { TablePagination } from "@/components/admin/TablePagination";
import { TableStateRows } from "@/components/admin/TableStateRows";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDateTime } from "@/lib/format/formatDate";
import type { PageResponse } from "@/lib/api/pagination";
import type { AnnouncementListItem } from "../types/announcement";
import {
  audienceLabel,
  channelsText,
  DELIVERY_STATUS_VIEW,
} from "./announcementLabels";

type AnnouncementTableProps = {
  data: PageResponse<AnnouncementListItem> | null;
  loading: boolean;
  error: string | null;
  hasFilters: boolean;
  page: number;
  onPageChange: (page: number) => void;
  onRetry: () => void;
  onView: (id: string) => void;
};

function DeliveryCell({ item }: { item: AnnouncementListItem }) {
  if (!item.deliveryStatus)
    return <span className="text-muted-foreground">—</span>;
  const view = DELIVERY_STATUS_VIEW[item.deliveryStatus];
  return <StatusBadge tone={view.tone}>{view.label}</StatusBadge>;
}

export function AnnouncementTable({
  data,
  loading,
  error,
  hasFilters,
  page,
  onPageChange,
  onRetry,
  onView,
}: AnnouncementTableProps) {
  const items = data?.items ?? [];
  // Cột trạng thái giao chỉ hiện khi BE có trả field này cho ít nhất một dòng.
  const showDelivery = items.some((item) => "deliveryStatus" in item);
  const colSpan = showDelivery ? 6 : 5;

  return (
    <>
      <TableFrame>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Tiêu đề</TableHead>
              <TableHead>Nhóm người nhận</TableHead>
              <TableHead>Kênh</TableHead>
              <TableHead>Ngày đăng</TableHead>
              {showDelivery ? <TableHead>Trạng thái giao</TableHead> : null}
              <TableHead className="text-right">Thao tác</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableStateRows
              colSpan={colSpan}
              loading={loading}
              error={error}
              empty={!loading && !error && items.length === 0}
              onRetry={onRetry}
              emptyText={
                hasFilters
                  ? "Không có thông báo khớp bộ lọc. Đổi từ khóa hoặc khoảng ngày đăng."
                  : "Chưa có thông báo nào. Bấm Đăng thông báo để gửi thông báo đầu tiên."
              }
            />
            {!error && !loading
              ? items.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="max-w-md min-w-48 whitespace-normal">
                      <span
                        className="line-clamp-2 break-words"
                        title={item.title}
                      >
                        {item.title}
                      </span>
                    </TableCell>
                    <TableCell>{audienceLabel(item.audience)}</TableCell>
                    <TableCell>{channelsText(item.channels)}</TableCell>
                    <TableCell className="tabular-nums">
                      {formatDateTime(item.publishedAt)}
                    </TableCell>
                    {showDelivery ? (
                      <TableCell>
                        <DeliveryCell item={item} />
                      </TableCell>
                    ) : null}
                    <TableCell className="text-right">
                      {item.allowedActions.includes("view") ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={`Xem thông báo ${item.title}`}
                          onClick={() => onView(item.id)}
                        >
                          Xem
                        </Button>
                      ) : null}
                    </TableCell>
                  </TableRow>
                ))
              : null}
          </TableBody>
        </Table>
      </TableFrame>
      {data ? (
        <TablePagination
          page={page}
          totalPages={data.totalPages}
          totalItems={data.totalItems}
          onPageChange={onPageChange}
        />
      ) : null}
    </>
  );
}
