import { Eye, Pencil } from "lucide-react";
import { RowActionsMenu, type RowAction } from "@/components/admin/RowActionsMenu";
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
import { ROLE_LABELS } from "@/lib/domain/clinicRoles";
import type { StaffProfileListItem } from "../types/staffProfile";
import { StaffAvatar } from "./StaffAvatar";

type StaffProfileTableProps = {
  items: StaffProfileListItem[];
  loading: boolean;
  error: string | null;
  onRetry: () => void;
  /** Có bộ lọc đang bật: đổi câu trống từ "chưa có" sang "không khớp". */
  filtered: boolean;
  onView: (id: string) => void;
  onEdit: (id: string) => void;
};

const COLUMN_COUNT = 7;

export function StaffProfileTable({
  items,
  loading,
  error,
  onRetry,
  filtered,
  onView,
  onEdit,
}: StaffProfileTableProps) {
  const emptyText = filtered
    ? "Không có hồ sơ khớp bộ lọc. Bỏ bớt bộ lọc để xem thêm."
    : "Chưa có hồ sơ nhân viên nào. Bấm Tạo hồ sơ và chọn một tài khoản nhân viên đã có.";

  const actionsFor = (item: StaffProfileListItem): RowAction[] => [
    { key: "view", label: "Xem", icon: Eye, onSelect: () => onView(item.id) },
    ...(item.allowedActions.includes("edit")
      ? [
          {
            key: "edit",
            label: "Sửa",
            icon: Pencil,
            onSelect: () => onEdit(item.id),
          },
        ]
      : []),
  ];

  return (
    <TableFrame>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Họ tên hiển thị</TableHead>
            <TableHead>Vai trò</TableHead>
            <TableHead>Tên đăng nhập</TableHead>
            <TableHead>Liên hệ công việc</TableHead>
            <TableHead>Công khai</TableHead>
            <TableHead>Trạng thái</TableHead>
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
            empty={!loading && !error && items.length === 0}
            onRetry={onRetry}
            emptyText={emptyText}
          />
          {!loading && !error
            ? items.map((item) => (
                <TableRow key={item.id}>
                  <TableCell>
                    <div className="flex items-center gap-3">
                      <StaffAvatar
                        name={item.displayName}
                        photoUrl={item.photoUrl}
                      />
                      <button
                        type="button"
                        className="rounded-sm text-left font-medium hover:underline focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
                        onClick={() => onView(item.id)}
                      >
                        {item.displayName}
                      </button>
                    </div>
                  </TableCell>
                  <TableCell>{ROLE_LABELS[item.role]}</TableCell>
                  <TableCell className="text-muted-foreground">
                    {item.username}
                  </TableCell>
                  <TableCell>{item.workContact}</TableCell>
                  <TableCell>
                    {item.isPublic === null ? (
                      <span aria-label="Không áp dụng">—</span>
                    ) : (
                      <StatusBadge tone={item.isPublic ? "success" : "neutral"}>
                        {item.isPublic ? "Có" : "Không"}
                      </StatusBadge>
                    )}
                  </TableCell>
                  <TableCell>
                    <StatusBadge
                      tone={item.status === "ACTIVE" ? "success" : "neutral"}
                    >
                      {item.status === "ACTIVE" ? "Đang hoạt động" : "Đã gỡ"}
                    </StatusBadge>
                  </TableCell>
                  <TableCell>
                    <RowActionsMenu
                      label={item.displayName}
                      actions={actionsFor(item)}
                    />
                  </TableCell>
                </TableRow>
              ))
            : null}
        </TableBody>
      </Table>
    </TableFrame>
  );
}
