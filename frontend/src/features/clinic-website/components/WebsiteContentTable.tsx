import { ExternalLink, Pencil } from "lucide-react";
import {
  RowActionsMenu,
  type RowAction,
} from "@/components/admin/RowActionsMenu";
import { TableFrame } from "@/components/admin/TableFrame";
import { TablePagination } from "@/components/admin/TablePagination";
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
import { websiteContentPublicPath } from "../schema/websiteContentPublicPath";
import {
  WEBSITE_CONTENT_TYPE_LABELS,
  type WebsiteContentListItem,
} from "../types/websiteContentAdmin";

type Props = {
  items: WebsiteContentListItem[];
  loading: boolean;
  error: string | null;
  isFiltered: boolean;
  page: number;
  totalPages: number;
  totalItems: number;
  onPageChange: (page: number) => void;
  onRetry: () => void;
  onEdit: (item: WebsiteContentListItem) => void;
};

const COLUMN_COUNT = 6;

/** Chỉ vẽ hành động có trong `allowedActions` của bản ghi. */
function rowActions(
  item: WebsiteContentListItem,
  onEdit: Props["onEdit"],
): RowAction[] {
  const actions: RowAction[] = [];
  if (item.allowedActions.includes("edit"))
    actions.push({
      key: "edit",
      label: "Sửa",
      icon: Pencil,
      onSelect: () => onEdit(item),
    });
  if (item.allowedActions.includes("viewOnWebsite"))
    actions.push({
      key: "view",
      label: "Xem trên website",
      icon: ExternalLink,
      onSelect: () =>
        window.open(
          websiteContentPublicPath(item),
          "_blank",
          "noopener,noreferrer",
        ),
    });
  return actions;
}

export function WebsiteContentTable({
  items,
  loading,
  error,
  isFiltered,
  page,
  totalPages,
  totalItems,
  onPageChange,
  onRetry,
  onEdit,
}: Props) {
  return (
    <>
      <TableFrame>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Tiêu đề</TableHead>
              <TableHead>Loại</TableHead>
              <TableHead>Ngày đăng</TableHead>
              <TableHead>Cập nhật lần cuối</TableHead>
              <TableHead>Người đăng</TableHead>
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
              emptyText={
                isFiltered
                  ? "Không có nội dung khớp bộ lọc. Đổi từ khóa hoặc chọn loại khác."
                  : "Chưa có nội dung nào. Bấm Thêm nội dung để đăng nội dung đầu tiên."
              }
            />
            {!loading && !error
              ? items.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="max-w-72 whitespace-normal font-medium">
                      {item.title}
                    </TableCell>
                    <TableCell>
                      {WEBSITE_CONTENT_TYPE_LABELS[item.type]}
                    </TableCell>
                    <TableCell className="tabular-nums">
                      {formatDateTime(item.publishedAt)}
                    </TableCell>
                    <TableCell className="tabular-nums">
                      {formatDateTime(item.updatedAt)}
                    </TableCell>
                    <TableCell>{item.publishedBy}</TableCell>
                    <TableCell>
                      <RowActionsMenu
                        label={item.title}
                        actions={rowActions(item, onEdit)}
                      />
                    </TableCell>
                  </TableRow>
                ))
              : null}
          </TableBody>
        </Table>
      </TableFrame>
      {!loading && !error ? (
        <TablePagination
          page={page}
          totalPages={totalPages}
          totalItems={totalItems}
          onPageChange={onPageChange}
        />
      ) : null}
    </>
  );
}
