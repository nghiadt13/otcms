"use client";

import { useCallback, useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { useAccountList } from "../hooks/useAccountList";
import { useAccountStatusChange } from "../hooks/useAccountStatusChange";
import type { AccountDetail } from "../types/account";
import { AccountDetailSheet } from "./AccountDetailSheet";
import { AccountFormDialog } from "./AccountFormDialog";
import { AccountStatusDialog } from "./AccountStatusDialog";
import { AccountsFilterBar } from "./AccountsFilterBar";
import { AccountsTable } from "./AccountsTable";

/** Màn Tài khoản người dùng: PageHeader -> bộ lọc -> bảng; chi tiết ở Sheet, tạo ở Dialog. */
export function AccountManagement() {
  const list = useAccountList();
  const { reload, replaceItem, setFilters } = list;
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  // Cập nhật dòng tại chỗ khi đổi trạng thái (từ menu dòng hoặc từ Sheet).
  const handleChanged = useCallback(
    (updated: AccountDetail) =>
      replaceItem((item) => item.id === updated.id, updated),
    [replaceItem],
  );
  const closeSheet = useCallback(() => setSelectedId(null), []);

  const status = useAccountStatusChange({
    onChanged: handleChanged,
    onStale: reload,
  });

  return (
    <>
      <PageHeader
        title="Tài khoản người dùng"
        description="Cấp tài khoản cho nhân viên và tra cứu tài khoản bệnh nhân."
        actions={
          <Button onClick={() => setCreateOpen(true)}>Tạo tài khoản</Button>
        }
      />

      <AccountsFilterBar
        searchText={list.searchText}
        onSearchChange={list.setSearchText}
        role={list.filters.role}
        onRoleChange={(role) => setFilters({ role })}
        status={list.filters.status}
        onStatusChange={(next) => setFilters({ status: next })}
      />

      <AccountsTable
        items={list.data?.items ?? []}
        loading={list.loading}
        error={list.error}
        hasFilter={list.hasFilter}
        page={list.page}
        totalPages={list.data?.totalPages ?? 1}
        totalItems={list.data?.totalItems ?? 0}
        onPageChange={list.setPage}
        onRetry={reload}
        onView={setSelectedId}
        onStatusRequest={status.request}
      />

      <AccountStatusDialog
        target={status.target}
        onClose={status.close}
        onConfirm={status.confirm}
      />

      {selectedId ? (
        <AccountDetailSheet
          accountId={selectedId}
          onClose={closeSheet}
          onChanged={handleChanged}
          onStale={reload}
        />
      ) : null}

      {createOpen ? (
        <AccountFormDialog
          onCreated={reload}
          onClose={() => setCreateOpen(false)}
        />
      ) : null}
    </>
  );
}
