"use client";

import { useState } from "react";
import { TablePagination } from "@/components/admin/TablePagination";
import { PageHeader } from "@/components/PageHeader";
import { useActivityLogList } from "../hooks/useActivityLogList";
import { ActivityLogDetailSheet } from "./ActivityLogDetailSheet";
import { ActivityLogFilterBar } from "./ActivityLogFilterBar";
import { ActivityLogTable } from "./ActivityLogTable";

export function ActivityLogManagement() {
  const list = useActivityLogList();
  const [selectedId, setSelectedId] = useState<string | null>(null);

  return (
    <>
      <PageHeader
        title="Nhật ký hoạt động"
        description="Theo dõi ai đã làm gì, lúc nào, trên bản ghi nào. Chỉ đọc."
      />
      <ActivityLogFilterBar
        draft={list.draft}
        onChange={list.updateDraft}
        rangeError={list.rangeError}
      />
      <ActivityLogTable
        items={list.data?.items ?? []}
        loading={list.loading}
        error={list.error}
        onRetry={list.reload}
        onView={setSelectedId}
      />
      <TablePagination
        page={list.page}
        totalPages={list.data?.totalPages ?? 0}
        totalItems={list.data?.totalItems ?? 0}
        onPageChange={list.setPage}
      />
      <ActivityLogDetailSheet
        selectedId={selectedId}
        onClose={() => setSelectedId(null)}
      />
    </>
  );
}
