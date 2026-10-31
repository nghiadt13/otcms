"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { FieldError } from "@/components/admin/FieldError";
import { TablePagination } from "@/components/admin/TablePagination";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { APP_MESSAGES } from "@/lib/messages/appMessages";
import { useServiceCatalogList } from "../hooks/useServiceCatalogList";
import type {
  ServiceCatalogDetail,
  ServiceCatalogItem,
} from "../types/serviceCatalog";
import { ServiceCatalogFilters } from "./ServiceCatalogFilters";
import { ServiceCatalogTable } from "./ServiceCatalogTable";
import { ServiceDetailSheet } from "./ServiceDetailSheet";
import { ServiceFormSheet } from "./ServiceFormSheet";

// Sheet đang mở. `nonce` đổi mỗi lần mở để Sheet gắn lại và bỏ state cũ.
type OpenSheet =
  | { kind: "create"; nonce: number }
  | { kind: "detail" | "edit"; id: string; nonce: number }
  | null;

const toItem = (detail: ServiceCatalogDetail): ServiceCatalogItem => ({
  id: detail.id,
  name: detail.name,
  type: detail.type,
  modality: detail.modality,
  price: detail.price,
  status: detail.status,
  allowedActions: detail.allowedActions,
});

export function ServiceCatalogManagement() {
  const list = useServiceCatalogList();
  const [sheet, setSheet] = useState<OpenSheet>(null);
  const [opened, setOpened] = useState(0);

  const open = (
    next: { kind: "create" } | { kind: "detail" | "edit"; id: string },
  ) => {
    setOpened((n) => n + 1);
    setSheet({ ...next, nonce: opened + 1 } as OpenSheet);
  };
  const close = () => setSheet(null);
  const closeAndReload = () => {
    close();
    list.reload();
  };
  const replace = (detail: ServiceCatalogDetail) =>
    list.replaceItem((item) => item.id === detail.id, toItem(detail));

  // Lọc sai (MSG-54) hiện dưới FilterBar và giữ danh sách đang có; lỗi khác thay bảng.
  const filterError =
    list.error === APP_MESSAGES["MSG-54"] && list.data ? list.error : null;
  const tableError = filterError ? null : list.error;

  return (
    <>
      <PageHeader
        title="Dịch vụ và giá"
        description="Danh mục khám, chẩn đoán hình ảnh và xét nghiệm kèm giá."
        actions={
          <Button onClick={() => open({ kind: "create" })}>
            <Plus data-icon="inline-start" />
            Thêm dịch vụ
          </Button>
        }
      />
      <ServiceCatalogFilters
        searchText={list.searchText}
        onSearchTextChange={list.setSearchText}
        type={list.filters.type}
        onTypeChange={(type) => list.setFilters({ type })}
        status={list.filters.status}
        onStatusChange={(status) => list.setFilters({ status })}
      />
      <FieldError message={filterError} />
      <ServiceCatalogTable
        items={list.data?.items ?? []}
        loading={list.loading}
        error={tableError}
        hasFilters={list.hasFilters}
        onRetry={list.reload}
        onView={(id) => open({ kind: "detail", id })}
        onEdit={(id) => open({ kind: "edit", id })}
      />
      {list.data ? (
        <TablePagination
          page={list.data.page}
          totalPages={list.data.totalPages}
          totalItems={list.data.totalItems}
          onPageChange={list.setPage}
        />
      ) : null}

      {sheet?.kind === "create" || sheet?.kind === "edit" ? (
        <ServiceFormSheet
          key={sheet.nonce}
          serviceId={sheet.kind === "edit" ? sheet.id : undefined}
          onClose={close}
          onStale={closeAndReload}
          onSaved={(saved, mode) => {
            close();
            if (mode === "create") {
              list.resetFilters();
              list.reload();
            } else {
              replace(saved);
            }
          }}
        />
      ) : null}
      {sheet?.kind === "detail" ? (
        <ServiceDetailSheet
          key={sheet.nonce}
          serviceId={sheet.id}
          onClose={close}
          onStale={closeAndReload}
          onEdit={(id) => open({ kind: "edit", id })}
          onDiscontinued={replace}
        />
      ) : null}
    </>
  );
}
