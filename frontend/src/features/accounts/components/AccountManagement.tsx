"use client";

import { useCallback, useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { useAccountList } from "../hooks/useAccountList";
import { useAccountStatusChange } from "../hooks/useAccountStatusChange";
import type { AccountDetail } from "../types/account";
import { AccountDetailSheet } from "./AccountDetailSheet";
import { AccountStatusDialog } from "./AccountStatusDialog";
import { AccountsFilterBar } from "./AccountsFilterBar";
import { AccountsTable } from "./AccountsTable";

/** Admin tra cứu tài khoản bệnh nhân và quản lý trạng thái đăng nhập của họ. */
export function AccountManagement() {
  const list = useAccountList();
  const { reload, replaceItem, setFilters } = list;
  const [selectedId, setSelectedId] = useState<string | null>(null);

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
        description="Tra cứu tài khoản bệnh nhân và bật hoặc vô hiệu hóa quyền đăng nhập."
      />

      <AccountsFilterBar
        searchText={list.searchText}
        onSearchChange={list.setSearchText}
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

    </>
  );
}
