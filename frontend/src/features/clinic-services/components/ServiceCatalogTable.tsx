"use client";

import { StatusBadge } from "@/components/admin/StatusBadge";
import { TableFrame } from "@/components/admin/TableFrame";
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
import { CATEGORY_LABELS } from "@/lib/domain/clinicCategories";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import {
  MODALITY_LABELS,
  SERVICE_STATUS_LABELS,
  type ServiceCatalogItem,
} from "../types/serviceCatalog";

type ServiceCatalogTableProps = {
  items: ServiceCatalogItem[];
  loading: boolean;
  error: string | null;
  hasFilters: boolean;
  onRetry: () => void;
  onView: (id: string) => void;
  onEdit: (id: string) => void;
};

export function ServiceCatalogTable({
  items,
  loading,
  error,
  hasFilters,
  onRetry,
  onView,
  onEdit,
}: ServiceCatalogTableProps) {
  return (
    <TableFrame>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Tên dịch vụ</TableHead>
            <TableHead>Loại</TableHead>
            <TableHead>Phương thức</TableHead>
            <TableHead className="text-right">Giá</TableHead>
            <TableHead>Trạng thái</TableHead>
            <TableHead className="text-right">Thao tác</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableStateRows
            colSpan={6}
            loading={loading}
            error={error}
            empty={items.length === 0}
            onRetry={onRetry}
            emptyText={
              hasFilters
                ? "Không có dịch vụ khớp bộ lọc. Bỏ bộ lọc hoặc đổi từ khóa."
                : "Chưa có dịch vụ nào. Bấm Thêm dịch vụ để tạo dịch vụ đầu tiên."
            }
          />
          {!loading && !error
            ? items.map((item) => (
                <TableRow key={item.id}>
                  <TableCell className="max-w-80 whitespace-normal font-medium">
                    {item.name}
                  </TableCell>
                  <TableCell>{CATEGORY_LABELS[item.type]}</TableCell>
                  <TableCell>
                    {item.modality ? MODALITY_LABELS[item.modality] : "—"}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatCurrencyVnd(item.price)}
                  </TableCell>
                  <TableCell>
                    <StatusBadge
                      tone={item.status === "ACTIVE" ? "success" : "neutral"}
                    >
                      {SERVICE_STATUS_LABELS[item.status]}
                    </StatusBadge>
                  </TableCell>
                  <TableCell>
                    <div className="flex justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        aria-label={`Xem ${item.name}`}
                        onClick={() => onView(item.id)}
                      >
                        Xem
                      </Button>
                      {item.allowedActions.includes("edit") ? (
                        <Button
                          variant="outline"
                          size="sm"
                          aria-label={`Sửa ${item.name}`}
                          onClick={() => onEdit(item.id)}
                        >
                          Sửa
                        </Button>
                      ) : null}
                    </div>
                  </TableCell>
                </TableRow>
              ))
            : null}
        </TableBody>
      </Table>
    </TableFrame>
  );
}
