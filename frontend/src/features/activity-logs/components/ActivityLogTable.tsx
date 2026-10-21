"use client";

import { Eye } from "lucide-react";
import { RowActionsMenu } from "@/components/admin/RowActionsMenu";
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
import { formatDateTime } from "@/lib/format/formatDate";
import {
  formatActivityAction,
  formatActivityRecord,
  formatActorRole,
} from "../labels/activityLabels";
import type { ActivityLogItem } from "../types/activityLog";

type ActivityLogTableProps = {
  items: ActivityLogItem[];
  loading: boolean;
  error: string | null;
  onRetry: () => void;
  onView: (id: string) => void;
};

const COLUMN_COUNT = 6;

export function ActivityLogTable({
  items,
  loading,
  error,
  onRetry,
  onView,
}: ActivityLogTableProps) {
  return (
    <TableFrame>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="text-right">Thời gian</TableHead>
            <TableHead>Người thực hiện</TableHead>
            <TableHead>Hành động</TableHead>
            <TableHead>Bản ghi</TableHead>
            <TableHead>Lý do chỉnh sửa</TableHead>
            <TableHead className="w-10">
              <span className="sr-only">Thao tác</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableStateRows
            colSpan={COLUMN_COUNT}
            loading={loading}
            error={error}
            onRetry={onRetry}
            empty={!loading && !error && items.length === 0}
            emptyText={
              <>
                <p>Không có hoạt động khớp bộ lọc.</p>
                <p className="mt-1">
                  Thử mở rộng khoảng thời gian hoặc bỏ bớt bộ lọc.
                </p>
              </>
            }
          />
          {!loading && !error
            ? items.map((log) => {
                const time = formatDateTime(log.occurredAt, { seconds: true });
                return (
                  <TableRow key={log.id}>
                    <TableCell className="text-right whitespace-nowrap tabular-nums">
                      {time}
                    </TableCell>
                    <TableCell>
                      <span className="font-medium">{log.actorUsername}</span>
                      <span className="text-muted-foreground">
                        {" · "}
                        {formatActorRole(log.actorRole)}
                      </span>
                    </TableCell>
                    <TableCell>
                      {formatActivityAction(log.action, log.actionRole)}
                    </TableCell>
                    <TableCell>
                      {formatActivityRecord(log.recordType, log.recordLabel)}
                    </TableCell>
                    <TableCell className="max-w-64 whitespace-normal">
                      {log.correctionReason ?? "—"}
                    </TableCell>
                    <TableCell>
                      <RowActionsMenu
                        label={time}
                        actions={
                          log.allowedActions.includes("view")
                            ? [
                                {
                                  key: "view",
                                  label: "Xem chi tiết",
                                  icon: Eye,
                                  onSelect: () => onView(log.id),
                                },
                              ]
                            : []
                        }
                      />
                    </TableCell>
                  </TableRow>
                );
              })
            : null}
        </TableBody>
      </Table>
    </TableFrame>
  );
}
