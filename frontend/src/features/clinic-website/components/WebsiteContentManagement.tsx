"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { toListItem } from "../api/websiteContentAdminApi";
import type { EditorResult } from "../hooks/useWebsiteContentEditor";
import { useWebsiteContentList } from "../hooks/useWebsiteContentList";
import { WebsiteContentEditSheet } from "./WebsiteContentEditSheet";
import { WebsiteContentFilters } from "./WebsiteContentFilters";
import { WebsiteContentTable } from "./WebsiteContentTable";

// null = Sheet đóng; editId null = thêm mới. `key` ép dựng lại Sheet cho mỗi lần mở.
type SheetState = { key: number; editId: string | null } | null;

/** Màn Quản lý nội dung website (UC72–UC73): PageHeader → bộ lọc → bảng; soạn trong Sheet. */
export function WebsiteContentManagement() {
  const list = useWebsiteContentList();
  const [sheet, setSheet] = useState<SheetState>(null);
  const { reload, replaceItem } = list;

  function openSheet(editId: string | null) {
    setSheet((current) => ({ key: (current?.key ?? 0) + 1, editId }));
  }

  function handleFinish(result: EditorResult) {
    setSheet(null);
    if (result.kind === "updated") {
      const updated = toListItem(result.detail);
      replaceItem((item) => item.id === updated.id, updated);
    } else if (result.kind === "created" || result.kind === "gone") {
      reload();
    }
  }

  return (
    <>
      <PageHeader
        title="Nội dung website"
        description="Nội dung hiển thị công khai trên website."
        actions={
          <Button onClick={() => openSheet(null)}>
            <Plus data-icon="inline-start" />
            Thêm nội dung
          </Button>
        }
      />
      <WebsiteContentFilters
        search={list.searchInput}
        onSearchChange={list.setSearchInput}
        type={list.filters.type}
        onTypeChange={list.setType}
      />
      <WebsiteContentTable
        items={list.data?.items ?? []}
        loading={list.loading}
        error={list.error}
        isFiltered={list.isFiltered}
        page={list.page}
        totalPages={list.data?.totalPages ?? 1}
        totalItems={list.data?.totalItems ?? 0}
        onPageChange={list.setPage}
        onRetry={list.reload}
        onEdit={(item) => openSheet(item.id)}
      />
      {sheet ? (
        <WebsiteContentEditSheet
          key={sheet.key}
          editId={sheet.editId}
          onFinish={handleFinish}
        />
      ) : null}
    </>
  );
}
