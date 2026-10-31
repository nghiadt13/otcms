"use client";

import { StatusBadge } from "@/components/admin/StatusBadge";
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
import { ACCOUNT_TYPE_LABELS } from "@/lib/domain/clinicRoles";
import { formatDate } from "@/lib/format/formatDate";
import {
  STATUS_LABELS,
  STATUS_TONES,
  accountFullName,
  accountIdentifier,
} from "../accountDisplay";
import type { StatusTarget } from "../hooks/useAccountStatusChange";
import type { AccountListItem } from "../types/account";
import { AccountRowActions } from "./AccountRowActions";

type AccountsTableProps = {
  items: AccountListItem[];
  loading: boolean;
  error: string | null;
  hasFilter: boolean;
  page: number;
  totalPages: number;
  totalItems: number;
  onPageChange: (page: number) => void;
  onRetry: () => void;
  onView: (id: string) => void;
  onStatusRequest: (target: StatusTarget) => void;
};

const EMPTY_NO_DATA = "Chưa có tài khoản bệnh nhân nào.";
const EMPTY_FILTERED =
  "Không có tài khoản nào khớp bộ lọc. Bỏ bớt bộ lọc hoặc thử từ khóa khác.";

export function AccountsTable({
  items,
  loading,
  error,
  hasFilter,
  page,
  totalPages,
  totalItems,
  onPageChange,
  onRetry,
  onView,
  onStatusRequest,
}: AccountsTableProps) {
  return (
    <>
      <TableFrame>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Định danh</TableHead>
              <TableHead>Họ tên</TableHead>
              <TableHead>Loại tài khoản</TableHead>
              <TableHead>Trạng thái</TableHead>
              <TableHead>Ngày tạo</TableHead>
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
              emptyText={hasFilter ? EMPTY_FILTERED : EMPTY_NO_DATA}
            />
            {!loading && !error
              ? items.map((account) => (
                  <TableRow key={account.id}>
                    <TableCell className="font-medium break-all">
                      {accountIdentifier(account)}
                    </TableCell>
                    <TableCell
                      className={
                        account.fullName ? undefined : "text-muted-foreground"
                      }
                    >
                      {accountFullName(account)}
                    </TableCell>
                    <TableCell>
                      {ACCOUNT_TYPE_LABELS[account.accountType]}
                    </TableCell>
                    <TableCell>
                      <StatusBadge tone={STATUS_TONES[account.status]}>
                        {STATUS_LABELS[account.status]}
                      </StatusBadge>
                    </TableCell>
                    <TableCell className="tabular-nums">
                      {formatDate(account.createdAt)}
                    </TableCell>
                    <TableCell>
                      <AccountRowActions
                        account={account}
                        onView={onView}
                        onStatusRequest={onStatusRequest}
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
