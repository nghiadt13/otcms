"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import type { WebsiteContentListItem } from "../types/websiteContentAdmin";
import {
  deleteWebsiteArticle,
  toListItem,
} from "../api/websiteContentAdminApi";
import type { EditorResult } from "../hooks/useWebsiteContentEditor";
import { useWebsiteContentList } from "../hooks/useWebsiteContentList";
import { WebsiteContentEditSheet } from "./WebsiteContentEditSheet";
import { WebsiteContentFilters } from "./WebsiteContentFilters";
import { WebsiteContentTable } from "./WebsiteContentTable";

// null = Sheet đóng; editId null = thêm mới. `key` ép dựng lại Sheet cho mỗi lần mở.
type SheetState = {
  key: number;
  editId: string | null;
  article: boolean;
} | null;

/** Màn Quản lý nội dung website (UC72–UC73): PageHeader → bộ lọc → bảng; soạn trong Sheet. */
export function WebsiteContentManagement() {
  const list = useWebsiteContentList();
  const [sheet, setSheet] = useState<SheetState>(null);
  const [articleToDelete, setArticleToDelete] =
    useState<WebsiteContentListItem | null>(null);
  const { reload, replaceItem } = list;

  async function confirmDelete() {
    if (!articleToDelete) return;
    try {
      await deleteWebsiteArticle(articleToDelete.id);
      notifySuccess("Đã ngừng hiển thị bài viết.");
      reload();
    } catch (err) {
      notifyError(err);
      throw err;
    }
  }

  function openSheet(editId: string | null, article = false) {
    setSheet((current) => ({ key: (current?.key ?? 0) + 1, editId, article }));
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
          <Button onClick={() => openSheet(null, true)}>
            <Plus data-icon="inline-start" />
            Thêm bài viết
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
        onDelete={setArticleToDelete}
      />
      <ConfirmActionDialog
        open={articleToDelete !== null}
        onOpenChange={(open) => !open && setArticleToDelete(null)}
        title="Ngừng hiển thị bài viết?"
        description={`Bài “${articleToDelete?.title ?? ""}” sẽ được ẩn trên website và vẫn lưu trong Admin.`}
        confirmLabel="Ngừng hiển thị"
        pendingLabel="Đang cập nhật…"
        destructive
        onConfirm={confirmDelete}
      />
      {sheet ? (
        <WebsiteContentEditSheet
          key={sheet.key}
          editId={sheet.editId}
          initialType={sheet.article ? "HEALTH_ARTICLE" : ""}
          onFinish={handleFinish}
        />
      ) : null}
    </>
  );
}
